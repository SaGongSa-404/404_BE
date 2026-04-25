package com.sagongsa.backend.wishlist;

import java.math.BigDecimal;

public record WishlistItemCreateRequest(
	String inputSource,
	String originalUrl,
	String normalizedUrl,
	String title,
	String imageUrl,
	Integer listedPrice,
	String currencyCode,
	String category,
	BigDecimal categoryConfidence,
	Boolean categoryLockedByUser,
	String sourceDomain,
	String rawTitle,
	String rawDescription,
	String rawPriceText,
	String rawPayloadJson
) {
}
