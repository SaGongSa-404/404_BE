package com.sagongsa.backend.wishlist;

import static com.sagongsa.backend.wishlist.WishlistPolicy.*;

import com.sagongsa.backend.domain.enums.ItemCategory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WishlistQueries {
	private final JdbcTemplate jdbcTemplate;
	public WishlistQueries(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

	@Transactional(readOnly = true)
	public WishlistItemPageResponse list(UUID userId, String rawCategory, Integer requestedLimit, WishlistCursor cursor) {
		ensureWishlistUserAllowed(userId);

		String category = null;
		if (StringUtils.hasText(rawCategory)) {
			category = parseRequiredEnum(rawCategory, ItemCategory.class, "category").name();
		}
		int limit = normalizeLimit(requestedLimit);
		List<Object> parameters = new ArrayList<>();
		parameters.add(userId);

		StringBuilder query = new StringBuilder(summarySelect());
		query.append("""
			where si.user_id = ?
			  and si.status = 'SAVED'
			""");
		if (category != null) {
			query.append("  and si.category = ?\n");
			parameters.add(category);
		}
		if (cursor != null && cursor.hasTieBreaker()) {
			query.append("  and (si.created_at < ? or (si.created_at = ? and si.id < ?))\n");
			OffsetDateTime cursorCreatedAt = cursor.createdAt().atOffset(ZoneOffset.UTC);
			parameters.add(cursorCreatedAt);
			parameters.add(cursorCreatedAt);
			parameters.add(cursor.id());
		} else if (cursor != null) {
			query.append("  and si.created_at < ?\n");
			parameters.add(cursor.createdAt().atOffset(ZoneOffset.UTC));
		}
		query.append("""
			order by si.created_at desc, si.id desc
			limit ?
			""");
		parameters.add(limit + 1);

		List<WishlistItemSummaryResponse> fetched = jdbcTemplate.query(
			query.toString(),
			this::mapSummaryRow,
			parameters.toArray()
		);
		boolean hasMore = fetched.size() > limit;
		List<WishlistItemSummaryResponse> items = hasMore ? fetched.subList(0, limit) : fetched;
		String nextCursor = hasMore ? WishlistCursor.encode(items.getLast()) : null;
		return new WishlistItemPageResponse(List.copyOf(items), nextCursor, hasMore);
	}

	@Transactional(readOnly = true)
	public WishlistItemResponse get(UUID userId, UUID itemId) {
		ensureWishlistUserAllowed(userId);
		return findSavedByUserAndId(userId, itemId);
	}

	WishlistItemResponse findByUserAndId(UUID userId, UUID itemId) {
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

	WishlistItemResponse findSavedByUserAndId(UUID userId, UUID itemId) {
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

	void ensureWishlistUserAllowed(UUID userId) {
		Boolean allowed;
		try {
			allowed = jdbcTemplate.queryForObject(
				"""
				select status = 'ACTIVE' and onboarding_status = 'COMPLETED'
				from users
				where id = ?
				""",
				Boolean.class,
				userId
			);
		}
		catch (EmptyResultDataAccessException exception) {
			throw new WishlistItemNotFoundException("User was not found.");
		}

		if (!Boolean.TRUE.equals(allowed)) {
			throw new WishlistForbiddenException("Wishlist can be used only by active users who completed onboarding.");
		}
	}

	Optional<WishlistItemResponse> findExistingSavedNormalizedUrl(UUID userId, String normalizedUrl) {
		return findExistingSavedNormalizedUrl(userId, normalizedUrl, null);
	}

	Optional<WishlistItemResponse> findExistingSavedNormalizedUrl(UUID userId, String normalizedUrl, UUID excludedItemId) {
		if (!StringUtils.hasText(normalizedUrl)) {
			return Optional.empty();
		}
		List<Object> parameters = new ArrayList<>();
		parameters.add(userId);
		parameters.add(normalizedUrl);
		StringBuilder query = new StringBuilder(baseSelect());
		query.append("""
			where si.user_id = ?
			  and si.normalized_url = ?
			  and si.status = 'SAVED'
			""");
		if (excludedItemId != null) {
			query.append("  and si.id <> ?\n");
			parameters.add(excludedItemId);
		}
		query.append("limit 1\n");
		List<WishlistItemResponse> items = jdbcTemplate.query(
			query.toString(),
			this::mapRow,
			parameters.toArray()
		);
		return items.stream().findFirst();
	}

	Optional<WishlistItemResponse> findByIdempotencyKey(UUID userId, String idempotencyKey) {
		if (!StringUtils.hasText(idempotencyKey)) {
			return Optional.empty();
		}
		List<WishlistItemResponse> items = jdbcTemplate.query(
			baseSelect() + """
			where si.user_id = ?
			  and si.idempotency_key = ?
			limit 1
			""",
			this::mapRow,
			userId,
			idempotencyKey
		);
		return items.stream().findFirst();
	}

	String baseSelect() {
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

	String summarySelect() {
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
				exists (
					select 1
					from feed_posts fp
					where fp.item_id = si.id
					  and fp.user_id = si.user_id
					  and fp.deleted_at is null
					  and fp.moderation_status = 'ACTIVE'
				) as selected,
				si.status,
				si.created_at,
				si.updated_at
			from saved_items si
			""";
	}

	WishlistItemResponse mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
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

	WishlistItemSummaryResponse mapSummaryRow(ResultSet resultSet, int rowNumber) throws SQLException {
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
			resultSet.getBoolean("selected"),
			resultSet.getString("status"),
			getInstant(resultSet, "created_at"),
			getInstant(resultSet, "updated_at")
		);
	}

	Integer getInteger(ResultSet resultSet, String columnName) throws SQLException {
		int value = resultSet.getInt(columnName);
		return resultSet.wasNull() ? null : value;
	}

	Instant getInstant(ResultSet resultSet, String columnName) throws SQLException {
		OffsetDateTime value = resultSet.getObject(columnName, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}
}
