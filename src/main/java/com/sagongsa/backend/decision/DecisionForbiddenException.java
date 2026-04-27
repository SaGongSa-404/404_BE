package com.sagongsa.backend.decision;

public class DecisionForbiddenException extends RuntimeException {

	public DecisionForbiddenException(String message) {
		super(message);
	}
}
