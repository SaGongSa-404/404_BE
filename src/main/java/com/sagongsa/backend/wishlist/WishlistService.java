package com.sagongsa.backend.wishlist;

import static com.sagongsa.backend.wishlist.WishlistPolicy.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WishlistService {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private final WishlistQueries queries;

	public WishlistService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, WishlistQueries queries) {
		this.queries = queries;
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public WishlistItemResponse create(UUID userId, WishlistItemCreateRequest request) {
		return create(userId, request, null);
	}

	@Transactional
	public WishlistItemResponse create(UUID userId, WishlistItemCreateRequest request, String rawIdempotencyKey) {
		queries.ensureWishlistUserAllowed(userId);
		if (request == null) {
			throw new BadRequestException("Request body is required.");
		}

		String idempotencyKey = cleanOptional(rawIdempotencyKey, "Idempotency-Key", MAX_IDEMPOTENCY_KEY_LENGTH);
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

		Optional<WishlistItemResponse> idempotentItem = queries.findByIdempotencyKey(userId, idempotencyKey);
		if (idempotentItem.isPresent()) {
			return idempotentItem.get();
		}

		Optional<WishlistItemResponse> existingItem = queries.findExistingSavedNormalizedUrl(userId, normalizedUrl);
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
					idempotency_key, status, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SAVED', ?, ?)
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
				idempotencyKey,
				now,
				now
			);
		}
		catch (DuplicateKeyException exception) {
			Optional<WishlistItemResponse> duplicateIdempotentItem = queries.findByIdempotencyKey(userId, idempotencyKey);
			if (duplicateIdempotentItem.isPresent()) {
				return duplicateIdempotentItem.get();
			}
			throw new DuplicateSavedItemException(
				"Saved wishlist item already exists for the normalized URL.",
				queries.findExistingSavedNormalizedUrl(userId, normalizedUrl).orElse(null)
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

		return queries.findByUserAndId(userId, itemId);
	}

	@Transactional
	public WishlistItemResponse updateCategory(UUID userId, UUID itemId, WishlistCategoryUpdateRequest request) {
		queries.ensureWishlistUserAllowed(userId);
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

		return queries.findByUserAndId(userId, itemId);
	}

	@Transactional
	public WishlistItemResponse update(UUID userId, UUID itemId, WishlistItemUpdateRequest request) {
		queries.ensureWishlistUserAllowed(userId);
		if (request == null) {
			throw new BadRequestException("Request body is required.");
		}

		WishlistItemResponse current = queries.findSavedByUserAndId(userId, itemId);
		ItemInputSource inputSource = parseRequiredEnum(current.inputSource(), ItemInputSource.class, "inputSource");
		ItemCategory category = parseRequiredEnum(request.category(), ItemCategory.class, "category");
		String title = cleanRequired(request.title(), "title", 255);
		Integer listedPrice = validateListedPrice(request.listedPrice());
		String originalUrl = cleanOptional(request.originalUrl(), "originalUrl");
		String normalizedUrl = cleanOptional(request.normalizedUrl(), "normalizedUrl");

		if (inputSource == ItemInputSource.SHARE) {
			if (StringUtils.hasText(originalUrl) || StringUtils.hasText(normalizedUrl)) {
				throw new BadRequestException("SHARE wishlist item URL cannot be updated.");
			}
			originalUrl = current.originalUrl();
			normalizedUrl = current.normalizedUrl();
		} else {
			normalizedUrl = normalizeSavedUrl(inputSource, originalUrl, normalizedUrl);
			queries.findExistingSavedNormalizedUrl(userId, normalizedUrl, itemId)
				.ifPresent(existingItem -> {
					throw new DuplicateSavedItemException(
						"Saved wishlist item already exists for the normalized URL.",
						existingItem
					);
				});
		}

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		try {
			int updated = jdbcTemplate.update(
				"""
				update saved_items
				set original_url = ?,
					normalized_url = ?,
					title = ?,
					listed_price = ?,
					category = ?,
					category_locked_by_user = true,
					updated_at = ?
				where user_id = ?
				  and id = ?
				  and status = 'SAVED'
				""",
				originalUrl,
				normalizedUrl,
				title,
				listedPrice,
				category.name(),
				now,
				userId,
				itemId
			);

			if (updated == 0) {
				throw new WishlistItemNotFoundException("Saved wishlist item was not found.");
			}
		}
		catch (DuplicateKeyException exception) {
			throw new DuplicateSavedItemException(
				"Saved wishlist item already exists for the normalized URL.",
				queries.findExistingSavedNormalizedUrl(userId, normalizedUrl, itemId).orElse(null)
			);
		}

		return queries.findByUserAndId(userId, itemId);
	}

	@Transactional
	public void drop(UUID userId, UUID itemId) {
		queries.ensureWishlistUserAllowed(userId);
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
	@Transactional(readOnly = true)
	public WishlistItemPageResponse list(UUID userId, String category, Integer limit, WishlistCursor cursor) { return queries.list(userId, category, limit, cursor); }
	@Transactional(readOnly = true)
	public WishlistItemResponse get(UUID userId, UUID itemId) { return queries.get(userId, itemId); }
}
