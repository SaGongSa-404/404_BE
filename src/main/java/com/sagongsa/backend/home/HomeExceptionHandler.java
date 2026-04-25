package com.sagongsa.backend.home;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
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

	@ExceptionHandler(InvalidHomeUserIdHeaderException.class)
	public ResponseEntity<ProblemDetail> handleInvalidUserIdHeader(InvalidHomeUserIdHeaderException exception) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
		problemDetail.setTitle("Invalid X-User-Id Header");
		return ResponseEntity.badRequest().body(problemDetail);
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException exception) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
			HttpStatus.BAD_REQUEST,
			exception.getHeaderName() + " header is required"
		);
		problemDetail.setTitle("Missing Required Header");
		return ResponseEntity.badRequest().body(problemDetail);
	}
}
