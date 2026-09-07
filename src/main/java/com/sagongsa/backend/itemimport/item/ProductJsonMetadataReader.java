package com.sagongsa.backend.itemimport.item;

import static com.sagongsa.backend.itemimport.item.ShoppingExtractionData.*;
import static com.sagongsa.backend.itemimport.item.ShoppingUrlNormalizer.*;
import static com.sagongsa.backend.itemimport.item.ShoppingProductPolicy.*;
import static com.sagongsa.backend.itemimport.item.ShoppingMetadataText.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

final class ProductJsonMetadataReader {
	private static final Set<String> ZIGZAG_PRODUCT_STATE_HOSTS = Set.of(
		"zigzag.kr", "www.zigzag.kr", "store.zigzag.kr"
	);
	private final ObjectMapper objectMapper;
	ProductJsonMetadataReader(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

	ZigzagMetadata zigzagMetadata(Document document, URI finalUri) {
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

	JsonNode findZigzagProduct(JsonNode node, String productId) {
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

	String textAt(JsonNode node, String... path) {
		JsonNode current = node;
		for (String segment : path) {
			current = current.path(segment);
			if (current.isMissingNode() || current.isNull()) {
				return null;
			}
		}
		return current.isValueNode() ? normalizeWhitespace(current.asText()) : null;
	}

	String firstZigzagProductImage(JsonNode images) {
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

	String jsonLdText(Document document, String path) {
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

	String productJsonLdText(Document document, String path) {
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

	JsonNode findJsonLdProduct(JsonNode node) {
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

	boolean isJsonLdProductType(JsonNode typeNode) {
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

	String jsonValue(String rawJson, String path) {
		try {
			JsonNode root = objectMapper.readTree(rawJson);
			return searchJson(root, path.split("\\."));
		} catch (JsonProcessingException exception) {
			return null;
		}
	}

	EmbeddedMetadata embeddedMetadata(Document document) {
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

	EmbeddedMetadata embeddedMetadata(JsonNode node) {
		return embeddedMetadata(node, false);
	}

	EmbeddedMetadata embeddedMetadata(JsonNode node, boolean productContext) {
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

	String jsonCandidate(String rawScript) {
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

	boolean isProductContainerKey(String key) {
		return key.contains("product") || key.contains("goods") || key.equals("item");
	}

	boolean isProductTitleKey(String key, boolean productContext) {
		return isStrongProductTitleKey(key) || (productContext && (key.equals("name") || key.equals("title")));
	}

	boolean isStrongProductTitleKey(String key) {
		return key.equals("productname") || key.equals("product_name")
			|| key.equals("goodsnm") || key.equals("goodsname")
			|| key.equals("itemname") || key.equals("item_name");
	}

	boolean isDescriptionKey(String key) {
		return key.equals("description") || key.equals("summary") || key.equals("content");
	}

	boolean isPriceKey(String key, boolean productContext) {
		return isStrongPriceKey(key) || (productContext && (key.equals("price") || key.equals("amount")));
	}

	boolean isStrongPriceKey(String key) {
		return key.equals("saleprice") || key.equals("sale_price")
			|| key.equals("finalprice") || key.equals("final_price") || key.equals("discountedprice")
			|| key.equals("discounted_price") || key.equals("goodsprice") || key.equals("sellprice");
	}

	boolean isImageKey(String key, boolean productContext) {
		return isStrongImageKey(key) || (productContext && (key.equals("image") || key.equals("thumbnail")));
	}

	boolean isStrongImageKey(String key) {
		return key.equals("imageurl") || key.equals("image_url")
			|| key.equals("thumbnailurl") || key.equals("thumbnail_url")
			|| key.equals("thumbnailimageurl") || key.equals("thumbnail_image_url")
			|| key.equals("representativeimageurl") || key.equals("representative_image_url")
			|| key.equals("mainimage") || key.equals("main_image");
	}

	int keyPriority(String key, boolean productContext) {
		if (isStrongProductTitleKey(key) || isStrongPriceKey(key)) {
			return 3;
		}
		if (isStrongImageKey(key)) {
			return productContext ? 3 : 1;
		}
		return productContext ? 2 : 1;
	}

	String searchJson(JsonNode node, String[] path) {
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

	String[] tail(String[] path) {
		if (path.length <= 1) {
			return new String[0];
		}
		String[] tail = new String[path.length - 1];
		System.arraycopy(path, 1, tail, 0, path.length - 1);
		return tail;
	}
}
