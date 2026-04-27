package com.sagongsa.backend.home;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = HomeSummaryController.class)
public class HomeExceptionHandler {

	@ExceptionHandler(HomeUserNotFoundException.class)
	public ResponseEntity<ProblemDetail> handleUserNotFound(HomeUserNotFoundException exception) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problemDetail.setTitle("User Not Found");
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
	}

	@ExceptionHandler(HomeForbiddenException.class)
	public ResponseEntity<ProblemDetail> handleForbidden(HomeForbiddenException exception) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
		problemDetail.setTitle("Forbidden");
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail);
	}

}
