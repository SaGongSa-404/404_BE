package com.sagongsa.backend.wishlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WishlistService {

	private static final BigDecimal MIN_CONFIDENCE = BigDecimal.ZERO;
	private static final BigDecimal MAX_CONFIDENCE = BigDecimal.valueOf(100);
	private static final int DEFAULT_LIST_LIMIT = 20;
	private static final int MAX_LIST_LIMIT = 50;
	private static final Set<String> TRACKING_QUERY_KEYS = Set.of(
		"fbclid", "gclid", "igshid", "mc_cid", "mc_eid", "n_media", "n_query", "n_rank", "n_ad_group"
	);

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public WishlistService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public WishlistItemResponse create(UUID userId, WishlistItemCreateRequest request) {
		ensureWishlistUserAllowed(userId);
		if (request == null) {
			throw new BadRequestException("Request body is required.");
		}

		ItemInputSource inputSource = parseRequiredEnum(request.inputSource(), ItemInputSource.class, "inputSource");
		ItemCategory category = parseRequiredEnum(request.category(), ItemCategory.class, "category");
		String title = cleanRequired(request.title(), "title", 255);
		String originalUrl = cleanOptional(request.originalUrl(), "originalUrl");
		String normalizedUrl = normalizeSavedUrl(inputSource, originalUrl, cleanOptional(request.normalizedUrl(), "normalizedUrl"));
		String imageUrl = cleanOptional(request.imageUrl(), "imageUrl");
		Integer listedPrice = validateListedPrice(request.listedPrice());
		String currencyCode = cleanCurrencyCode(request.currencyCode());
		BigDecimal categoryConfidence = validateCategoryConfidence(request.categoryConfidence());
		boolean categoryLockedByUser = Boolean.TRUE.equals(request.categoryLockedByUser());
		MetadataFields metadata = metadataFields(request);

		Optional<WishlistItemResponse> existingItem = findExistingSavedNormalizedUrl(userId, normalizedUrl);
		if (existingItem.isPresent()) {
			throw new DuplicateSavedItemException(
				"Saved wishlist item already exists for the normalized URL.",
				existingItem.get()
			);
		}

		UUID itemId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		try {
			jdbcTemplate.update(
				"""
				insert into saved_items (
					id, user_id, input_source, original_url, normalized_url, title, image_url,
					listed_price, currency_code, category, category_confidence, category_locked_by_user,
					status, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SAVED', ?, ?)
				""",
				itemId,
				userId,
				inputSource.name(),
				originalUrl,
				normalizedUrl,
				title,
				imageUrl,
				listedPrice,
				currencyCode,
				category.name(),
				categoryConfidence,
				categoryLockedByUser,
				now,
				now
			);
		}
		catch (DuplicateKeyException exception) {
			throw new DuplicateSavedItemException(
				"Saved wishlist item already exists for the normalized URL.",
				findExistingSavedNormalizedUrl(userId, normalizedUrl).orElse(null)
			);
		}

		if (metadata.hasAnyValue()) {
			jdbcTemplate.update(
				"""
				insert into item_source_metadata (
					item_id, source_domain, raw_title, raw_description, raw_price_text,
					raw_payload_json, extracted_at
				)
				values (?, ?, ?, ?, ?, cast(? as jsonb), ?)
				""",
				itemId,
				metadata.sourceDomain(),
				metadata.rawTitle(),
				metadata.rawDescription(),
				metadata.rawPriceText(),
				metadata.rawPayloadJson(),
				now
			);
		}

		return findByUserAndId(userId, itemId);
	}

	@Transactional(readOnly = true)
	public List<WishlistItemSummaryResponse> list(UUID userId, String rawCategory, Integer requestedLimit, Instant cursor) {
		ensureWishlistUserAllowed(userId);

		String category = null;
		if (StringUtils.hasText(rawCategory)) {
			category = parseRequiredEnum(rawCategory, ItemCategory.class, "category").name();
		}
		int limit = normalizeLimit(requestedLimit);

		if (category == null) {
			if (cursor == null) {
				return jdbcTemplate.query(
					summarySelect() + """
					where si.user_id = ?
					  and si.status = 'SAVED'
					order by si.created_at desc
					limit ?
					""",
					this::mapSummaryRow,
					userId,
					limit
				);
			}
			return jdbcTemplate.query(
				summarySelect() + """
				where si.user_id = ?
				  and si.status = 'SAVED'
				  and si.created_at < ?
				order by si.created_at desc
				limit ?
				""",
				this::mapSummaryRow,
				userId,
				cursor.atOffset(ZoneOffset.UTC),
				limit
			);
		}

		if (cursor == null) {
			return jdbcTemplate.query(
				summarySelect() + """
				where si.user_id = ?
				  and si.status = 'SAVED'
				  and si.category = ?
				order by si.created_at desc
				limit ?
				""",
				this::mapSummaryRow,
				userId,
				category,
				limit
			);
		}
		return jdbcTemplate.query(
			summarySelect() + """
			where si.user_id = ?
			  and si.status = 'SAVED'
			  and si.category = ?
			  and si.created_at < ?
			order by si.created_at desc
			limit ?
			""",
			this::mapSummaryRow,
			userId,
			category,
			cursor.atOffset(ZoneOffset.UTC),
			limit
		);
	}

	@Transactional(readOnly = true)
	public WishlistItemResponse get(UUID userId, UUID itemId) {
		ensureWishlistUserAllowed(userId);
		return findSavedByUserAndId(userId, itemId);
	}

	@Transactional
	public WishlistItemResponse updateCategory(UUID userId, UUID itemId, WishlistCategoryUpdateRequest request) {
		ensureWishlistUserAllowed(userId);
		if (request == null) {
			throw new BadRequestException("Request body is required.");
		}
		ItemCategory category = parseRequiredEnum(request.category(), ItemCategory.class, "category");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		int updated = jdbcTemplate.update(
			"""
			update saved_items
			set category = ?,
				category_locked_by_user = true,
				updated_at = ?
			where user_id = ?
			  and id = ?
			  and status = 'SAVED'
			""",
			category.name(),
			now,
			userId,
			itemId
		);

		if (updated == 0) {
			throw new WishlistItemNotFoundException("Saved wishlist item was not found.");
		}

		return findByUserAndId(userId, itemId);
	}

	@Transactional
	public void drop(UUID userId, UUID itemId) {
		ensureWishlistUserAllowed(userId);
		int updated = jdbcTemplate.update(
			"""
			update saved_items
			set status = 'DROPPED',
				updated_at = ?
			where user_id = ?
			  and id = ?
			  and status = 'SAVED'
			""",
			OffsetDateTime.now(ZoneOffset.UTC),
			userId,
			itemId
		);

		if (updated == 0) {
			throw new WishlistItemNotFoundException("Saved wishlist item was not found.");
		}
	}

	private WishlistItemResponse findByUserAndId(UUID userId, UUID itemId) {
		List<WishlistItemResponse> items = jdbcTemplate.query(
			baseSelect() + """
			where si.user_id = ?
			  and si.id = ?
			""",
			this::mapRow,
			userId,
			itemId
		);

		if (items.isEmpty()) {
			throw new WishlistItemNotFoundException("Saved wishlist item was not found.");
		}

		return items.getFirst();
	}

	private WishlistItemResponse findSavedByUserAndId(UUID userId, UUID itemId) {
		List<WishlistItemResponse> items = jdbcTemplate.query(
			baseSelect() + """
			where si.user_id = ?
			  and si.id = ?
			  and si.status = 'SAVED'
			""",
			this::mapRow,
			userId,
			itemId
		);

		if (items.isEmpty()) {
			throw new WishlistItemNotFoundException("Saved wishlist item was not found.");
		}

		return items.getFirst();
	}

	private void ensureWishlistUserAllowed(UUID userId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"select exists(select 1 from users where id = ?)",
			Boolean.class,
			userId
		);
		if (!Boolean.TRUE.equals(exists)) {
			throw new WishlistItemNotFoundException("User was not found.");
		}

		Boolean allowed = jdbcTemplate.queryForObject(
			"""
			select exists(
				select 1
				from users
				where id = ?
				  and status = 'ACTIVE'
				  and onboarding_status = 'COMPLETED'
			)
			""",
			Boolean.class,
			userId
		);
		if (!Boolean.TRUE.equals(allowed)) {
			throw new WishlistForbiddenException("Wishlist can be used only by active users who completed onboarding.");
		}
	}

	private Optional<WishlistItemResponse> findExistingSavedNormalizedUrl(UUID userId, String normalizedUrl) {
		List<WishlistItemResponse> items = jdbcTemplate.query(
			baseSelect() + """
			where si.user_id = ?
			  and si.normalized_url = ?
			  and si.status = 'SAVED'
			limit 1
			""",
			this::mapRow,
			userId,
			normalizedUrl
		);
		return items.stream().findFirst();
	}

	private String baseSelect() {
		return """
			select
				si.id,
				si.user_id,
				si.input_source,
				si.original_url,
				si.normalized_url,
				si.title,
				si.image_url,
				si.listed_price,
				trim(si.currency_code) as currency_code,
				si.category,
				si.category_confidence,
				si.category_locked_by_user,
				si.status,
				si.created_at,
				si.updated_at,
				ism.source_domain,
				ism.raw_title,
				ism.raw_description,
				ism.raw_price_text,
				ism.raw_payload_json::text as raw_payload_json,
				ism.extracted_at
			from saved_items si
			left join item_source_metadata ism on ism.item_id = si.id
			""";
	}

	private String summarySelect() {
		return """
			select
				si.id,
				si.user_id,
				si.input_source,
				si.original_url,
				si.normalized_url,
				si.title,
				si.image_url,
				si.listed_price,
				trim(si.currency_code) as currency_code,
				si.category,
				si.category_confidence,
				si.category_locked_by_user,
				si.status,
				si.created_at,
				si.updated_at
			from saved_items si
			""";
	}

	private WishlistItemResponse mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
		return new WishlistItemResponse(
			resultSet.getObject("id", UUID.class),
			resultSet.getObject("user_id", UUID.class),
			resultSet.getString("input_source"),
			resultSet.getString("original_url"),
			resultSet.getString("normalized_url"),
			resultSet.getString("title"),
			resultSet.getString("image_url"),
			getInteger(resultSet, "listed_price"),
			resultSet.getString("currency_code"),
			resultSet.getString("category"),
			resultSet.getBigDecimal("category_confidence"),
			resultSet.getBoolean("category_locked_by_user"),
			resultSet.getString("status"),
			getInstant(resultSet, "created_at"),
			getInstant(resultSet, "updated_at"),
			resultSet.getString("source_domain"),
			resultSet.getString("raw_title"),
			resultSet.getString("raw_description"),
			resultSet.getString("raw_price_text"),
			resultSet.getString("raw_payload_json"),
			getInstant(resultSet, "extracted_at")
		);
	}

	private WishlistItemSummaryResponse mapSummaryRow(ResultSet resultSet, int rowNumber) throws SQLException {
		return new WishlistItemSummaryResponse(
			resultSet.getObject("id", UUID.class),
			resultSet.getObject("user_id", UUID.class),
			resultSet.getString("input_source"),
			resultSet.getString("original_url"),
			resultSet.getString("normalized_url"),
			resultSet.getString("title"),
			resultSet.getString("image_url"),
			getInteger(resultSet, "listed_price"),
			resultSet.getString("currency_code"),
			resultSet.getString("category"),
			resultSet.getBigDecimal("category_confidence"),
			resultSet.getBoolean("category_locked_by_user"),
			resultSet.getString("status"),
			getInstant(resultSet, "created_at"),
			getInstant(resultSet, "updated_at")
		);
	}

	private Integer getInteger(ResultSet resultSet, String columnName) throws SQLException {
		int value = resultSet.getInt(columnName);
		return resultSet.wasNull() ? null : value;
	}

	private Instant getInstant(ResultSet resultSet, String columnName) throws SQLException {
		OffsetDateTime value = resultSet.getObject(columnName, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private <T extends Enum<T>> T parseRequiredEnum(String value, Class<T> enumType, String fieldName) {
		String cleaned = cleanRequired(value, fieldName, 80).toUpperCase(Locale.ROOT);
		try {
			return Enum.valueOf(enumType, cleaned);
		}
		catch (IllegalArgumentException exception) {
			throw new BadRequestException(fieldName + " has an unsupported value.");
		}
	}

	private String cleanRequired(String value, String fieldName, int maxLength) {
		String cleaned = cleanOptional(value, fieldName);
		if (cleaned == null) {
			throw new BadRequestException(fieldName + " is required.");
		}
		if (cleaned.length() > maxLength) {
			throw new BadRequestException(fieldName + " must be " + maxLength + " characters or fewer.");
		}
		return cleaned;
	}

	private String cleanOptional(String value, String fieldName) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	private String cleanOptional(String value, String fieldName, int maxLength) {
		String cleaned = cleanOptional(value, fieldName);
		if (cleaned != null && cleaned.length() > maxLength) {
			throw new BadRequestException(fieldName + " must be " + maxLength + " characters or fewer.");
		}
		return cleaned;
	}

	private Integer validateListedPrice(Integer listedPrice) {
		if (listedPrice != null && listedPrice < 0) {
			throw new BadRequestException("listedPrice must be zero or greater.");
		}
		return listedPrice;
	}

	private String cleanCurrencyCode(String currencyCode) {
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

	private String normalizeSavedUrl(ItemInputSource inputSource, String originalUrl, String normalizedUrl) {
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

	private URI parseHttpUrl(String rawUrl, String fieldName) {
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

	private int normalizeLimit(Integer requestedLimit) {
		if (requestedLimit == null) {
			return DEFAULT_LIST_LIMIT;
		}
		if (requestedLimit < 1 || requestedLimit > MAX_LIST_LIMIT) {
			throw new BadRequestException("limit must be between 1 and " + MAX_LIST_LIMIT + ".");
		}
		return requestedLimit;
	}

	private String canonicalizeHttpUrl(String rawUrl, String fieldName) {
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

	private String removeTrackingQueryParameters(String rawQuery) {
		String filtered = Arrays.stream(rawQuery.split("&"))
			.filter(parameter -> !isTrackingQueryParameter(parameter))
			.collect(Collectors.joining("&"));
		return filtered.isBlank() ? null : filtered;
	}

	private boolean isTrackingQueryParameter(String parameter) {
		String key = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
		return key.startsWith("utm_") || TRACKING_QUERY_KEYS.contains(key);
	}

	private BigDecimal validateCategoryConfidence(BigDecimal categoryConfidence) {
		if (categoryConfidence == null) {
			return null;
		}

		if (categoryConfidence.compareTo(MIN_CONFIDENCE) < 0 || categoryConfidence.compareTo(MAX_CONFIDENCE) > 0) {
			throw new BadRequestException("categoryConfidence must be between 0 and 100.");
		}
		return categoryConfidence;
	}

	private MetadataFields metadataFields(WishlistItemCreateRequest request) {
		String rawPayloadJson = cleanOptional(request.rawPayloadJson(), "rawPayloadJson");
		if (rawPayloadJson != null) {
			try {
				objectMapper.readTree(rawPayloadJson);
			}
			catch (JsonProcessingException exception) {
				throw new BadRequestException("rawPayloadJson must be valid JSON.");
			}
		}

		return new MetadataFields(
			cleanOptional(request.sourceDomain(), "sourceDomain", 120),
			cleanOptional(request.rawTitle(), "rawTitle"),
			cleanOptional(request.rawDescription(), "rawDescription"),
			cleanOptional(request.rawPriceText(), "rawPriceText", 120),
			rawPayloadJson
		);
	}

	private record MetadataFields(
		String sourceDomain,
		String rawTitle,
		String rawDescription,
		String rawPriceText,
		String rawPayloadJson
	) {

		boolean hasAnyValue() {
			return sourceDomain != null
				|| rawTitle != null
				|| rawDescription != null
				|| rawPriceText != null
				|| rawPayloadJson != null;
		}
	}
}
