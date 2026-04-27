package com.sagongsa.backend.wishlist;

public class DuplicateSavedItemException extends RuntimeException {

	private final WishlistItemResponse existingItem;

	public DuplicateSavedItemException(String message, WishlistItemResponse existingItem) {
		super(message);
		this.existingItem = existingItem;
	}

	public WishlistItemResponse existingItem() {
		return existingItem;
	}
}
