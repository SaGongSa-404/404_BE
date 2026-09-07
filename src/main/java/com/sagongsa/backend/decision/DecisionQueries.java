package com.sagongsa.backend.decision;

import static com.sagongsa.backend.domain.budget.BudgetLedger.*;

import static com.sagongsa.backend.decision.DecisionData.*;
import static com.sagongsa.backend.decision.DecisionPolicy.*;

import com.sagongsa.backend.decision.DecisionOutcome.MascotReaction;
import com.sagongsa.backend.decision.DecisionOutcome.Reminder;
import com.sagongsa.backend.domain.enums.PurchaseDecisionResult;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionQueries {
	private final DecisionJdbcRepository repository;
	public DecisionQueries(DecisionJdbcRepository repository) { this.repository = repository; }

	@Transactional(readOnly = true)
	public DecisionOutcome getResult(UUID userId, UUID decisionId) {
		return getResult(userId, decisionId, null);
	}

	DecisionOutcome getResult(UUID userId, UUID decisionId, BudgetExhaustion budgetExhaustionOverride) {
		repository.requireDecisionUser(userId);
		DecisionResult decision = repository.findDecisionResult(userId, decisionId)
			.orElseThrow(() -> new DecisionNotFoundException("Purchase decision result was not found."));
		Reminder reminder = repository.findReminder(decisionId).orElse(null);
		MascotReaction mascot = new MascotReaction(decision.mascotState(), decision.mascotMessage());
		BudgetExhaustion budgetExhaustion = budgetExhaustionOverride == null
			? budgetExhaustion(decision)
			: budgetExhaustionOverride;
		if (budgetExhaustionOverride == null) {
			budgetExhaustion = new BudgetExhaustion(budgetExhaustion.exhaustedAfter(), false);
		}

		return new DecisionOutcome(
			decision.id(),
			decision.itemId(),
			decision.itemTitle(),
			decision.itemStatus(),
			decision.result(),
			decision.finalPrice(),
			decision.budgetYearMonth(),
			decision.budgetAfterAmount(),
			budgetExhaustion.exhaustedAfter(),
			budgetExhaustion.becameExhausted(),
			decision.similarCategorySpendAmount(),
			decision.selfCheckYesCount(),
			decision.rationalityResult(),
			resultMessage(PurchaseDecisionResult.valueOf(decision.result()), reminder),
			mascot,
			reminder,
			decision.decidedAt(),
			decision.budgetExhausted()
		);
	}

}
