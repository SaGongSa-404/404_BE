package com.sagongsa.backend.wishlist;

public class WishlistItemNotFoundException extends RuntimeException {

	public WishlistItemNotFoundException(String message) {
		super(message);
	}
}
