package com.sagongsa.backend.wishlist;

public record WishlistItemUpdateRequest(
	String originalUrl,
	String normalizedUrl,
	String title,
	Integer listedPrice,
	String category
) {
}
