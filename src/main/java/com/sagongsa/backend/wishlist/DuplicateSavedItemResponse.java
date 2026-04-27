package com.sagongsa.backend.wishlist;

public record DuplicateSavedItemResponse(
	String code,
	String message,
	WishlistItemResponse existingItem
) {
}
