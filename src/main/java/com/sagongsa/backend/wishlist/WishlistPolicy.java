package com.sagongsa.backend.wishlist;

import com.sagongsa.backend.domain.enums.ItemInputSource;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

final class WishlistPolicy {
	static final BigDecimal MIN_CONFIDENCE = BigDecimal.ZERO;
	static final BigDecimal MAX_CONFIDENCE = BigDecimal.valueOf(100);
	static final int DEFAULT_LIST_LIMIT = 20;
	static final int MAX_LIST_LIMIT = 50;
	static final int MAX_IDEMPOTENCY_KEY_LENGTH = 120;
	static final Set<String> TRACKING_QUERY_KEYS = Set.of(
		"fbclid", "gclid", "igshid", "mc_cid", "mc_eid", "n_media", "n_query", "n_rank", "n_ad_group"
	);

	static <T extends Enum<T>> T parseRequiredEnum(String value, Class<T> enumType, String fieldName) {
		String cleaned = cleanRequired(value, fieldName, 80).toUpperCase(Locale.ROOT);
		try {
			return Enum.valueOf(enumType, cleaned);
		}
		catch (IllegalArgumentException exception) {
			throw new BadRequestException(fieldName + " has an unsupported value.");
		}
	}

	static String cleanRequired(String value, String fieldName, int maxLength) {
		String cleaned = cleanOptional(value, fieldName);
		if (cleaned == null) {
			throw new BadRequestException(fieldName + " is required.");
		}
		if (cleaned.length() > maxLength) {
			throw new BadRequestException(fieldName + " must be " + maxLength + " characters or fewer.");
		}
		return cleaned;
	}

	static String cleanOptional(String value, String fieldName) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	static String cleanOptional(String value, String fieldName, int maxLength) {
		String cleaned = cleanOptional(value, fieldName);
		if (cleaned != null && cleaned.length() > maxLength) {
			throw new BadRequestException(fieldName + " must be " + maxLength + " characters or fewer.");
		}
		return cleaned;
	}

	static Integer validateListedPrice(Integer listedPrice) {
		if (listedPrice != null && listedPrice <= 0) {
			throw new BadRequestException("listedPrice must be greater than zero.");
		}
		return listedPrice;
	}

	static String cleanCurrencyCode(String currencyCode) {
		String cleaned = cleanOptional(currencyCode, "currencyCode");
		if (cleaned == null) {
			return null;
		}

		cleaned = cleaned.toUpperCase(Locale.ROOT);
		if (cleaned.length() != 3) {
			throw new BadRequestException("currencyCode must be 3 characters.");
		}
		return cleaned;
	}

	static String normalizeSavedUrl(ItemInputSource inputSource, String originalUrl, String normalizedUrl) {
		if (!StringUtils.hasText(originalUrl) && !StringUtils.hasText(normalizedUrl)) {
			if (inputSource == ItemInputSource.DIRECT_INPUT) {
				return null;
			}
			throw new BadRequestException("originalUrl or normalizedUrl is required.");
		}
		if (StringUtils.hasText(originalUrl)) {
			parseHttpUrl(originalUrl, "originalUrl");
		}
		return canonicalizeHttpUrl(StringUtils.hasText(normalizedUrl) ? normalizedUrl : originalUrl, "normalizedUrl");
	}

	static URI parseHttpUrl(String rawUrl, String fieldName) {
		try {
			URI uri = URI.create(rawUrl.trim());
			String scheme = Optional.ofNullable(uri.getScheme()).orElse("").toLowerCase(Locale.ROOT);
			if (!Objects.equals(scheme, "http") && !Objects.equals(scheme, "https")) {
				throw new BadRequestException(fieldName + " must use http or https.");
			}
			if (!StringUtils.hasText(uri.getHost())) {
				throw new BadRequestException(fieldName + " host is required.");
			}
			if (StringUtils.hasText(uri.getUserInfo())) {
				throw new BadRequestException(fieldName + " must not include user info.");
			}
			return uri;
		} catch (IllegalArgumentException exception) {
			throw new BadRequestException(fieldName + " must be a valid URL.");
		}
	}

	static int normalizeLimit(Integer requestedLimit) {
		if (requestedLimit == null) {
			return DEFAULT_LIST_LIMIT;
		}
		if (requestedLimit < 1 || requestedLimit > MAX_LIST_LIMIT) {
			throw new BadRequestException("limit must be between 1 and " + MAX_LIST_LIMIT + ".");
		}
		return requestedLimit;
	}

	static String canonicalizeHttpUrl(String rawUrl, String fieldName) {
		URI uri = parseHttpUrl(rawUrl, fieldName);
		String filteredQuery = uri.getRawQuery() == null ? null : removeTrackingQueryParameters(uri.getRawQuery());
		try {
			return new URI(
				uri.getScheme().toLowerCase(Locale.ROOT),
				uri.getAuthority().toLowerCase(Locale.ROOT),
				uri.getPath(),
				filteredQuery,
				null
			).toString();
		} catch (URISyntaxException exception) {
			throw new BadRequestException(fieldName + " must be a valid URL.");
		}
	}

	static String removeTrackingQueryParameters(String rawQuery) {
		String filtered = Arrays.stream(rawQuery.split("&"))
			.filter(parameter -> !isTrackingQueryParameter(parameter))
			.collect(Collectors.joining("&"));
		return filtered.isBlank() ? null : filtered;
	}

	static boolean isTrackingQueryParameter(String parameter) {
		String key = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
		return key.startsWith("utm_") || TRACKING_QUERY_KEYS.contains(key);
	}

	static BigDecimal validateCategoryConfidence(BigDecimal categoryConfidence) {
		if (categoryConfidence == null) {
			return null;
		}

		if (categoryConfidence.compareTo(MIN_CONFIDENCE) < 0 || categoryConfidence.compareTo(MAX_CONFIDENCE) > 0) {
			throw new BadRequestException("categoryConfidence must be between 0 and 100.");
		}
		return categoryConfidence;
	}
}
