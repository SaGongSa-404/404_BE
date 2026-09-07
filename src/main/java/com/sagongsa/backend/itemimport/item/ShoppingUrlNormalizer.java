package com.sagongsa.backend.itemimport.item;

import static com.sagongsa.backend.itemimport.item.ShoppingExtractionData.*;
import static com.sagongsa.backend.itemimport.item.ShoppingProductPolicy.*;
import static com.sagongsa.backend.itemimport.item.ShoppingMetadataText.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ShoppingUrlNormalizer {
	private static final Pattern ABLY_GOODS_PATH_PATTERN = Pattern.compile(
		"(?:^|/)goods/([0-9]+)(?![0-9])"
	);
	private static final Set<String> TRACKING_QUERY_KEYS = Set.of(
		"fbclid", "gclid", "igshid", "mc_cid", "mc_eid", "n_media", "n_query", "n_rank", "n_ad_group"
	);
	private static final Set<String> APP_SHARE_TRACKING_QUERY_KEYS = Set.of(
		"source_caller", "pid", "shortlink", "short_id", "is_retargeting", "referrer",
		"af_siteid", "af_dp", "af_referrer_uid", "af_channel", "af_force_deeplink", "af_click_lookback",
		"deep_link_value", "tracking_content", "airbridge_referrer", "https_deeplink"
	);
	private static final int MAX_NESTED_URL_DECODE_COUNT = 6;
	static NormalizationResult normalizeShoppingUri(URI uri) {
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

	static Optional<String> extractAblyShareProductId(URI uri) {
		Optional<String> pathProductId = extractAblyProductIdFromEncodedValue(uri.getRawPath());
		if (pathProductId.isPresent()) {
			return pathProductId;
		}
		return extractAblyProductIdFromEncodedValue(uri.getRawQuery());
	}

	static Optional<String> extractAblyProductIdFromEncodedValue(String rawValue) {
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

	static URI rebuildUriWithHost(URI uri, String host) {
		try {
			return new URI("https", null, host, -1, uri.getPath(), uri.getRawQuery(), null);
		} catch (java.net.URISyntaxException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shopping url", exception);
		}
	}

	static URI rebuildUri(URI uri, String rawQuery) {
		try {
			return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), rawQuery, null);
		} catch (URISyntaxException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid normalized url", exception);
		}
	}

	static String removeTrackingQueryParameters(String rawQuery, String host) {
		String filtered = Arrays.stream(rawQuery.split("&"))
			.filter(parameter -> !isTrackingQueryParameter(parameter, host))
			.collect(Collectors.joining("&"));
		return filtered.isBlank() ? null : filtered;
	}

	static boolean isTrackingQueryParameter(String parameter, String host) {
		String key = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
		String normalizedHost = host == null ? "" : host.toLowerCase(Locale.ROOT);
		boolean knownAppShareHost = normalizedHost.endsWith("musinsa.com") || normalizedHost.endsWith("29cm.co.kr");
		return key.startsWith("utm_") || TRACKING_QUERY_KEYS.contains(key)
			|| (knownAppShareHost && APP_SHARE_TRACKING_QUERY_KEYS.contains(key));
	}
}
