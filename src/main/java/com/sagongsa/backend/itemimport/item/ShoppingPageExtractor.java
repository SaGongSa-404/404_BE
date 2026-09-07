package com.sagongsa.backend.itemimport.item;

import static com.sagongsa.backend.itemimport.item.ShoppingExtractionData.*;
import static com.sagongsa.backend.itemimport.item.ShoppingUrlNormalizer.*;
import static com.sagongsa.backend.itemimport.item.ShoppingProductPolicy.*;
import static com.sagongsa.backend.itemimport.item.ShoppingMetadataText.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;

final class ShoppingPageExtractor {
	private static final Pattern PRICE_WITH_CURRENCY_PATTERN = Pattern.compile("\\b([0-9]{1,3}(?:,[0-9]{3})+)\\s*원");
	private static final Pattern MUSINSA_FINAL_PRICE_PATTERN = Pattern.compile(
		"(?s)\\\"goodsPrice\\\"\\s*:\\s*\\{.{0,2000}?\\\"finalPrice\\\"\\s*:\\s*([0-9]+)"
	);
	private static final Pattern TWENTY_NINE_CM_DISPLAYED_PRICE_PATTERN = Pattern.compile(
		"(?s)\"id\"\\s*:\\s*\"pdp_product_price\".{0,500}?\"children\"\\s*:\\s*\\[\"([0-9][0-9,]*)\""
	);
	private static final Set<String> NOISE_IMAGE_KEYWORDS = Set.of("logo", "icon", "sprite", "badge", "banner");
	private final ProductJsonMetadataReader jsonReader;
	ShoppingPageExtractor(ObjectMapper objectMapper) { this.jsonReader = new ProductJsonMetadataReader(objectMapper); }

	ExtractionResult extractFromPage(Document document, FetchedPage page) {
		String html = page.body();
		String sourceDomain = sourceDomain(page.finalUri());
		ZigzagMetadata zigzagMetadata = jsonReader.zigzagMetadata(document, page.finalUri());
		EmbeddedMetadata embeddedMetadata = jsonReader.embeddedMetadata(document);
		String productJsonLdTitle = jsonReader.productJsonLdText(document, "name");
		String productJsonLdDescription = jsonReader.productJsonLdText(document, "description");
		String productJsonLdPrice = jsonReader.productJsonLdText(document, "offers.price");
		String productJsonLdCurrency = jsonReader.productJsonLdText(document, "offers.priceCurrency");
		String productJsonLdImage = jsonReader.productJsonLdText(document, "image");
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
			jsonReader.jsonLdText(document, "description")
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
			jsonReader.productJsonLdText(document, "brand.name"),
			jsonReader.productJsonLdText(document, "brand"),
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
			parseListedPrice(jsonReader.jsonLdText(document, "price")),
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
			jsonReader.jsonLdText(document, "price"),
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

	String twentyNineCmDisplayedPrice(Document document, String html, String sourceDomain) {
		if (!"29cm.co.kr".equals(sourceDomain) && !"product.29cm.co.kr".equals(sourceDomain)) {
			return null;
		}
		String renderedPrice = firstText(document, "#pdp_product_price");
		if (!isBlank(renderedPrice)) {
			return renderedPrice;
		}
		return findByRegex(html.replace("\\\"", "\""), TWENTY_NINE_CM_DISPLAYED_PRICE_PATTERN);
	}

	String bestImage(Document document) {
		return document.select("img[src], img[data-src], img[srcset]").stream()
			.map(element -> firstNonBlank(element.absUrl("src"), element.absUrl("data-src"), firstSrcSetUrl(element.attr("srcset"))))
			.map(ShoppingMetadataText::normalizeImageUrl)
			.filter(Objects::nonNull)
			.filter(url -> NOISE_IMAGE_KEYWORDS.stream().noneMatch(url.toLowerCase(Locale.ROOT)::contains))
			.findFirst()
			.orElse(null);
	}

	String firstSrcSetUrl(String srcSet) {
		if (isBlank(srcSet)) {
			return null;
		}
		String firstCandidate = srcSet.split(",")[0].trim();
		return firstCandidate.split("\\s+")[0];
	}
}
