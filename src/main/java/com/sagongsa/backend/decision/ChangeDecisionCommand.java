package com.sagongsa.backend.decision;

import java.util.List;

public record ChangeDecisionCommand(
	String result,
	Integer finalPrice,
	String changeReason,
	List<SelfCheckAnswer> selfCheckAnswers
) {

	public record SelfCheckAnswer(
		String questionCode,
		Boolean answerBoolean
	) {
	}
}
