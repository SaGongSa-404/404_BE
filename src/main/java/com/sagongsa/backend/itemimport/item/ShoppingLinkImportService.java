package com.sagongsa.backend.itemimport.item;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.domain.enums.ItemStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShoppingLinkImportService {

	private static final Pattern PRICE_WITH_CURRENCY_PATTERN = Pattern.compile("\\b([0-9]{1,3}(?:,[0-9]{3})+)\\s*원");
	private static final Pattern WON_PRICE_AMOUNT_PATTERN = Pattern.compile("([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)\\s*원");
	private static final Pattern PRICE_AMOUNT_PATTERN = Pattern.compile("([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)");
	private static final Pattern MUSINSA_FINAL_PRICE_PATTERN = Pattern.compile(
		"(?s)\\\"goodsPrice\\\"\\s*:\\s*\\{.{0,2000}?\\\"finalPrice\\\"\\s*:\\s*([0-9]+)"
	);
	private static final Pattern TWENTY_NINE_CM_DISPLAYED_PRICE_PATTERN = Pattern.compile(
		"(?s)\"id\"\\s*:\\s*\"pdp_product_price\".{0,500}?\"children\"\\s*:\\s*\\[\"([0-9][0-9,]*)\""
	);
	private static final Pattern ABLY_GOODS_PATH_PATTERN = Pattern.compile(
		"(?:^|/)goods/([0-9]+)(?![0-9])"
	);
	private static final Set<String> NOISE_IMAGE_KEYWORDS = Set.of("logo", "icon", "sprite", "badge", "banner");
	private static final Set<String> TRACKING_QUERY_KEYS = Set.of(
		"fbclid", "gclid", "igshid", "mc_cid", "mc_eid", "n_media", "n_query", "n_rank", "n_ad_group"
	);
	private static final Set<String> APP_SHARE_TRACKING_QUERY_KEYS = Set.of(
		"source_caller", "pid", "shortlink", "short_id", "is_retargeting", "referrer",
		"af_siteid", "af_dp", "af_referrer_uid", "af_channel", "af_force_deeplink", "af_click_lookback",
		"deep_link_value", "tracking_content", "airbridge_referrer", "https_deeplink"
	);
	private static final Set<String> OLIVE_YOUNG_HOSTS = Set.of("oliveyoung.co.kr", "m.oliveyoung.co.kr");
	private static final Set<String> ZIGZAG_PRODUCT_STATE_HOSTS = Set.of(
		"zigzag.kr", "www.zigzag.kr", "store.zigzag.kr"
	);
	private static final Set<String> VERIFIED_PRODUCT_HOSTS = Set.of(
		"musinsa.com", "www.musinsa.com",
		"daangn.com", "www.daangn.com",
		"bunjang.co.kr", "www.bunjang.co.kr", "m.bunjang.co.kr",
		"zigzag.kr", "www.zigzag.kr", "store.zigzag.kr", "s.zigzag.kr", "link.zigzag.kr",
		"oliveyoung.co.kr", "www.oliveyoung.co.kr", "m.oliveyoung.co.kr",
		"brand.naver.com", "m.brand.naver.com",
		"smartstore.naver.com", "m.smartstore.naver.com",
		"kream.co.kr", "www.kream.co.kr",
		"product.29cm.co.kr", "www.29cm.co.kr",
		"m.a-bly.com"
	);
	private static final double DEFAULT_CATEGORY_CONFIDENCE = 0.35d;
	private static final int MAX_NESTED_URL_DECODE_COUNT = 6;

	private final PageFetcher pageFetcher;
	private final ObjectMapper objectMapper;

	public ShoppingLinkImportService(PageFetcher pageFetcher, ObjectMapper objectMapper) {
		this.pageFetcher = pageFetcher;
		this.objectMapper = objectMapper;
	}

	public ShoppingLinkImportResponse importLink(ShoppingLinkImportRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}

		ItemInputSource inputSource = Optional.ofNullable(request.inputSource())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "inputSource is required"));

		return switch (inputSource) {
			case SHARE -> importSharedLink(request);
			case DIRECT_INPUT -> importManualInput(request);
		};
	}

	private ShoppingLinkImportResponse importSharedLink(ShoppingLinkImportRequest request) {
		URI originalUri = parseHttpUri(request.url(), "url is required for SHARE");
		NormalizationResult normalized = normalizeShoppingUri(originalUri);
		FetchedPage page = pageFetcher.fetch(normalized.uri());

		if (page.statusCode() >= 400) {
			throw new ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"Shopping page returned " + page.statusCode()
			);
		}

		Document document = Jsoup.parse(page.body(), page.finalUri().toString());
		if (isBlockedShoppingPage(document, page)) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Shopping page access was challenged");
		}
		ExtractionResult extracted = extractFromPage(document, page);
		List<String> warnings = new ArrayList<>(normalized.warnings());
		validateVerifiedProduct(originalUri, page.finalUri(), extracted);

		if (extracted.isPartial()) {
			warnings.add("상품 메타데이터가 일부만 추출되었습니다.");
		}

		if (isBlank(extracted.title())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unable to extract shopping item title");
		}
		if (extracted.price() == null && isBlank(extracted.imageUrl())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unable to extract shopping metadata");
		}

		ItemCategory category = classifyCategory(sourceDomain(page.finalUri()), extracted.title(), extracted.summary());
		SavedItemDraft item = new SavedItemDraft(
			ItemInputSource.SHARE,
			originalUri.toString(),
			page.finalUri().toString(),
			extracted.title(),
			firstNonBlank(request.brandName(), extracted.brandName()),
			extracted.summary(),
			extracted.imageUrl(),
			extracted.price(),
			extracted.currencyCode(),
			category,
			category == ItemCategory.ETC ? null : DEFAULT_CATEGORY_CONFIDENCE,
			false,
			ItemStatus.SAVED
		);
		ItemSourceMetadataDraft sourceMetadata = new ItemSourceMetadataDraft(
			sourceDomain(page.finalUri()),
			extracted.rawTitle(),
			extracted.rawDescription(),
			extracted.rawPriceText(),
			toJson(extracted.rawPayloadJson()),
			Instant.now(),
			extracted.method()
		);

		return new ShoppingLinkImportResponse(
			extracted.isPartial() ? "PARTIAL" : "SUCCESS",
			item,
			sourceMetadata,
			toWishlistSaveDraft(item, sourceMetadata),
			List.copyOf(warnings)
		);
	}

	private ShoppingLinkImportResponse importManualInput(ShoppingLinkImportRequest request) {
		if (isBlank(request.title())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required for DIRECT_INPUT");
		}
		if (request.price() != null && request.price() < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price must be zero or greater for DIRECT_INPUT");
		}

		URI normalizedUri = null;
		List<String> warnings = new ArrayList<>();
		if (!isBlank(request.url())) {
			NormalizationResult normalizationResult = normalizeShoppingUri(parseHttpUri(request.url(), "Invalid url"));
			normalizedUri = normalizationResult.uri();
			warnings.addAll(normalizationResult.warnings());
		}

		String normalizedTitle = normalizeWhitespace(request.title());
		String normalizedBrandName = normalizeWhitespace(request.brandName());
		String normalizedImageUrl = normalizeImageUrl(request.imageUrl());
		ItemCategory category = classifyCategory(
			normalizedUri == null ? null : sourceDomain(normalizedUri),
			normalizedTitle,
			null
		);

		SavedItemDraft item = new SavedItemDraft(
			ItemInputSource.DIRECT_INPUT,
			blankToNull(request.url()),
			normalizedUri == null ? null : normalizedUri.toString(),
			normalizedTitle,
			normalizedBrandName,
			null,
			normalizedImageUrl,
			request.price(),
			request.price() == null ? null : "KRW",
			category,
			null,
			false,
			ItemStatus.SAVED
		);
		ItemSourceMetadataDraft sourceMetadata = new ItemSourceMetadataDraft(
			normalizedUri == null ? null : sourceDomain(normalizedUri),
			normalizedTitle,
			null,
			request.price() == null ? null : String.valueOf(request.price()),
			toJson(Map.of(
				"inputSource", ItemInputSource.DIRECT_INPUT.name(),
				"manualFields", List.of("title", "brandName", "price", "imageUrl")
			)),
			Instant.now(),
			"MANUAL"
		);

		return new ShoppingLinkImportResponse(
			"SUCCESS",
			item,
			sourceMetadata,
			toWishlistSaveDraft(item, sourceMetadata),
			List.copyOf(warnings)
		);
	}

	private NormalizationResult normalizeShoppingUri(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		List<String> warnings = new ArrayList<>();
		URI normalized = uri;

		if ("www.coupang.com".equals(host) && uri.getPath() != null && uri.getPath().startsWith("/vp/products/")) {
			String[] segments = uri.getPath().split("/");
			if (segments.length >= 4) {
				normalized = URI.create("https://m.coupang.com/nm/products/" + segments[3]);
				warnings.add("쿠팡 링크를 모바일 상품 경로로 정규화했습니다.");
			}
		}
		if ("store.zigzag.kr".equals(host) && uri.getPath() != null
			&& uri.getPath().matches("/(?:app/)?catalog/products/[0-9]+/?")) {
			normalized = rebuildUriWithHost(uri, "zigzag.kr");
			warnings.add("지그재그 스토어 링크를 공개 상품 경로로 정규화했습니다.");
		}
		Optional<String> ablyProductId = "ably.airbridge.io".equals(host)
			? extractAblyShareProductId(uri)
			: Optional.empty();
		if (ablyProductId.isPresent()) {
			normalized = URI.create("https://m.a-bly.com/goods/" + ablyProductId.get());
			warnings.add("에이블리 공유 링크를 모바일 상품 경로로 정규화했습니다.");
		}

		if (normalized.getRawQuery() != null && !normalized.getRawQuery().isBlank()) {
			String filteredQuery = removeTrackingQueryParameters(normalized.getRawQuery(), normalized.getHost());
			if (!Objects.equals(normalized.getRawQuery(), filteredQuery)) {
				normalized = rebuildUri(normalized, filteredQuery);
				warnings.add("추적성 query parameter를 제거했습니다.");
			}
		}

		return new NormalizationResult(normalized, List.copyOf(warnings));
	}

	private Optional<String> extractAblyShareProductId(URI uri) {
		Optional<String> pathProductId = extractAblyProductIdFromEncodedValue(uri.getRawPath());
		if (pathProductId.isPresent()) {
			return pathProductId;
		}
		return extractAblyProductIdFromEncodedValue(uri.getRawQuery());
	}

	private Optional<String> extractAblyProductIdFromEncodedValue(String rawValue) {
		String candidate = rawValue;
		for (int decodeCount = 0; decodeCount <= MAX_NESTED_URL_DECODE_COUNT && !isBlank(candidate); decodeCount++) {
			Matcher matcher = ABLY_GOODS_PATH_PATTERN.matcher(candidate);
			if (matcher.find()) {
				return Optional.of(matcher.group(1));
			}
			try {
				String decoded = URLDecoder.decode(candidate, StandardCharsets.UTF_8);
				if (decoded.equals(candidate)) {
					break;
				}
				candidate = decoded;
			} catch (IllegalArgumentException exception) {
				break;
			}
		}
		return Optional.empty();
	}

	private ExtractionResult extractFromPage(Document document, FetchedPage page) {
		String html = page.body();
		String sourceDomain = sourceDomain(page.finalUri());
		ZigzagMetadata zigzagMetadata = zigzagMetadata(document, page.finalUri());
		EmbeddedMetadata embeddedMetadata = embeddedMetadata(document);
		String productJsonLdTitle = productJsonLdText(document, "name");
		String productJsonLdDescription = productJsonLdText(document, "description");
		String productJsonLdPrice = productJsonLdText(document, "offers.price");
		String productJsonLdCurrency = productJsonLdText(document, "offers.priceCurrency");
		String productJsonLdImage = productJsonLdText(document, "image");
		String productMetaPrice = metaContent(document, "meta[property=product:price:amount]");
		String ablyDisplayedPrice = isAblyDomain(sourceDomain) && isPositivePrice(productMetaPrice)
			? productMetaPrice
			: null;
		String siteSalePrice = isOliveYoungDomain(sourceDomain)
			? metaContent(document, "meta[property=eg:salePrice]")
			: null;
		String musinsaFinalPrice = isMusinsaDomain(sourceDomain)
			? findByRegex(html, MUSINSA_FINAL_PRICE_PATTERN)
			: null;
		String twentyNineCmDisplayedPrice = twentyNineCmDisplayedPrice(document, html, sourceDomain);
		String summary = firstNonBlank(
			productJsonLdDescription,
			embeddedMetadata.description(),
			metaContent(document, "meta[property=og:description]"),
			metaContent(document, "meta[name=description]"),
			metaContent(document, "meta[name=twitter:description]"),
			jsonLdText(document, "description")
		);
		String title = firstProductTitle(
			sourceDomain,
			zigzagMetadata.title(),
			productJsonLdTitle,
			embeddedMetadata.title(),
			metaContent(document, "meta[property=og:title]"),
			metaContent(document, "meta[name=twitter:title]"),
			normalizeWhitespace(document.title()),
			firstText(document, "h1", "[data-testid=productName]", ".prod-buy-header__title")
		);

		String brandName = firstNonBlank(
			productJsonLdText(document, "brand.name"),
			productJsonLdText(document, "brand"),
			metaContent(document, "meta[property=kakao:commerce:brand_name]"),
			firstText(document, "[itemprop=brand]", ".prod-brand-name", ".brand-name")
		);

		Integer price = firstNonNull(
			parseListedPrice(siteSalePrice),
			parseListedPrice(musinsaFinalPrice),
			parseListedPrice(zigzagMetadata.priceText()),
			parseListedPrice(twentyNineCmDisplayedPrice),
			parseListedPrice(ablyDisplayedPrice),
			parseListedPrice(productJsonLdPrice),
			parseListedPrice(productMetaPrice),
			parseListedPrice(embeddedMetadata.priceText()),
			parseListedPrice(metaContent(document, "meta[property=og:price:amount]")),
			parseListedPrice(metaContent(document, "meta[property=kakao:commerce:price]")),
			parseListedPrice(jsonLdText(document, "price")),
			parseListedPrice(findByRegex(html, PRICE_WITH_CURRENCY_PATTERN))
		);
		String rawPriceText = firstValidPriceText(
			siteSalePrice,
			musinsaFinalPrice,
			zigzagMetadata.priceText(),
			twentyNineCmDisplayedPrice,
			ablyDisplayedPrice,
			productJsonLdPrice,
			productMetaPrice,
			embeddedMetadata.priceText(),
			metaContent(document, "meta[property=og:price:amount]"),
			metaContent(document, "meta[property=kakao:commerce:price]"),
			jsonLdText(document, "price"),
			findByRegex(html, PRICE_WITH_CURRENCY_PATTERN)
		);
		String currencyCode = resolveCurrencyCode(
			sourceDomain,
			productJsonLdCurrency,
			metaContent(document, "meta[property=product:price:currency]"),
			metaContent(document, "meta[property=og:price:currency]")
		);

		String imageUrl = firstNonBlank(
			normalizeImageUrl(zigzagMetadata.imageUrl()),
			normalizeImageUrl(productJsonLdImage),
			normalizeImageUrl(embeddedMetadata.imageUrl()),
			normalizeImageUrl(metaContent(document, "meta[property=og:image]")),
			normalizeImageUrl(metaContent(document, "meta[name=twitter:image]")),
			bestImage(document)
		);

		String method = "OPEN_GRAPH";
		if (zigzagMetadata.hasAnyValue()) {
			method = "ZIGZAG_PRODUCT_STATE";
		} else if (!isBlank(twentyNineCmDisplayedPrice)) {
			method = "TWENTY_NINE_CM_PAGE_STATE";
		} else if (!isBlank(ablyDisplayedPrice)) {
			method = "ABLY_PRODUCT_META";
		} else if (!isBlank(productJsonLdTitle) || !isBlank(productJsonLdPrice) || !isBlank(productJsonLdImage)) {
			method = "JSON_LD";
		} else if (embeddedMetadata.hasAnyValue()) {
			method = "EMBEDDED_JSON";
		} else if (title != null || price != null || imageUrl != null) {
			method = "HTML_META";
		}

		Map<String, Object> rawPayloadJson = new LinkedHashMap<>();
		rawPayloadJson.put("requestedUrl", page.requestedUri().toString());
		rawPayloadJson.put("finalUrl", page.finalUri().toString());
		rawPayloadJson.put("statusCode", page.statusCode());
		rawPayloadJson.put("contentType", page.contentType());
		rawPayloadJson.put("title", title);
		rawPayloadJson.put("brandName", brandName);
		rawPayloadJson.put("summary", summary);
		rawPayloadJson.put("price", price);
		rawPayloadJson.put("rawPriceText", rawPriceText);
		rawPayloadJson.put("currencyCode", currencyCode);
		rawPayloadJson.put("imageUrl", imageUrl);
		rawPayloadJson.put("method", method);
		rawPayloadJson.put("verifiedProduct", isVerifiedProductUri(page.finalUri()));

		return new ExtractionResult(
			title,
			brandName,
			summary,
			price,
			rawPriceText,
			currencyCode,
			imageUrl,
			method,
			rawPayloadJson
		);
	}

	private String twentyNineCmDisplayedPrice(Document document, String html, String sourceDomain) {
		if (!"29cm.co.kr".equals(sourceDomain) && !"product.29cm.co.kr".equals(sourceDomain)) {
			return null;
		}
		String renderedPrice = firstText(document, "#pdp_product_price");
		if (!isBlank(renderedPrice)) {
			return renderedPrice;
		}
		return findByRegex(html.replace("\\\"", "\""), TWENTY_NINE_CM_DISPLAYED_PRICE_PATTERN);
	}

	private boolean isPositivePrice(String value) {
		Integer parsed = parseListedPrice(value);
		return parsed != null && parsed > 0;
	}

	private boolean isAblyDomain(String sourceDomain) {
		return "m.a-bly.com".equals(sourceDomain);
	}

	private ZigzagMetadata zigzagMetadata(Document document, URI finalUri) {
		String host = Optional.ofNullable(finalUri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		if (!ZIGZAG_PRODUCT_STATE_HOSTS.contains(host)) {
			return ZigzagMetadata.empty();
		}

		Matcher productIdMatcher = Pattern.compile("/(?:app/)?catalog/products/([0-9]+)").matcher(
			Optional.ofNullable(finalUri.getPath()).orElse("")
		);
		if (!productIdMatcher.find()) {
			return ZigzagMetadata.empty();
		}
		String productId = productIdMatcher.group(1);

		for (Element scriptElement : document.select("script")) {
			String rawScript = firstNonBlank(scriptElement.data(), scriptElement.html());
			String json = jsonCandidate(rawScript);
			if (json == null) {
				continue;
			}
			try {
				JsonNode product = findZigzagProduct(objectMapper.readTree(json), productId);
				if (product == null) {
					continue;
				}
				String priceText = firstNonBlank(
					textAt(product, "product_price", "display_final_price", "final_price", "price"),
					textAt(product, "product_price", "store_discount_info", "discount_price"),
					textAt(product, "product_price", "max_price_info", "price")
				);
				String imageUrl = firstZigzagProductImage(product.path("product_image_list"));
				return new ZigzagMetadata(
					normalizeWhitespace(product.path("name").asText(null)),
					priceText,
					imageUrl
				);
			} catch (JsonProcessingException ignored) {
				// Continue until the target product state is found in another script.
			}
		}
		return ZigzagMetadata.empty();
	}

	private JsonNode findZigzagProduct(JsonNode node, String productId) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isObject()
			&& productId.equals(node.path("id").asText())
			&& (node.has("product_price") || node.has("product_image_list"))) {
			return node;
		}
		for (JsonNode child : node) {
			JsonNode product = findZigzagProduct(child, productId);
			if (product != null) {
				return product;
			}
		}
		return null;
	}

	private String textAt(JsonNode node, String... path) {
		JsonNode current = node;
		for (String segment : path) {
			current = current.path(segment);
			if (current.isMissingNode() || current.isNull()) {
				return null;
			}
		}
		return current.isValueNode() ? normalizeWhitespace(current.asText()) : null;
	}

	private String firstZigzagProductImage(JsonNode images) {
		if (!images.isArray()) {
			return null;
		}
		for (JsonNode image : images) {
			String imageUrl = firstNonBlank(
				textAt(image, "pdp_thumbnail_url"),
				textAt(image, "pdp_static_image_url"),
				textAt(image, "url"),
				textAt(image, "origin_url")
			);
			if (!isBlank(imageUrl)) {
				return imageUrl;
			}
		}
		return null;
	}

	private URI rebuildUriWithHost(URI uri, String host) {
		try {
			return new URI("https", null, host, -1, uri.getPath(), uri.getRawQuery(), null);
		} catch (java.net.URISyntaxException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shopping url", exception);
		}
	}

	private boolean isMusinsaDomain(String sourceDomain) {
		return sourceDomain != null && (sourceDomain.equals("musinsa.com") || sourceDomain.equals("www.musinsa.com"));
	}

	private void validateVerifiedProduct(URI requestedUri, URI finalUri, ExtractionResult extracted) {
		boolean requestedVerifiedHost = isVerifiedProductHost(requestedUri);
		boolean finalVerifiedHost = isVerifiedProductHost(finalUri);
		if (!requestedVerifiedHost && !finalVerifiedHost) {
			return;
		}
		if (requestedVerifiedHost && !finalVerifiedHost) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Shopping product redirected outside its verified domain");
		}
		if (!isVerifiedProductUri(finalUri)) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Only product detail URLs can be verified");
		}
		if (isBlank(extracted.title()) || isBlank(extracted.imageUrl()) || isBlank(extracted.currencyCode())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unable to verify complete shopping metadata");
		}
		if (extracted.price() == null || !"KRW".equals(extracted.currencyCode())) {
			throw new ResponseStatusException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"Only verified integer KRW prices are supported"
			);
		}
	}

	private boolean isVerifiedProductHost(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		return VERIFIED_PRODUCT_HOSTS.contains(host);
	}

	private boolean isVerifiedProductUri(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		String path = Optional.ofNullable(uri.getPath()).orElse("");
		if (host.equals("musinsa.com") || host.equals("www.musinsa.com")) {
			return path.matches("/products/[0-9]+/?");
		}
		if (host.equals("daangn.com") || host.equals("www.daangn.com")) {
			return path.matches("/articles/[0-9]+/?") || path.startsWith("/kr/buy-sell/");
		}
		if (host.endsWith("bunjang.co.kr")) {
			return path.matches("/products/[0-9]+/?");
		}
		if (ZIGZAG_PRODUCT_STATE_HOSTS.contains(host)) {
			return path.matches("/(?:app/)?catalog/products/[0-9]+/?");
		}
		if (host.equals("oliveyoung.co.kr") || host.equals("www.oliveyoung.co.kr") || host.equals("m.oliveyoung.co.kr")) {
			String query = Optional.ofNullable(uri.getRawQuery()).orElse("");
			return path.equals("/store/goods/getGoodsDetail.do")
				&& query.matches("(?:^|.*&)goodsNo=[A-Z][0-9]+(?:&.*|$)");
		}
		if (host.equals("brand.naver.com") || host.equals("m.brand.naver.com")) {
			return path.matches("/[a-zA-Z0-9_-]+/products/[0-9]+/?");
		}
		if (host.equals("smartstore.naver.com") || host.equals("m.smartstore.naver.com")) {
			return path.matches("/[a-zA-Z0-9_-]+/products/[0-9]+/?");
		}
		if (host.equals("kream.co.kr") || host.equals("www.kream.co.kr")) {
			return path.matches("/products/[0-9]+/?");
		}
		if (host.equals("product.29cm.co.kr")) {
			return path.matches("/catalog/[0-9]+/?");
		}
		if (host.equals("www.29cm.co.kr")) {
			return path.matches("/products/[0-9]+/?");
		}
		if (host.equals("m.a-bly.com")) {
			return path.matches("/goods/[0-9]+/?");
		}
		return false;
	}

	private String resolveCurrencyCode(String sourceDomain, String... candidates) {
		for (String candidate : candidates) {
			String normalized = normalizeWhitespace(candidate);
			if (normalized != null && normalized.matches("(?i)[A-Z]{3}")) {
				return normalized.toUpperCase(Locale.ROOT);
			}
		}
		return "KRW";
	}

	private ItemCategory classifyCategory(String sourceDomain, String title, String summary) {
		String haystack = ((sourceDomain == null ? "" : sourceDomain) + " " + (title == null ? "" : title) + " " + (summary == null ? "" : summary))
			.toLowerCase(Locale.ROOT);

		if (containsAny(haystack, "셔츠", "니트", "가디건", "팬츠", "아우터", "원피스", "스니커즈", "신발", "가방", "musinsa", "zigzag", "ably", "29cm")) {
			return ItemCategory.FASHION;
		}
		if (containsAny(haystack, "립스틱", "립밤", "립틴트", "립글로스", "립라이너", "쿠션", "에센스", "크림", "마스크팩", "oliveyoung", "향수", "샴푸")) {
			return ItemCategory.BEAUTY;
		}
		if (containsAny(haystack, "이어폰", "헤드폰", "키보드", "마우스", "노트북", "갤럭시", "아이폰", "ipad", "monitor", "ssd", "보조배터리", "충전기")) {
			return ItemCategory.DIGITAL;
		}
		if (containsAny(haystack, "컵", "머그", "침구", "수납", "조명", "청소", "커피머신", "테이블")) {
			return ItemCategory.LIVING;
		}
		if (containsAny(haystack, "간식", "음료", "커피", "프로틴", "식품", "라면", "과자")) {
			return ItemCategory.FOOD;
		}
		if (containsAny(haystack, "레고", "피규어", "게임", "취미", "캠핑", "자전거", "스포츠", "레저", "유도", "도복", "운동")) {
			return ItemCategory.HOBBY;
		}
		if (containsAny(haystack, "subscription", "멤버십", "정기구독", "월간", "연간 구독")) {
			return ItemCategory.SUBSCRIPTION;
		}
		return ItemCategory.ETC;
	}

	private String bestImage(Document document) {
		return document.select("img[src], img[data-src], img[srcset]").stream()
			.map(element -> firstNonBlank(element.absUrl("src"), element.absUrl("data-src"), firstSrcSetUrl(element.attr("srcset"))))
			.map(this::normalizeImageUrl)
			.filter(Objects::nonNull)
			.filter(url -> NOISE_IMAGE_KEYWORDS.stream().noneMatch(url.toLowerCase(Locale.ROOT)::contains))
			.findFirst()
			.orElse(null);
	}

	private String firstSrcSetUrl(String srcSet) {
		if (isBlank(srcSet)) {
			return null;
		}
		String firstCandidate = srcSet.split(",")[0].trim();
		return firstCandidate.split("\\s+")[0];
	}

	private String jsonLdText(Document document, String path) {
		for (Element scriptElement : document.select("script[type=application/ld+json]")) {
			String rawJson = scriptElement.data();
			if (isBlank(rawJson)) {
				rawJson = scriptElement.html();
			}
			String value = jsonValue(rawJson, path);
			if (!isBlank(value)) {
				return normalizeWhitespace(value);
			}
		}
		return null;
	}

	private String productJsonLdText(Document document, String path) {
		for (Element scriptElement : document.select("script[type=application/ld+json]")) {
			String rawJson = firstNonBlank(scriptElement.data(), scriptElement.html());
			if (isBlank(rawJson)) {
				continue;
			}
			try {
				JsonNode productNode = findJsonLdProduct(objectMapper.readTree(rawJson));
				String value = productNode == null ? null : searchJson(productNode, path.split("\\."));
				if (!isBlank(value)) {
					return normalizeStructuredDataText(value);
				}
			} catch (JsonProcessingException ignored) {
				// Ignore invalid third-party structured data and continue with other candidates.
			}
		}
		return null;
	}

	private JsonNode findJsonLdProduct(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isObject() && isJsonLdProductType(node.get("@type"))) {
			return node;
		}
		for (JsonNode child : node) {
			JsonNode product = findJsonLdProduct(child);
			if (product != null) {
				return product;
			}
		}
		return null;
	}

	private boolean isJsonLdProductType(JsonNode typeNode) {
		if (typeNode == null || typeNode.isNull()) {
			return false;
		}
		if (typeNode.isArray()) {
			for (JsonNode value : typeNode) {
				if (isJsonLdProductType(value)) {
					return true;
				}
			}
			return false;
		}
		return typeNode.isTextual() && "product".equalsIgnoreCase(typeNode.asText());
	}

	private String jsonValue(String rawJson, String path) {
		try {
			JsonNode root = objectMapper.readTree(rawJson);
			return searchJson(root, path.split("\\."));
		} catch (JsonProcessingException exception) {
			return null;
		}
	}

	private EmbeddedMetadata embeddedMetadata(Document document) {
		EmbeddedMetadata metadata = EmbeddedMetadata.empty();
		for (Element scriptElement : document.select("script")) {
			String rawScript = firstNonBlank(scriptElement.data(), scriptElement.html());
			String json = jsonCandidate(rawScript);
			if (json == null) {
				continue;
			}
			try {
				metadata = metadata.merge(embeddedMetadata(objectMapper.readTree(json)));
				if (metadata.hasAllProductValues()) {
					return metadata;
				}
			} catch (JsonProcessingException ignored) {
				// Third-party commerce pages often include non-JSON scripts; skip those safely.
			}
		}
		return metadata;
	}

	private EmbeddedMetadata embeddedMetadata(JsonNode node) {
		return embeddedMetadata(node, false);
	}

	private EmbeddedMetadata embeddedMetadata(JsonNode node, boolean productContext) {
		if (node == null || node.isNull()) {
			return EmbeddedMetadata.empty();
		}
		if (node.isArray()) {
			EmbeddedMetadata metadata = EmbeddedMetadata.empty();
			for (JsonNode child : node) {
				metadata = metadata.merge(embeddedMetadata(child, productContext));
				if (metadata.hasAllProductValues()) {
					return metadata;
				}
			}
			return metadata;
		}
		if (!node.isObject()) {
			return EmbeddedMetadata.empty();
		}

		EmbeddedMetadata metadata = EmbeddedMetadata.empty();
		Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> field = fields.next();
			String key = field.getKey().toLowerCase(Locale.ROOT);
			JsonNode value = field.getValue();
			boolean nestedProductContext = productContext || isProductContainerKey(key);
			if (value.isValueNode()) {
				String text = normalizeWhitespace(value.asText());
				if (isPlaceholderMetadataText(text)) {
					metadata = metadata.merge(embeddedMetadata(value, nestedProductContext));
					continue;
				}
				if (isProductTitleKey(key, productContext)) {
					metadata = metadata.withTitle(text, keyPriority(key, productContext));
				} else if (isDescriptionKey(key)) {
					metadata = metadata.withDescription(text, keyPriority(key, productContext));
				} else if (isPriceKey(key, productContext) && parseListedPrice(text) != null) {
					metadata = metadata.withPriceText(text, keyPriority(key, productContext));
				} else if (isImageKey(key, productContext)) {
					metadata = metadata.withImageUrl(text, keyPriority(key, productContext));
				}
			}
			metadata = metadata.merge(embeddedMetadata(value, nestedProductContext));
			if (metadata.hasAllProductValues()) {
				return metadata;
			}
		}
		return metadata;
	}

	private String jsonCandidate(String rawScript) {
		if (isBlank(rawScript)) {
			return null;
		}
		String trimmed = rawScript.trim();
		if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
			return trimmed;
		}
		int start = trimmed.indexOf('{');
		int end = trimmed.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return null;
		}
		return trimmed.substring(start, end + 1);
	}

	private boolean isProductContainerKey(String key) {
		return key.contains("product") || key.contains("goods") || key.equals("item");
	}

	private boolean isProductTitleKey(String key, boolean productContext) {
		return isStrongProductTitleKey(key) || (productContext && (key.equals("name") || key.equals("title")));
	}

	private boolean isStrongProductTitleKey(String key) {
		return key.equals("productname") || key.equals("product_name")
			|| key.equals("goodsnm") || key.equals("goodsname")
			|| key.equals("itemname") || key.equals("item_name");
	}

	private boolean isDescriptionKey(String key) {
		return key.equals("description") || key.equals("summary") || key.equals("content");
	}

	private boolean isPriceKey(String key, boolean productContext) {
		return isStrongPriceKey(key) || (productContext && (key.equals("price") || key.equals("amount")));
	}

	private boolean isStrongPriceKey(String key) {
		return key.equals("saleprice") || key.equals("sale_price")
			|| key.equals("finalprice") || key.equals("final_price") || key.equals("discountedprice")
			|| key.equals("discounted_price") || key.equals("goodsprice") || key.equals("sellprice");
	}

	private boolean isImageKey(String key, boolean productContext) {
		return isStrongImageKey(key) || (productContext && (key.equals("image") || key.equals("thumbnail")));
	}

	private boolean isStrongImageKey(String key) {
		return key.equals("imageurl") || key.equals("image_url")
			|| key.equals("thumbnailurl") || key.equals("thumbnail_url")
			|| key.equals("thumbnailimageurl") || key.equals("thumbnail_image_url")
			|| key.equals("representativeimageurl") || key.equals("representative_image_url")
			|| key.equals("mainimage") || key.equals("main_image");
	}

	private int keyPriority(String key, boolean productContext) {
		if (isStrongProductTitleKey(key) || isStrongPriceKey(key)) {
			return 3;
		}
		if (isStrongImageKey(key)) {
			return productContext ? 3 : 1;
		}
		return productContext ? 2 : 1;
	}

	private String searchJson(JsonNode node, String[] path) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isArray()) {
			for (JsonNode child : node) {
				String value = searchJson(child, path);
				if (!isBlank(value)) {
					return value;
				}
			}
			return null;
		}
		if (path.length == 0) {
			if (node.isValueNode()) {
				return node.asText();
			}
			if (node.has("@value")) {
				return node.get("@value").asText();
			}
			return null;
		}
		if (node.has(path[0])) {
			return searchJson(node.get(path[0]), tail(path));
		}
		Iterator<JsonNode> children = node.elements();
		while (children.hasNext()) {
			String value = searchJson(children.next(), path);
			if (!isBlank(value)) {
				return value;
			}
		}
		return null;
	}

	private String[] tail(String[] path) {
		if (path.length <= 1) {
			return new String[0];
		}
		String[] tail = new String[path.length - 1];
		System.arraycopy(path, 1, tail, 0, path.length - 1);
		return tail;
	}

	private String metaContent(Document document, String cssQuery) {
		String content = document.selectFirst(cssQuery) == null ? null : document.selectFirst(cssQuery).attr("content");
		return normalizeWhitespace(content);
	}

	private String firstText(Document document, String... selectors) {
		for (String selector : selectors) {
			if (document.selectFirst(selector) != null) {
				String text = normalizeWhitespace(document.selectFirst(selector).text());
				if (!isBlank(text)) {
					return text;
				}
			}
		}
		return null;
	}

	private String findByRegex(String text, Pattern pattern) {
		Matcher matcher = pattern.matcher(text);
		return matcher.find() ? matcher.group(1) : null;
	}

	private boolean isBlockedShoppingPage(Document document, FetchedPage page) {
		String title = normalizeWhitespace(document.title());
		String bodyText = normalizeWhitespace(document.body() == null ? null : document.body().text());
		String html = page.body() == null ? "" : page.body().toLowerCase(Locale.ROOT);
		return isChallengeShell(title, bodyText, html)
			|| isBlockedNoticeShell(title, bodyText);
	}

	private boolean isChallengeShell(String title, String bodyText, String html) {
		return hasChallengeMarker(html)
			&& (isBlank(title) || isBlockedPageText(title))
			&& (isBlank(bodyText) || bodyText.length() < 80 || isBlockedPageText(bodyText));
	}

	private boolean isBlockedNoticeShell(String title, String bodyText) {
		if (isBlockedPageText(title)) {
			return isBlank(bodyText) || bodyText.length() < 120 || isBlockedPageText(bodyText);
		}
		return isBlank(title) && isBlockedPageText(bodyText) && bodyText.length() < 120;
	}

	private boolean hasChallengeMarker(String html) {
		return html.contains("cf-mitigated")
			|| html.contains("cf_chl")
			|| html.contains("/cdn-cgi/challenge-platform");
	}

	private boolean isBlockedPageText(String text) {
		if (isBlank(text)) {
			return false;
		}
		String normalized = text.toLowerCase(Locale.ROOT);
		return normalized.contains("잠시만 기다")
			|| normalized.contains("접속 정보를 확인")
			|| normalized.contains("enable javascript and cookies");
	}

	private Integer parsePrice(String rawPrice) {
		if (isBlank(rawPrice)) {
			return null;
		}
		Matcher wonPriceMatcher = WON_PRICE_AMOUNT_PATTERN.matcher(rawPrice);
		Matcher priceMatcher = PRICE_AMOUNT_PATTERN.matcher(rawPrice);
		String amount = wonPriceMatcher.find()
			? wonPriceMatcher.group(1)
			: priceMatcher.find() ? priceMatcher.group(1) : null;
		if (amount == null) {
			return null;
		}
		try {
			BigDecimal price = new BigDecimal(amount.replace(",", "")).stripTrailingZeros();
			if (price.scale() > 0) {
				return null;
			}
			return price.intValueExact();
		} catch (ArithmeticException | NumberFormatException exception) {
			return null;
		}
	}

	private Integer parseListedPrice(String rawPrice) {
		Integer price = parsePrice(rawPrice);
		if (price == null || price <= 0) {
			return null;
		}
		return price;
	}

	private URI parseHttpUri(String rawUrl, String errorMessage) {
		if (isBlank(rawUrl)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
		}

		try {
			URI uri = URI.create(rawUrl.trim());
			String scheme = Optional.ofNullable(uri.getScheme()).orElse("").toLowerCase(Locale.ROOT);
			if (!scheme.equals("http") && !scheme.equals("https")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only http/https urls are supported");
			}
			if (isBlank(uri.getHost())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
			}
			if (!isBlank(uri.getUserInfo())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url must not include user info");
			}
			return uri;
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage, exception);
		}
	}

	private String sourceDomain(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("");
		return host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
	}

	private String firstProductTitle(String sourceDomain, String... values) {
		for (String value : values) {
			String title = cleanProductTitle(sourceDomain, value);
			if (!isBlank(title)) {
				return title;
			}
		}
		return null;
	}

	private String cleanProductTitle(String sourceDomain, String value) {
		String title = normalizeWhitespace(value);
		if (isBlank(title) || isBlockedPageText(title)) {
			return null;
		}
		if (isOliveYoungDomain(sourceDomain)) {
			title = normalizeWhitespace(title
				.replaceFirst("\\s*/\\s*올리브영$", "")
				.replaceFirst("\\s*\\|\\s*올리브영$", ""));
			if (isOliveYoungSiteTitle(title)) {
				return null;
			}
		}
		if (isShoppingBridgeOrSiteTitle(sourceDomain, title)) {
			return null;
		}
		return title;
	}

	private boolean isOliveYoungDomain(String sourceDomain) {
		return OLIVE_YOUNG_HOSTS.contains(sourceDomain);
	}

	private boolean isOliveYoungSiteTitle(String title) {
		if (isBlank(title)) {
			return true;
		}
		String normalized = title.replace("[", "")
			.replace("]", "")
			.replace("/", "")
			.trim()
			.toLowerCase(Locale.ROOT);
		return normalized.equals("올리브영") || normalized.equals("oliveyoung");
	}

	private boolean isShoppingBridgeOrSiteTitle(String sourceDomain, String title) {
		if (isBlank(sourceDomain) || isBlank(title)) {
			return false;
		}
		String normalizedTitle = title.replace("+", "")
			.replace(" ", "")
			.trim()
			.toLowerCase(Locale.ROOT);
		return switch (sourceDomain) {
			case "zigzag.kr", "s.zigzag.kr", "link.zigzag.kr", "zigzag.airbridge.io" ->
				normalizedTitle.equals("지그재그") || normalizedTitle.equals("zigzag") || normalizedTitle.equals("지그재그스토어");
			case "bunjang.co.kr", "m.bunjang.co.kr", "bunjang.airbridge.io", "go.bgzt.link",
				"link.bunjang.co.kr", "share.bunjang.co.kr" ->
				normalizedTitle.equals("번개장터") || normalizedTitle.equals("bunjang");
			case "app.shopping.naver.com", "naver.me" ->
				normalizedTitle.equals("네이버스토어") || normalizedTitle.equals("네이버플러스스토어");
			case "oy.run" ->
				normalizedTitle.equals("올리브영") || normalizedTitle.equals("oliveyoung");
			default -> false;
		};
	}

	private boolean containsAny(String value, String... keywords) {
		for (String keyword : keywords) {
			if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private String normalizeImageUrl(String imageUrl) {
		if (isBlank(imageUrl)) {
			return null;
		}
		try {
			String decodedImageUrl = normalizeStructuredDataText(imageUrl);
			if (isBlank(decodedImageUrl)) {
				return null;
			}
			URI uri = URI.create(decodedImageUrl);
			String scheme = Optional.ofNullable(uri.getScheme()).orElse("").toLowerCase(Locale.ROOT);
			if ((!scheme.equals("http") && !scheme.equals("https")) || isBlank(uri.getHost())) {
				return null;
			}
			return uri.toString();
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private String normalizeWhitespace(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.replaceAll("\\s+", " ").trim();
		return normalized.isBlank() ? null : normalized;
	}

	private String normalizeStructuredDataText(String value) {
		if (value == null) {
			return null;
		}
		String decoded = Parser.unescapeEntities(value, false)
			.replace("<![CDATA[", "")
			.replace("]]>", "");
		return normalizeWhitespace(decoded);
	}

	private boolean isPlaceholderMetadataText(String value) {
		if (isBlank(value)) {
			return true;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return normalized.equals("null") || normalized.equals("undefined") || normalized.equals("none");
	}

	private String blankToNull(String value) {
		return isBlank(value) ? null : value.trim();
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private URI rebuildUri(URI uri, String rawQuery) {
		try {
			return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), rawQuery, null);
		} catch (URISyntaxException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid normalized url", exception);
		}
	}

	private String removeTrackingQueryParameters(String rawQuery, String host) {
		String filtered = Arrays.stream(rawQuery.split("&"))
			.filter(parameter -> !isTrackingQueryParameter(parameter, host))
			.collect(Collectors.joining("&"));
		return filtered.isBlank() ? null : filtered;
	}

	private boolean isTrackingQueryParameter(String parameter, String host) {
		String key = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
		String normalizedHost = host == null ? "" : host.toLowerCase(Locale.ROOT);
		boolean knownAppShareHost = normalizedHost.endsWith("musinsa.com") || normalizedHost.endsWith("29cm.co.kr");
		return key.startsWith("utm_") || TRACKING_QUERY_KEYS.contains(key)
			|| (knownAppShareHost && APP_SHARE_TRACKING_QUERY_KEYS.contains(key));
	}

	private String toJson(Map<String, Object> rawPayloadJson) {
		try {
			return objectMapper.writeValueAsString(rawPayloadJson);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to serialize import metadata", exception);
		}
	}

	private WishlistSaveDraft toWishlistSaveDraft(SavedItemDraft item, ItemSourceMetadataDraft metadata) {
		return new WishlistSaveDraft(
			item.inputSource(),
			item.originalUrl(),
			item.normalizedUrl(),
			item.title(),
			item.imageUrl(),
			item.listedPrice(),
			item.currencyCode(),
			item.category(),
			item.categoryConfidence(),
			item.categoryLockedByUser(),
			metadata.sourceDomain(),
			metadata.rawTitle(),
			metadata.rawDescription(),
			metadata.rawPriceText(),
			metadata.rawPayloadJson()
		);
	}

	@SafeVarargs
	private <T> T firstNonNull(T... values) {
		for (T value : values) {
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (!isBlank(value)) {
				return normalizeWhitespace(value);
			}
		}
		return null;
	}

	private String firstValidPriceText(String... values) {
		for (String value : values) {
			if (parseListedPrice(value) != null) {
				return normalizeWhitespace(value);
			}
		}
		return null;
	}

	private record NormalizationResult(URI uri, List<String> warnings) {
	}

	private record EmbeddedMetadata(
		String title,
		int titlePriority,
		String description,
		int descriptionPriority,
		String priceText,
		int pricePriority,
		String imageUrl,
		int imagePriority
	) {
		private static EmbeddedMetadata empty() {
			return new EmbeddedMetadata(null, 0, null, 0, null, 0, null, 0);
		}

		private EmbeddedMetadata merge(EmbeddedMetadata other) {
			return new EmbeddedMetadata(
				bestValue(title, titlePriority, other.title, other.titlePriority),
				bestPriority(title, titlePriority, other.title, other.titlePriority),
				bestValue(description, descriptionPriority, other.description, other.descriptionPriority),
				bestPriority(description, descriptionPriority, other.description, other.descriptionPriority),
				bestValue(priceText, pricePriority, other.priceText, other.pricePriority),
				bestPriority(priceText, pricePriority, other.priceText, other.pricePriority),
				bestValue(imageUrl, imagePriority, other.imageUrl, other.imagePriority),
				bestPriority(imageUrl, imagePriority, other.imageUrl, other.imagePriority)
			);
		}

		private EmbeddedMetadata withTitle(String value, int priority) {
			return new EmbeddedMetadata(
				bestValue(title, titlePriority, value, priority),
				bestPriority(title, titlePriority, value, priority),
				description,
				descriptionPriority,
				priceText,
				pricePriority,
				imageUrl,
				imagePriority
			);
		}

		private EmbeddedMetadata withDescription(String value, int priority) {
			return new EmbeddedMetadata(
				title,
				titlePriority,
				bestValue(description, descriptionPriority, value, priority),
				bestPriority(description, descriptionPriority, value, priority),
				priceText,
				pricePriority,
				imageUrl,
				imagePriority
			);
		}

		private EmbeddedMetadata withPriceText(String value, int priority) {
			return new EmbeddedMetadata(
				title,
				titlePriority,
				description,
				descriptionPriority,
				bestValue(priceText, pricePriority, value, priority),
				bestPriority(priceText, pricePriority, value, priority),
				imageUrl,
				imagePriority
			);
		}

		private EmbeddedMetadata withImageUrl(String value, int priority) {
			return new EmbeddedMetadata(
				title,
				titlePriority,
				description,
				descriptionPriority,
				priceText,
				pricePriority,
				bestValue(imageUrl, imagePriority, value, priority),
				bestPriority(imageUrl, imagePriority, value, priority)
			);
		}

		private boolean hasAnyValue() {
			return title != null || description != null || priceText != null || imageUrl != null;
		}

		private boolean hasAllProductValues() {
			return title != null && priceText != null && imageUrl != null;
		}

		private static String bestValue(String current, int currentPriority, String next, int nextPriority) {
			if (next == null) {
				return current;
			}
			if (current == null || nextPriority > currentPriority) {
				return next;
			}
			return current;
		}

		private static int bestPriority(String current, int currentPriority, String next, int nextPriority) {
			if (next == null) {
				return currentPriority;
			}
			if (current == null || nextPriority > currentPriority) {
				return nextPriority;
			}
			return currentPriority;
		}
	}

	private record ZigzagMetadata(String title, String priceText, String imageUrl) {

		private static ZigzagMetadata empty() {
			return new ZigzagMetadata(null, null, null);
		}

		private boolean hasAnyValue() {
			return title != null || priceText != null || imageUrl != null;
		}
	}

	private record ExtractionResult(
		String title,
		String brandName,
		String summary,
		Integer price,
		String rawPriceText,
		String currencyCode,
		String imageUrl,
		String method,
		Map<String, Object> rawPayloadJson
	) {
		private String rawTitle() {
			return title;
		}

		private String rawDescription() {
			return summary;
		}

		private boolean isPartial() {
			return isBlank(title) || price == null || price <= 0 || isBlank(currencyCode) || isBlank(imageUrl);
		}

		private boolean isBlank(String value) {
			return value == null || value.isBlank();
		}
	}
}
