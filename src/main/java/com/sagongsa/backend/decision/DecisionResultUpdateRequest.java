package com.sagongsa.backend.decision;

import java.util.List;

public record DecisionResultUpdateRequest(
	String result,
	Integer finalPrice,
	String changeReason,
	List<SelfCheckAnswerUpdateRequest> selfCheckAnswers
) {

	public record SelfCheckAnswerUpdateRequest(
		String questionCode,
		Boolean answerBoolean
	) {
	}
}
