package com.sagongsa.backend.itemimport.item;

import static com.sagongsa.backend.itemimport.item.ShoppingMetadataText.isBlank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;

final class ProductApiMetadataHtml {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private ProductApiMetadataHtml() {
	}

	static Optional<String> fromAbly(String apiBody) {
		if (apiBody == null || apiBody.isBlank()) {
			return Optional.empty();
		}
		try {
			JsonNode goods = OBJECT_MAPPER.readTree(apiBody).path("goods");
			String title = jsonText(goods, "name");
			String price = jsonText(goods, "price_info", "thumbnail_price");
			String imageUrl = firstJsonArrayText(goods.path("cover_images"));
			String brandName = jsonText(goods, "market", "name");
			if (isBlank(title) || isBlank(price) || isBlank(imageUrl) || "0".equals(price)) {
				return Optional.empty();
			}

			StringBuilder html = new StringBuilder("<!doctype html><html><head>");
			html.append("<title>").append(escapeHtml(title)).append("</title>");
			appendPropertyMeta(html, "og:title", title);
			appendPropertyMeta(html, "og:image", imageUrl);
			appendPropertyMeta(html, "product:price:amount", price);
			appendPropertyMeta(html, "product:price:currency", "KRW");
			appendPropertyMeta(html, "kakao:commerce:brand_name", brandName);
			html.append("</head><body></body></html>");
			return Optional.of(html.toString());
		} catch (JsonProcessingException exception) {
			return Optional.empty();
		}
	}

	static Optional<String> fromKream(String apiBody) {
		if (apiBody == null || apiBody.isBlank()) {
			return Optional.empty();
		}
		try {
			JsonNode root = OBJECT_MAPPER.readTree(apiBody);
			JsonNode release = root.path("release");
			String title = firstNonBlank(jsonText(release, "translated_name"), jsonText(release, "name"));
			String price = jsonText(root, "market", "lowest_ask");
			String imageUrl = firstJsonArrayText(release.path("image_urls"));
			String brandName = firstNonBlank(
				jsonText(release, "brand", "translated_name"),
				jsonText(release, "brand", "name")
			);
			if (isBlank(title) && isBlank(price) && isBlank(imageUrl)) {
				return Optional.empty();
			}

			StringBuilder html = new StringBuilder("<!doctype html><html><head>");
			html.append("<title>").append(escapeHtml(firstNonBlank(title, "KREAM 상품"))).append("</title>");
			appendPropertyMeta(html, "og:title", title);
			appendPropertyMeta(html, "og:image", imageUrl);
			appendPropertyMeta(html, "product:price:amount", price);
			appendPropertyMeta(html, "kakao:commerce:brand_name", brandName);
			html.append("</head><body></body></html>");
			return Optional.of(html.toString());
		} catch (JsonProcessingException exception) {
			return Optional.empty();
		}
	}

	private static String firstJsonArrayText(JsonNode node) {
		if (!node.isArray()) {
			return null;
		}
		for (JsonNode value : node) {
			String text = value.asText();
			if (!isBlank(text)) {
				return text.trim();
			}
		}
		return null;
	}

	static Optional<String> fromBunjang(String apiBody) {
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

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (!isBlank(value)) {
				return value.trim();
			}
		}
		return null;
	}

	private static String escapeHtml(String value) {
		return value.replace("&", "&amp;")
			.replace("\"", "&quot;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}
}
