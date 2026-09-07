package com.sagongsa.backend.wishlist;

import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 위시 명령의 트랜잭션에 참여하며 저장 순서와 변경 행 수를 보존한다. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class WishlistJdbcRepository {
	private final JdbcTemplate jdbcTemplate;

	WishlistJdbcRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void insertItem(
		UUID itemId,
		UUID userId,
		ItemInputSource inputSource,
		String originalUrl,
		String normalizedUrl,
		String title,
		String imageUrl,
		Integer listedPrice,
		String currencyCode,
		ItemCategory category,
		BigDecimal categoryConfidence,
		boolean categoryLockedByUser,
		String idempotencyKey,
		OffsetDateTime now
	) {
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

	public void insertMetadata(
		UUID itemId,
		MetadataFields metadata,
		OffsetDateTime now
	) {
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

	public int updateCategory(
		UUID userId,
		UUID itemId,
		ItemCategory category,
		OffsetDateTime now
	) {
		return jdbcTemplate.update(
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
	}

	public int updateItem(
		UUID userId,
		UUID itemId,
		String originalUrl,
		String normalizedUrl,
		String title,
		Integer listedPrice,
		ItemCategory category,
		OffsetDateTime now
	) {
		return jdbcTemplate.update(
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
	}

	public int drop(
		UUID userId,
		UUID itemId,
		OffsetDateTime now
	) {
		return jdbcTemplate.update(
			"""
			update saved_items
			set status = 'DROPPED',
				updated_at = ?
			where user_id = ?
			  and id = ?
			  and status = 'SAVED'
			""",
			now,
			userId,
			itemId
		);
	}

	record MetadataFields(
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
