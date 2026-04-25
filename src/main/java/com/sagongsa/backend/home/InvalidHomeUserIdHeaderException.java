package com.sagongsa.backend.home;

public class InvalidHomeUserIdHeaderException extends RuntimeException {

	private final String headerValue;

	public InvalidHomeUserIdHeaderException(String headerValue) {
		super("X-User-Id header must be a valid UUID");
		this.headerValue = headerValue;
	}

	public String getHeaderValue() {
		return headerValue;
	}
}
