package com.sagongsa.backend.wishlist;

import static com.sagongsa.backend.wishlist.WishlistPolicy.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.wishlist.WishlistJdbcRepository.MetadataFields;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WishlistService {

	private final WishlistJdbcRepository repository;
	private final ObjectMapper objectMapper;
	private final WishlistQueries queries;

	public WishlistService(
		WishlistJdbcRepository repository,
		ObjectMapper objectMapper,
		WishlistQueries queries
	) {
		this.queries = queries;
		this.repository = repository;
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
			repository.insertItem(itemId, userId, inputSource, originalUrl, normalizedUrl, title, imageUrl, listedPrice, currencyCode, category, categoryConfidence, categoryLockedByUser, idempotencyKey, now);
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
			repository.insertMetadata(itemId, metadata, now);
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

		int updated = repository.updateCategory(userId, itemId, category, now);

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
			int updated = repository.updateItem(userId, itemId, originalUrl, normalizedUrl, title, listedPrice, category, now);

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
		int updated = repository.drop(userId, itemId, OffsetDateTime.now(ZoneOffset.UTC));

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

	@Transactional(readOnly = true)
	public WishlistItemPageResponse list(UUID userId, String category, Integer limit, WishlistCursor cursor) { return queries.list(userId, category, limit, cursor); }
	@Transactional(readOnly = true)
	public WishlistItemResponse get(UUID userId, UUID itemId) { return queries.get(userId, itemId); }
}
