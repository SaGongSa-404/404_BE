package com.sagongsa.backend.wishlist;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WishlistItemSummaryResponse(
	UUID id,
	UUID userId,
	String inputSource,
	String originalUrl,
	String normalizedUrl,
	String title,
	String imageUrl,
	Integer listedPrice,
	String currencyCode,
	String category,
	BigDecimal categoryConfidence,
	boolean categoryLockedByUser,
	boolean selected,
	String status,
	Instant createdAt,
	Instant updatedAt
) {
}
