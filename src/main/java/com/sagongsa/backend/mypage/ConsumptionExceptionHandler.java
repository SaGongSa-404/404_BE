package com.sagongsa.backend.mypage;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ConsumptionController.class)
class ConsumptionExceptionHandler {

	record ErrorBody(String code, String message) {}

	@ExceptionHandler(ConsumptionNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	ErrorBody handleNotFound(ConsumptionNotFoundException ex) {
		return new ErrorBody("NOT_FOUND", ex.getMessage());
	}

	@ExceptionHandler(ConsumptionBadRequestException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ErrorBody handleBadRequest(ConsumptionBadRequestException ex) {
		return new ErrorBody("BAD_REQUEST", ex.getMessage());
	}
}
