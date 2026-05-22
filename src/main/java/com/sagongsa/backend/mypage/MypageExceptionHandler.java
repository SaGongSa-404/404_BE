package com.sagongsa.backend.mypage;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MypageController.class)
class MypageExceptionHandler {

	record ErrorBody(String code, String message) {}

	@ExceptionHandler(MypageNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	ErrorBody handleNotFound(MypageNotFoundException ex) {
		return new ErrorBody("NOT_FOUND", ex.getMessage());
	}
}
