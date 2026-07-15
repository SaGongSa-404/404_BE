package com.sagongsa.backend.itemimport.job;

import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShoppingImportCrawlIdentity {

	private static final Pattern PRODUCTS_PATH = Pattern.compile("^/products/([0-9]+)(?:/|$)");
	private static final Pattern GOODS_PATH = Pattern.compile("^/goods/([0-9]+)(?:/|$)");
	private static final Pattern ZIGZAG_CATALOG_PATH = Pattern.compile("^/catalog/products/([0-9]+)(?:/|$)");
	private static final Pattern OLIVE_YOUNG_PRODUCT_ID = Pattern.compile("^[A-Z][0-9]+$");

	private ShoppingImportCrawlIdentity() {
	}

	static Optional<Identity> from(ShoppingLinkImportRequest request) {
		if (request == null || request.inputSource() != ItemInputSource.SHARE
			|| request.url() == null || request.url().isBlank()) {
			return Optional.empty();
		}

		String originalUrl = request.url().trim();
		try {
			URI uri = URI.create(originalUrl);
			String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
			String path = Optional.ofNullable(uri.getPath()).orElse("");
			String site = site(host);
			String productId = productId(site, uri, path);
			String material = productId == null
				? "url:" + originalUrl
				: "product:" + site + ":" + productId;
			return Optional.of(new Identity(sha256(material), site));
		} catch (IllegalArgumentException exception) {
			return Optional.of(new Identity(sha256("url:" + originalUrl), "other"));
		}
	}

	private static String productId(String site, URI uri, String path) {
		return switch (site) {
			case "oliveyoung" -> matchingValue(
				queryParameter(uri.getRawQuery(), "goodsNo"),
				OLIVE_YOUNG_PRODUCT_ID
			);
			case "musinsa", "29cm", "kream", "bunjang" -> pathValue(PRODUCTS_PATH, path);
			case "ably" -> pathValue(GOODS_PATH, path);
			case "zigzag" -> pathValue(ZIGZAG_CATALOG_PATH, path);
			default -> null;
		};
	}

	private static String site(String host) {
		if (matchesHost(host, "oliveyoung.co.kr")) {
			return "oliveyoung";
		}
		if (matchesHost(host, "musinsa.com")) {
			return "musinsa";
		}
		if (matchesHost(host, "29cm.co.kr")) {
			return "29cm";
		}
		if (matchesHost(host, "a-bly.com")) {
			return "ably";
		}
		if (matchesHost(host, "kream.co.kr")) {
			return "kream";
		}
		if (matchesHost(host, "bunjang.co.kr")) {
			return "bunjang";
		}
		if (matchesHost(host, "zigzag.kr")) {
			return "zigzag";
		}
		if (matchesHost(host, "daangn.com")) {
			return "daangn";
		}
		if (matchesHost(host, "naver.com")) {
			return "naver";
		}
		return "other";
	}

	private static boolean matchesHost(String host, String root) {
		return host.equals(root) || host.endsWith("." + root);
	}

	private static String pathValue(Pattern pattern, String path) {
		Matcher matcher = pattern.matcher(path);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static String matchingValue(String value, Pattern pattern) {
		return value != null && pattern.matcher(value).matches() ? value : null;
	}

	private static String queryParameter(String rawQuery, String expectedName) {
		if (rawQuery == null || rawQuery.isBlank()) {
			return null;
		}
		for (String pair : rawQuery.split("&")) {
			int separator = pair.indexOf('=');
			String rawName = separator < 0 ? pair : pair.substring(0, separator);
			if (!URLDecoder.decode(rawName, StandardCharsets.UTF_8).equals(expectedName)) {
				continue;
			}
			String rawValue = separator < 0 ? "" : pair.substring(separator + 1);
			String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8).trim();
			return value.isEmpty() ? null : value;
		}
		return null;
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	record Identity(String key, String site) {
	}
}
