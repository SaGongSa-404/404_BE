package com.sagongsa.backend.onboarding;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OnboardingController.class)
class OnboardingExceptionHandler {

	@ExceptionHandler(OnboardingBadRequestException.class)
	ResponseEntity<OnboardingErrorResponse> handleBadRequest(OnboardingBadRequestException exception) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(new OnboardingErrorResponse("BAD_REQUEST", exception.getMessage()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<OnboardingErrorResponse> handleUnreadableRequest() {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(new OnboardingErrorResponse("BAD_REQUEST", "Request body is malformed."));
	}

	@ExceptionHandler(OnboardingNotFoundException.class)
	ResponseEntity<OnboardingErrorResponse> handleNotFound(OnboardingNotFoundException exception) {
		return ResponseEntity
			.status(HttpStatus.NOT_FOUND)
			.body(new OnboardingErrorResponse("USER_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(OnboardingConflictException.class)
	ResponseEntity<OnboardingErrorResponse> handleConflict(OnboardingConflictException exception) {
		return ResponseEntity
			.status(HttpStatus.CONFLICT)
			.body(new OnboardingErrorResponse("ONBOARDING_CONFLICT", exception.getMessage()));
	}

	private record OnboardingErrorResponse(String code, String message) {
	}
}
