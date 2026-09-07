package com.sagongsa.backend.decision;

import java.util.List;
import java.util.UUID;

public record CompleteDecisionCommand(
	UUID itemId,
	String result,
	Integer finalPrice,
	String rationaleText,
	List<SelfCheckAnswer> selfCheckAnswers
) {

	public record SelfCheckAnswer(
		String questionCode,
		Boolean answerBoolean
	) {
	}
}
