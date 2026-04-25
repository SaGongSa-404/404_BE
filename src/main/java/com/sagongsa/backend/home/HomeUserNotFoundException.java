package com.sagongsa.backend.home;

import java.util.UUID;

public class HomeUserNotFoundException extends RuntimeException {

	private final UUID userId;

	public HomeUserNotFoundException(UUID userId) {
		super("User not found for X-User-Id: " + userId);
		this.userId = userId;
	}

	public UUID getUserId() {
		return userId;
	}
}
