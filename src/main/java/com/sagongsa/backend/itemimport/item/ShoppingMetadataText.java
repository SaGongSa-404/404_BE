package com.sagongsa.backend.itemimport.item;

import static com.sagongsa.backend.itemimport.item.ShoppingExtractionData.*;
import static com.sagongsa.backend.itemimport.item.ShoppingUrlNormalizer.*;
import static com.sagongsa.backend.itemimport.item.ShoppingProductPolicy.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ShoppingMetadataText {
	private static final Pattern WON_PRICE_AMOUNT_PATTERN = Pattern.compile("([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)\\s*원");
	private static final Pattern PRICE_AMOUNT_PATTERN = Pattern.compile("([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)");
	static boolean isPositivePrice(String value) {
		Integer parsed = parseListedPrice(value);
		return parsed != null && parsed > 0;
	}

	static String metaContent(Document document, String cssQuery) {
		String content = document.selectFirst(cssQuery) == null ? null : document.selectFirst(cssQuery).attr("content");
		return normalizeWhitespace(content);
	}

	static String firstText(Document document, String... selectors) {
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

	static String findByRegex(String text, Pattern pattern) {
		Matcher matcher = pattern.matcher(text);
		return matcher.find() ? matcher.group(1) : null;
	}

	static Integer parsePrice(String rawPrice) {
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

	static Integer parseListedPrice(String rawPrice) {
		Integer price = parsePrice(rawPrice);
		if (price == null || price <= 0) {
			return null;
		}
		return price;
	}

	static URI parseHttpUri(String rawUrl, String errorMessage) {
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

	static String sourceDomain(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("");
		return host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
	}

	static boolean containsAny(String value, String... keywords) {
		for (String keyword : keywords) {
			if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	static String normalizeImageUrl(String imageUrl) {
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

	static String normalizeWhitespace(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.replaceAll("\\s+", " ").trim();
		return normalized.isBlank() ? null : normalized;
	}

	static String normalizeStructuredDataText(String value) {
		if (value == null) {
			return null;
		}
		String decoded = Parser.unescapeEntities(value, false)
			.replace("<![CDATA[", "")
			.replace("]]>", "");
		return normalizeWhitespace(decoded);
	}

	static boolean isPlaceholderMetadataText(String value) {
		if (isBlank(value)) {
			return true;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return normalized.equals("null") || normalized.equals("undefined") || normalized.equals("none");
	}

	static String blankToNull(String value) {
		return isBlank(value) ? null : value.trim();
	}

	static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	@SafeVarargs
	static <T> T firstNonNull(T... values) {
		for (T value : values) {
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	static String firstNonBlank(String... values) {
		for (String value : values) {
			if (!isBlank(value)) {
				return normalizeWhitespace(value);
			}
		}
		return null;
	}

	static String firstValidPriceText(String... values) {
		for (String value : values) {
			if (parseListedPrice(value) != null) {
				return normalizeWhitespace(value);
			}
		}
		return null;
	}
}
