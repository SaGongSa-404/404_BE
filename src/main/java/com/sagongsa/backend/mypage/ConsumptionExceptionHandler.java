package com.sagongsa.backend.mypage;

import com.sagongsa.backend.decision.DecisionBadRequestException;
import com.sagongsa.backend.decision.DecisionForbiddenException;
import com.sagongsa.backend.decision.DecisionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ConsumptionController.class)
class ConsumptionExceptionHandler {

	record ErrorBody(String code, String message) {}

	@ExceptionHandler({ConsumptionNotFoundException.class, DecisionNotFoundException.class})
	@ResponseStatus(HttpStatus.NOT_FOUND)
	ErrorBody handleNotFound(RuntimeException ex) {
		return new ErrorBody("NOT_FOUND", ex.getMessage());
	}

	@ExceptionHandler({ConsumptionBadRequestException.class, DecisionBadRequestException.class})
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ErrorBody handleBadRequest(RuntimeException ex) {
		return new ErrorBody("BAD_REQUEST", ex.getMessage());
	}

	@ExceptionHandler(DecisionForbiddenException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	ErrorBody handleForbidden(DecisionForbiddenException ex) {
		return new ErrorBody("FORBIDDEN", ex.getMessage());
	}
}
