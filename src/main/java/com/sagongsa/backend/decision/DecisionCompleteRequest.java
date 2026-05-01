package com.sagongsa.backend.decision;

import java.util.List;
import java.util.UUID;

public record DecisionCompleteRequest(
	UUID itemId,
	String result,
	Integer finalPrice,
	String rationaleText,
	List<SelfCheckAnswerRequest> selfCheckAnswers
) {

	public record SelfCheckAnswerRequest(
		String questionCode,
		Boolean answerBoolean
	) {
	}
}
