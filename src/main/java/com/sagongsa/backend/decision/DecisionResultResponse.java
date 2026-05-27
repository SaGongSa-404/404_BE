package com.sagongsa.backend.decision;

import java.time.Instant;
import java.util.UUID;

public record DecisionResultResponse(
	UUID decisionId,
	UUID itemId,
	String itemTitle,
	String itemStatus,
	String result,
	Integer finalPrice,
	String budgetYearMonth,
	Integer budgetAfterAmount,
	boolean budgetExhaustedAfter,
	boolean budgetBecameExhausted,
	Integer similarCategorySpendAmount,
	int selfCheckYesCount,
	String rationalityResult,
	String resultMessage,
	MascotReactionResponse mascot,
	ReminderResponse reminder,
	Instant decidedAt,
	boolean budgetExhausted
) {

	public record MascotReactionResponse(
		String state,
		String message
	) {
	}

	public record ReminderResponse(
		UUID id,
		String type,
		String status,
		Instant scheduledFor
	) {
	}
}
