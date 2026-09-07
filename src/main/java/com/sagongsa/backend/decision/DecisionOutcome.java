package com.sagongsa.backend.decision;

import java.time.Instant;
import java.util.UUID;

public record DecisionOutcome(
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
	MascotReaction mascot,
	Reminder reminder,
	Instant decidedAt,
	boolean budgetExhausted
) {

	public record MascotReaction(
		String state,
		String message
	) {
	}

	public record Reminder(
		UUID id,
		String type,
		String status,
		Instant scheduledFor
	) {
	}
}
