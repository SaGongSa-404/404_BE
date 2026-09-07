package com.sagongsa.backend.itemimport.item;

import static com.sagongsa.backend.itemimport.item.ShoppingExtractionData.*;
import static com.sagongsa.backend.itemimport.item.ShoppingUrlNormalizer.*;
import static com.sagongsa.backend.itemimport.item.ShoppingMetadataText.*;

import com.sagongsa.backend.domain.enums.ItemCategory;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ShoppingProductPolicy {
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
	static boolean isAblyDomain(String sourceDomain) {
		return "m.a-bly.com".equals(sourceDomain);
	}

	static boolean isMusinsaDomain(String sourceDomain) {
		return sourceDomain != null && (sourceDomain.equals("musinsa.com") || sourceDomain.equals("www.musinsa.com"));
	}

	static void validateVerifiedProduct(URI requestedUri, URI finalUri, ExtractionResult extracted) {
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

	static boolean isVerifiedProductHost(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		return VERIFIED_PRODUCT_HOSTS.contains(host);
	}

	static boolean isVerifiedProductUri(URI uri) {
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

	static String resolveCurrencyCode(String sourceDomain, String... candidates) {
		for (String candidate : candidates) {
			String normalized = normalizeWhitespace(candidate);
			if (normalized != null && normalized.matches("(?i)[A-Z]{3}")) {
				return normalized.toUpperCase(Locale.ROOT);
			}
		}
		return "KRW";
	}

	static ItemCategory classifyCategory(String sourceDomain, String title, String summary) {
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

	static boolean isBlockedShoppingPage(Document document, FetchedPage page) {
		String title = normalizeWhitespace(document.title());
		String bodyText = normalizeWhitespace(document.body() == null ? null : document.body().text());
		String html = page.body() == null ? "" : page.body().toLowerCase(Locale.ROOT);
		return isChallengeShell(title, bodyText, html)
			|| isBlockedNoticeShell(title, bodyText);
	}

	static boolean isChallengeShell(String title, String bodyText, String html) {
		return hasChallengeMarker(html)
			&& (isBlank(title) || isBlockedPageText(title))
			&& (isBlank(bodyText) || bodyText.length() < 80 || isBlockedPageText(bodyText));
	}

	static boolean isBlockedNoticeShell(String title, String bodyText) {
		if (isBlockedPageText(title)) {
			return isBlank(bodyText) || bodyText.length() < 120 || isBlockedPageText(bodyText);
		}
		return isBlank(title) && isBlockedPageText(bodyText) && bodyText.length() < 120;
	}

	static boolean hasChallengeMarker(String html) {
		return html.contains("cf-mitigated")
			|| html.contains("cf_chl")
			|| html.contains("/cdn-cgi/challenge-platform");
	}

	static boolean isBlockedPageText(String text) {
		if (isBlank(text)) {
			return false;
		}
		String normalized = text.toLowerCase(Locale.ROOT);
		return normalized.contains("잠시만 기다")
			|| normalized.contains("접속 정보를 확인")
			|| normalized.contains("enable javascript and cookies");
	}

	static String firstProductTitle(String sourceDomain, String... values) {
		for (String value : values) {
			String title = cleanProductTitle(sourceDomain, value);
			if (!isBlank(title)) {
				return title;
			}
		}
		return null;
	}

	static String cleanProductTitle(String sourceDomain, String value) {
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

	static boolean isOliveYoungDomain(String sourceDomain) {
		return OLIVE_YOUNG_HOSTS.contains(sourceDomain);
	}

	static boolean isOliveYoungSiteTitle(String title) {
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

	static boolean isShoppingBridgeOrSiteTitle(String sourceDomain, String title) {
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
}
