package com.sagongsa.backend.wishlist;

public class DuplicateSavedItemException extends RuntimeException {

	public DuplicateSavedItemException(String message) {
		super(message);
	}
}
