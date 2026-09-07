package com.sagongsa.backend.decision;

final class DecisionWebMapper {
	private DecisionWebMapper() {}

	static CompleteDecisionCommand command(DecisionCompleteRequest request) {
		if (request == null) return null;
		return new CompleteDecisionCommand(request.itemId(), request.result(), request.finalPrice(), request.rationaleText(),
			request.selfCheckAnswers() == null ? null : request.selfCheckAnswers().stream()
				.map(a -> a == null ? null : new CompleteDecisionCommand.SelfCheckAnswer(a.questionCode(), a.answerBoolean())).toList());
	}

	static ChangeDecisionCommand command(DecisionResultUpdateRequest request) {
		if (request == null) return null;
		return new ChangeDecisionCommand(request.result(), request.finalPrice(), request.changeReason(),
			request.selfCheckAnswers() == null ? null : request.selfCheckAnswers().stream()
				.map(a -> a == null ? null : new ChangeDecisionCommand.SelfCheckAnswer(a.questionCode(), a.answerBoolean())).toList());
	}

	static DecisionResultResponse response(DecisionOutcome r) {
		return new DecisionResultResponse(r.decisionId(), r.itemId(), r.itemTitle(), r.itemStatus(), r.result(),
			r.finalPrice(), r.budgetYearMonth(), r.budgetAfterAmount(), r.budgetExhaustedAfter(), r.budgetBecameExhausted(),
			r.similarCategorySpendAmount(), r.selfCheckYesCount(), r.rationalityResult(), r.resultMessage(),
			r.mascot() == null ? null : new DecisionResultResponse.MascotReactionResponse(r.mascot().state(), r.mascot().message()),
			r.reminder() == null ? null : new DecisionResultResponse.ReminderResponse(r.reminder().id(), r.reminder().type(), r.reminder().status(), r.reminder().scheduledFor()),
			r.decidedAt(), r.budgetExhausted());
	}
}
