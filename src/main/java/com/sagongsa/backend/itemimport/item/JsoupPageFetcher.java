package com.sagongsa.backend.itemimport.item;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class JsoupPageFetcher implements PageFetcher {

	private static final String ANDROID_USER_AGENT =
		"Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36";
	private static final int DEFAULT_TIMEOUT_MILLIS = 30_000;
	private static final int DEFAULT_MAX_ATTEMPTS = 2;
	private static final int DEFAULT_MAX_RESPONSE_BYTES = 1_000_000;
	private static final int MAX_REDIRECTS = 5;
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final Pattern BUNJANG_PRODUCT_PATH_PATTERN = Pattern.compile("^/products/(\\d+)(?:/.*)?$");
	private static final Pattern JS_HTTP_ASSIGNMENT_PATTERN = Pattern.compile(
		"(?is)\\b(?:var|let|const)\\s+(store_link|fallback_url|fallbackUrl|target_url|targetUrl|web_url|webUrl|product_url|productUrl|landing_url|landingUrl|redirect_url|redirectUrl|af_android_url|af_ios_url|af_web_dp)\\s*=\\s*(['\"])(https?://.*?)\\2"
	);
	private static final Pattern JS_APP_LINK_ASSIGNMENT_PATTERN = Pattern.compile(
		"(?is)\\b(?:var|let|const)\\s+(?:app_link|appLink|deep_link|deepLink|deeplink|fallback_deeplink|fallbackDeeplink)\\s*=\\s*(['\"])(.*?)\\1"
	);
	private static final Pattern JS_LOCATION_ASSIGNMENT_PATTERN = Pattern.compile(
		"(?is)\\b(?:window\\.)?location(?:\\.href)?\\s*=\\s*(['\"])(https?://.*?)\\1"
	);
	private static final Pattern JS_LOCATION_CALL_PATTERN = Pattern.compile(
		"(?is)\\b(?:window\\.)?location\\.(?:replace|assign)\\(\\s*(['\"])(https?://.*?)\\1\\s*\\)"
	);
	private static final Pattern JSON_HTTP_URL_PATTERN = Pattern.compile(
		"(?is)\"(targetUrl|target_url|webUrl|web_url|productUrl|product_url|landingUrl|landing_url|fallbackUrl|fallback_url|redirectUrl|redirect_url|url)\"\\s*:\\s*\"(https?://(?:\\\\.|[^\"\\\\])*)\""
	);
	private static final Pattern JSON_APP_URL_PATTERN = Pattern.compile(
		"(?is)\"(dst|deepLink|deep_link|deeplink|appLink|app_link|af_dp)\"\\s*:\\s*\"((?:[a-z][a-z0-9+.-]*:)(?:\\\\.|[^\"\\\\])*)\""
	);

	private final int timeoutMillis;
	private final int maxAttempts;
	private final int maxResponseBytes;

	public JsoupPageFetcher() {
		this(DEFAULT_MAX_RESPONSE_BYTES);
	}

	JsoupPageFetcher(int maxResponseBytes) {
		this(DEFAULT_TIMEOUT_MILLIS, DEFAULT_MAX_ATTEMPTS, maxResponseBytes);
	}

	JsoupPageFetcher(int timeoutMillis, int maxAttempts) {
		this(timeoutMillis, maxAttempts, DEFAULT_MAX_RESPONSE_BYTES);
	}

	JsoupPageFetcher(int timeoutMillis, int maxAttempts, int maxResponseBytes) {
		this.timeoutMillis = timeoutMillis <= 0 ? DEFAULT_TIMEOUT_MILLIS : timeoutMillis;
		this.maxAttempts = maxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
		this.maxResponseBytes = maxResponseBytes <= 0 ? DEFAULT_MAX_RESPONSE_BYTES : maxResponseBytes;
	}

	@Override
	public FetchedPage fetch(URI uri) {
		ResponseStatusException lastStatusException = null;
		IOException lastIoException = null;

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				FetchedPage page = fetchOnce(uri);
				if (isRetryableStatus(page.statusCode()) && attempt < maxAttempts) {
					lastStatusException = new ResponseStatusException(
						HttpStatus.BAD_GATEWAY,
						"Shopping page returned " + page.statusCode()
					);
					continue;
				}
				return page;
			} catch (IOException exception) {
				lastIoException = exception;
			}
		}

		if (lastStatusException != null) {
			throw lastStatusException;
		}
		throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch shopping page", lastIoException);
	}

	private FetchedPage fetchOnce(URI uri) throws IOException {
		URI currentUri = uri;
		for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
			ShoppingUrlSafety.validatePublicHost(currentUri);
			Optional<URI> queryRedirect = queryRedirectTarget(currentUri);
			if (queryRedirect.isPresent()) {
				currentUri = queryRedirect.get();
				continue;
			}
			Connection.Response response = Jsoup.connect(currentUri.toString())
				.userAgent(ANDROID_USER_AGENT)
				.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
				.header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
				.header("Upgrade-Insecure-Requests", "1")
				.followRedirects(false)
				.ignoreContentType(true)
				.ignoreHttpErrors(true)
				.timeout(timeoutMillis)
				.maxBodySize(maxResponseBytes)
				.execute();

			if (isRedirect(response.statusCode())) {
				String location = response.header("Location");
				if (location != null && !location.isBlank()) {
					currentUri = currentUri.resolve(location);
					continue;
				}
			}

			String body = response.body();
			enforceBodySize(body);

			Optional<URI> clientSideRedirect = clientSideRedirectTarget(
				currentUri,
				response.contentType(),
				body
			);
			if (clientSideRedirect.isPresent()) {
				currentUri = clientSideRedirect.get();
				continue;
			}

			URI finalUri = URI.create(response.url().toString());
			Optional<FetchedPage> bunjangProductPage = bunjangProductApiPage(
				uri,
				finalUri,
				response.statusCode(),
				response.contentType(),
				response.body()
			);
			if (bunjangProductPage.isPresent()) {
				return bunjangProductPage.get();
			}

			return new FetchedPage(
				uri,
				finalUri,
				response.statusCode(),
				response.contentType(),
				body
			);
		}
		throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Shopping page redirected too many times");
	}

	private void enforceBodySize(String body) {
		if (body != null && body.getBytes(StandardCharsets.UTF_8).length > maxResponseBytes) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Shopping page response is too large");
		}
	}

	private boolean isRedirect(int statusCode) {
		return statusCode >= 300 && statusCode < 400;
	}

	private boolean isRetryableStatus(int statusCode) {
		return statusCode == 408 || statusCode == 429 || statusCode >= 500;
	}

	static Optional<URI> clientSideRedirectTarget(URI baseUri, String contentType, String body) {
		if (body == null || body.isBlank() || !mayContainClientSideRedirect(contentType)) {
			return Optional.empty();
		}

		Document document = Jsoup.parse(body, baseUri.toString());
		Optional<URI> metaRefreshRedirect = metaRefreshRedirectTarget(baseUri, document);
		if (metaRefreshRedirect.isPresent()) {
			return metaRefreshRedirect;
		}

		String baseHost = Optional.ofNullable(baseUri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		if (!isKnownBridgeHost(baseHost)) {
			return Optional.empty();
		}

		Optional<URI> bridgeJsonRedirect = bridgeJsonRedirectTarget(baseUri, body);
		if (bridgeJsonRedirect.isPresent()) {
			return bridgeJsonRedirect;
		}

		Optional<URI> assignedHttpUrl = assignedHttpUrlTarget(baseUri, body);
		if (assignedHttpUrl.isPresent()) {
			return assignedHttpUrl;
		}

		Optional<URI> appLinkWebUrl = appLinkWebUrlTarget(baseUri, body);
		if (appLinkWebUrl.isPresent()) {
			return appLinkWebUrl;
		}

		return locationRedirectTarget(baseUri, body);
	}

	private static boolean mayContainClientSideRedirect(String contentType) {
		if (contentType == null || contentType.isBlank()) {
			return true;
		}
		String normalized = contentType.toLowerCase(Locale.ROOT);
		return normalized.contains("text/html")
			|| normalized.contains("application/xhtml")
			|| normalized.contains("text/javascript")
			|| normalized.contains("application/javascript");
	}

	private static Optional<URI> metaRefreshRedirectTarget(URI baseUri, Document document) {
		return document.select("meta[http-equiv=refresh]").stream()
			.map(element -> element.attr("content"))
			.map(JsoupPageFetcher::metaRefreshUrl)
			.flatMap(Optional::stream)
			.map(target -> resolveHttpUri(baseUri, target))
			.flatMap(Optional::stream)
			.findFirst();
	}

	private static Optional<String> metaRefreshUrl(String content) {
		if (content == null || content.isBlank()) {
			return Optional.empty();
		}
		Matcher matcher = Pattern.compile("(?i)\\burl\\s*=\\s*(.+)$").matcher(content);
		if (!matcher.find()) {
			return Optional.empty();
		}
		return Optional.of(stripWrappingQuotes(matcher.group(1).trim()));
	}

	private static Optional<URI> bridgeJsonRedirectTarget(URI baseUri, String body) {
		Matcher matcher = JSON_HTTP_URL_PATTERN.matcher(body);
		while (matcher.find()) {
			Optional<URI> target = resolveHttpUri(baseUri, matcher.group(2));
			if (target.isPresent() && isLikelyBridgeTarget(baseUri, target.get())) {
				return target;
			}
		}
		matcher = JSON_APP_URL_PATTERN.matcher(body);
		while (matcher.find()) {
			Optional<URI> target = webUrlFromAppLink(matcher.group(2))
				.flatMap(value -> resolveHttpUri(baseUri, value));
			if (target.isPresent() && isLikelyBridgeTarget(baseUri, target.get())) {
				return target;
			}
		}
		return Optional.empty();
	}

	private static boolean isLikelyBridgeTarget(URI baseUri, URI targetUri) {
		String baseHost = Optional.ofNullable(baseUri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		String targetHost = Optional.ofNullable(targetUri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		if (baseHost.equals(targetHost)) {
			return false;
		}
		return isKnownBridgeHost(baseHost)
			&& (targetHost.contains("brand.naver.com")
				|| targetHost.contains("oliveyoung.co.kr")
				|| targetHost.contains("zigzag.kr")
				|| targetHost.contains("bunjang.co.kr")
				|| targetHost.contains("musinsa.com")
				|| targetHost.contains("29cm.co.kr")
				|| targetHost.contains("kream.co.kr")
				|| targetHost.contains("daangn.com"));
	}

	private static boolean isKnownBridgeHost(String host) {
		return host.equals("app.shopping.naver.com")
			|| host.equals("naver.me")
			|| host.equals("abr.ge")
			|| host.endsWith(".airbridge.io")
			|| host.endsWith(".onelink.me")
			|| host.endsWith(".app.link")
			|| host.equals("oy.run")
			|| host.equals("s.zigzag.kr")
			|| host.equals("link.zigzag.kr")
			|| host.equals("go.bgzt.link")
			|| host.equals("link.bunjang.co.kr")
			|| host.equals("share.bunjang.co.kr");
	}

	static Optional<URI> queryRedirectTarget(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		String rawQuery = uri.getRawQuery();
		if (!isKnownBridgeHost(host) || rawQuery == null || rawQuery.isBlank()) {
			return Optional.empty();
		}
		Optional<URI> bunjangProductTarget = bunjangAirbridgeProductTarget(uri, rawQuery);
		if (bunjangProductTarget.isPresent()) {
			return bunjangProductTarget;
		}
		for (String key : redirectQueryKeys()) {
			Optional<URI> target = queryParameter(rawQuery, key)
				.flatMap(value -> webUrlFromAppLink(value).or(() -> Optional.of(value)))
				.flatMap(value -> resolveHttpUri(uri, value));
			if (target.isPresent()) {
				return target;
			}
		}
		return Optional.empty();
	}

	private static Optional<URI> bunjangAirbridgeProductTarget(URI uri, String rawQuery) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		if (!host.equals("bunjang.airbridge.io")) {
			return Optional.empty();
		}
		boolean isProduct = queryParameter(rawQuery, "type")
			.map(type -> type.equalsIgnoreCase("product"))
			.orElse(false);
		return queryParameter(rawQuery, "val")
			.filter(productId -> isProduct)
			.filter(productId -> productId.matches("\\d+"))
			.map(productId -> URI.create("https://m.bunjang.co.kr/products/" + productId));
	}

	private Optional<FetchedPage> bunjangProductApiPage(
		URI requestedUri,
		URI finalUri,
		int statusCode,
		String contentType,
		String body
	) throws IOException {
		if (statusCode != 200 || !isBunjangProductShell(finalUri, contentType, body)) {
			return Optional.empty();
		}
		Optional<String> productId = bunjangProductId(finalUri);
		if (productId.isEmpty()) {
			return Optional.empty();
		}

		URI apiUri = URI.create("https://api.bunjang.co.kr/api/pms/v1/products/" + productId.get() + "/detail/web");
		ShoppingUrlSafety.validatePublicHost(apiUri);
		Connection.Response apiResponse = Jsoup.connect(apiUri.toString())
			.userAgent(ANDROID_USER_AGENT)
			.header("Accept", "application/json, text/plain, */*")
			.header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
			.header("Origin", "https://m.bunjang.co.kr")
			.header("Referer", finalUri.toString())
			.ignoreContentType(true)
			.ignoreHttpErrors(true)
			.timeout(timeoutMillis)
			.execute();

		if (apiResponse.statusCode() >= 400) {
			return Optional.empty();
		}

		return bunjangProductMetadataHtml(apiResponse.body())
			.map(html -> new FetchedPage(requestedUri, finalUri, 200, "text/html; charset=UTF-8", html));
	}

	private static boolean isBunjangProductShell(URI uri, String contentType, String body) {
		if (bunjangProductId(uri).isEmpty() || body == null || body.isBlank() || !mayContainClientSideRedirect(contentType)) {
			return false;
		}
		Document document = Jsoup.parse(body, uri.toString());
		return isBunjangSiteTitle(document.title())
			&& isBunjangSiteTitle(document.selectFirst("meta[property=og:title]") == null
				? null
				: document.selectFirst("meta[property=og:title]").attr("content"));
	}

	private static Optional<String> bunjangProductId(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		if (!host.equals("m.bunjang.co.kr") && !host.equals("bunjang.co.kr")) {
			return Optional.empty();
		}
		String path = Optional.ofNullable(uri.getPath()).orElse("");
		Matcher matcher = BUNJANG_PRODUCT_PATH_PATTERN.matcher(path);
		return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
	}

	static Optional<String> bunjangProductMetadataHtml(String apiBody) {
		if (apiBody == null || apiBody.isBlank()) {
			return Optional.empty();
		}
		try {
			JsonNode product = OBJECT_MAPPER.readTree(apiBody).path("data").path("product");
			if (product.isMissingNode() || product.isNull()) {
				return Optional.empty();
			}

			String title = jsonText(product, "name");
			String description = jsonText(product, "description");
			String price = jsonText(product, "price");
			String brandName = jsonText(product, "brand", "name");
			String imageUrl = normalizeBunjangImageUrl(jsonText(product, "imageUrl"));
			if (isBlank(title) && isBlank(price) && isBlank(imageUrl)) {
				return Optional.empty();
			}

			StringBuilder html = new StringBuilder("<!doctype html><html><head>");
			html.append("<title>").append(escapeHtml(firstNonBlank(title, "번개장터 상품"))).append("</title>");
			appendPropertyMeta(html, "og:title", title);
			appendNameMeta(html, "twitter:title", title);
			appendPropertyMeta(html, "og:description", description);
			appendNameMeta(html, "description", description);
			appendPropertyMeta(html, "og:image", imageUrl);
			appendNameMeta(html, "twitter:image", imageUrl);
			appendPropertyMeta(html, "product:price:amount", price);
			appendPropertyMeta(html, "kakao:commerce:price", price);
			appendPropertyMeta(html, "kakao:commerce:brand_name", brandName);
			html.append("</head><body></body></html>");
			return Optional.of(html.toString());
		} catch (JsonProcessingException exception) {
			return Optional.empty();
		}
	}

	private static String jsonText(JsonNode node, String... path) {
		JsonNode current = node;
		for (String key : path) {
			current = current.path(key);
			if (current.isMissingNode() || current.isNull()) {
				return null;
			}
		}
		String value = current.asText();
		return isBlank(value) ? null : value.trim();
	}

	private static String normalizeBunjangImageUrl(String imageUrl) {
		if (isBlank(imageUrl)) {
			return null;
		}
		String normalized = imageUrl.replace("{cnt}", "1").replace("{res}", "900");
		try {
			URI uri = URI.create(normalized);
			String scheme = Optional.ofNullable(uri.getScheme()).orElse("").toLowerCase(Locale.ROOT);
			return scheme.equals("http") || scheme.equals("https") ? uri.toString() : null;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static void appendPropertyMeta(StringBuilder html, String property, String content) {
		appendMeta(html, "property", property, content);
	}

	private static void appendNameMeta(StringBuilder html, String name, String content) {
		appendMeta(html, "name", name, content);
	}

	private static void appendMeta(StringBuilder html, String attributeName, String key, String content) {
		if (isBlank(content)) {
			return;
		}
		html.append("<meta ")
			.append(attributeName)
			.append("=\"")
			.append(escapeHtml(key))
			.append("\" content=\"")
			.append(escapeHtml(content))
			.append("\" />");
	}

	private static boolean isBunjangSiteTitle(String value) {
		if (isBlank(value)) {
			return false;
		}
		String normalized = value.replace(" ", "").trim().toLowerCase(Locale.ROOT);
		return normalized.equals("번개장터") || normalized.equals("bunjang");
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (!isBlank(value)) {
				return value.trim();
			}
		}
		return null;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String escapeHtml(String value) {
		return value.replace("&", "&amp;")
			.replace("\"", "&quot;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}

	private static Optional<URI> assignedHttpUrlTarget(URI baseUri, String body) {
		Matcher matcher = JS_HTTP_ASSIGNMENT_PATTERN.matcher(body);
		while (matcher.find()) {
			Optional<URI> target = resolveHttpUri(baseUri, matcher.group(3));
			if (target.isPresent()) {
				return target;
			}
		}
		return Optional.empty();
	}

	private static Optional<URI> appLinkWebUrlTarget(URI baseUri, String body) {
		Matcher matcher = JS_APP_LINK_ASSIGNMENT_PATTERN.matcher(body);
		while (matcher.find()) {
			Optional<URI> target = webUrlFromAppLink(matcher.group(2))
				.flatMap(url -> resolveHttpUri(baseUri, url));
			if (target.isPresent()) {
				return target;
			}
		}
		return Optional.empty();
	}

	private static Optional<String> webUrlFromAppLink(String rawAppLink) {
		try {
			URI appLink = URI.create(unescapeJsUrl(rawAppLink));
			String rawQuery = appLink.getRawQuery();
			if (rawQuery == null || rawQuery.isBlank()) {
				return Optional.empty();
			}
			for (String key : redirectQueryKeys()) {
				Optional<String> value = queryParameter(rawQuery, key);
				if (value.isPresent() && isHttpUrl(value.get())) {
					return value;
				}
			}
			return Optional.empty();
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private static String[] redirectQueryKeys() {
		return new String[] {
			"url", "link", "dst", "deepLink", "deep_link", "deeplink", "deeplinkUrl", "deeplink_url",
			"deepLinkUrl", "deep_link_url", "appLink", "app_link", "af_dp",
			"targetUrl", "target_url", "webUrl", "web_url", "productUrl", "product_url",
			"landingUrl", "landing_url", "fallbackUrl", "fallback_url", "redirectUrl", "redirect_url",
			"fallbackDesktop", "fallback_desktop", "fallbackWeb", "fallback_web",
			"af_web_dp", "af_android_url", "af_ios_url"
		};
	}

	private static Optional<String> queryParameter(String rawQuery, String key) {
		for (String parameter : rawQuery.split("&")) {
			String[] parts = parameter.split("=", 2);
			String parameterKey = urlDecode(parts[0]);
			if (key.equals(parameterKey)) {
				return Optional.of(parts.length == 1 ? "" : urlDecode(parts[1]));
			}
		}
		return Optional.empty();
	}

	private static Optional<URI> locationRedirectTarget(URI baseUri, String body) {
		Optional<URI> assignedLocation = firstRegexUri(baseUri, body, JS_LOCATION_ASSIGNMENT_PATTERN, 2);
		if (assignedLocation.isPresent()) {
			return assignedLocation;
		}
		return firstRegexUri(baseUri, body, JS_LOCATION_CALL_PATTERN, 2);
	}

	private static Optional<URI> firstRegexUri(URI baseUri, String body, Pattern pattern, int urlGroup) {
		Matcher matcher = pattern.matcher(body);
		while (matcher.find()) {
			Optional<URI> target = resolveHttpUri(baseUri, matcher.group(urlGroup));
			if (target.isPresent()) {
				return target;
			}
		}
		return Optional.empty();
	}

	private static Optional<URI> resolveHttpUri(URI baseUri, String rawTarget) {
		try {
			String unescaped = unescapeJsUrl(rawTarget);
			URI target = baseUri.resolve(unescaped);
			String scheme = Optional.ofNullable(target.getScheme()).orElse("").toLowerCase(Locale.ROOT);
			if (!scheme.equals("http") && !scheme.equals("https")) {
				return Optional.empty();
			}
			if (isAppStoreUrl(target)) {
				return Optional.empty();
			}
			return Optional.of(normalizeKnownProductUri(target));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private static URI normalizeKnownProductUri(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		if ((host.equals("brand.naver.com") || host.equals("m.brand.naver.com"))
			&& Optional.ofNullable(uri.getPath()).orElse("").contains("/products/")) {
			return rebuildUriWithHostAndQuery(uri, "m.brand.naver.com", removeQueryParameter(uri.getRawQuery(), "NaPm"));
		}
		return uri;
	}

	private static URI rebuildUriWithHostAndQuery(URI uri, String host, String rawQuery) {
		try {
			return new URI(uri.getScheme(), uri.getUserInfo(), host, uri.getPort(), uri.getPath(), rawQuery, uri.getFragment());
		} catch (URISyntaxException exception) {
			return uri;
		}
	}

	private static String removeQueryParameter(String rawQuery, String keyToRemove) {
		if (rawQuery == null || rawQuery.isBlank()) {
			return rawQuery;
		}
		StringBuilder filtered = new StringBuilder();
		for (String parameter : rawQuery.split("&")) {
			String key = parameter.split("=", 2)[0];
			if (key.equalsIgnoreCase(keyToRemove)) {
				continue;
			}
			if (!filtered.isEmpty()) {
				filtered.append('&');
			}
			filtered.append(parameter);
		}
		return filtered.isEmpty() ? null : filtered.toString();
	}

	private static boolean isAppStoreUrl(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		return host.equals("apps.apple.com")
			|| host.equals("itunes.apple.com")
			|| host.equals("play.google.com");
	}

	private static boolean isHttpUrl(String value) {
		String normalized = value.toLowerCase(Locale.ROOT);
		return normalized.startsWith("http://") || normalized.startsWith("https://");
	}

	private static String unescapeJsUrl(String value) {
		return Parser.unescapeEntities(value, true)
			.replace("\\/", "/")
			.replace("\\u002F", "/")
			.replace("\\u002f", "/")
			.replace("\\u003A", ":")
			.replace("\\u003a", ":")
			.replace("\\u003F", "?")
			.replace("\\u003f", "?")
			.replace("\\u003D", "=")
			.replace("\\u003d", "=")
			.replace("\\u0026", "&")
			.replace("\\x26", "&");
	}

	private static String stripWrappingQuotes(String value) {
		if (value.length() >= 2) {
			char first = value.charAt(0);
			char last = value.charAt(value.length() - 1);
			if ((first == '\'' || first == '"') && first == last) {
				return value.substring(1, value.length() - 1);
			}
		}
		return value;
	}

	private static String urlDecode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
}
