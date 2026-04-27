package com.sagongsa.backend.decision;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DecisionController.class)
public class DecisionExceptionHandler {

	@ExceptionHandler(DecisionBadRequestException.class)
	public ResponseEntity<ApiErrorResponse> handleBadRequest(DecisionBadRequestException exception) {
		return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadableJson() {
		return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request body is not readable JSON.");
	}

	@ExceptionHandler(DecisionForbiddenException.class)
	public ResponseEntity<ApiErrorResponse> handleForbidden(DecisionForbiddenException exception) {
		return error(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage());
	}

	@ExceptionHandler(DecisionNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(DecisionNotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
	}

	@ExceptionHandler(DecisionConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleConflict(DecisionConflictException exception) {
		return error(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation() {
		return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request violates decision data constraints.");
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
	}
}
