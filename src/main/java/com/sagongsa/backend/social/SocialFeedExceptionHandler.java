package com.sagongsa.backend.social;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {SocialFeedController.class, BlockController.class, UserReportController.class})
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

	@ExceptionHandler(FileUploadInvalidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ErrorBody handleFileUploadInvalid(FileUploadInvalidException ex) {
		return new ErrorBody("INVALID_FILE", ex.getMessage());
	}

	@ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ErrorBody handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
		String message = ex.getConstraintViolations().stream()
			.map(v -> v.getPropertyPath() + ": " + v.getMessage())
			.findFirst()
			.orElse("잘못된 요청입니다.");
		return new ErrorBody("INVALID_REQUEST", message);
	}
}
