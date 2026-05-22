package com.sagongsa.backend.wishlist;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = WishlistController.class)
public class WishlistExceptionHandler {

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException exception) {
		return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadableJson() {
		return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request body is not readable JSON.");
	}

	@ExceptionHandler(DuplicateSavedItemException.class)
	public ResponseEntity<DuplicateSavedItemResponse> handleDuplicate(DuplicateSavedItemException exception) {
		return ResponseEntity
			.status(HttpStatus.CONFLICT)
			.body(new DuplicateSavedItemResponse("DUPLICATE_SAVED_ITEM", exception.getMessage(), exception.existingItem()));
	}

	@ExceptionHandler(WishlistItemNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(WishlistItemNotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
	}

	@ExceptionHandler(WishlistForbiddenException.class)
	public ResponseEntity<ApiErrorResponse> handleForbidden(WishlistForbiddenException exception) {
		return error(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation() {
		return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request violates wishlist data constraints.");
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
	}
}
