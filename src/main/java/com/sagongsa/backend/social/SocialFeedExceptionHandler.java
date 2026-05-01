package com.sagongsa.backend.social;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SocialFeedController.class)
class SocialFeedExceptionHandler {

	record ErrorBody(String code, String message) {}

	@ExceptionHandler(SocialFeedNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	ErrorBody handleNotFound(SocialFeedNotFoundException ex) {
		return new ErrorBody("NOT_FOUND", ex.getMessage());
	}

	@ExceptionHandler(SocialFeedForbiddenException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	ErrorBody handleForbidden(SocialFeedForbiddenException ex) {
		return new ErrorBody("FORBIDDEN", ex.getMessage());
	}
}
