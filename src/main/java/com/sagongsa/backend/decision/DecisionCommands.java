package com.sagongsa.backend.decision;

import com.sagongsa.backend.domain.budget.BudgetLedger;
import static com.sagongsa.backend.domain.budget.BudgetLedger.*;

import static com.sagongsa.backend.decision.DecisionData.*;
import static com.sagongsa.backend.decision.DecisionPolicy.*;

import com.sagongsa.backend.decision.DecisionOutcome.MascotReaction;
import com.sagongsa.backend.decision.DecisionOutcome.Reminder;
import com.sagongsa.backend.domain.budget.BudgetCycleRolloverService;
import com.sagongsa.backend.domain.enums.PurchaseDecisionResult;
import com.sagongsa.backend.domain.enums.RationalityResult;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionCommands {

	private final DecisionJdbcRepository repository;
	private final DecisionQueries queries;
	private final BudgetLedger budgetLedger;
	private final java.time.Clock clock;
	private final BudgetCycleRolloverService budgetCycleRolloverService;

	public DecisionCommands(DecisionJdbcRepository repository, BudgetCycleRolloverService budgetCycleRolloverService, java.time.Clock clock, DecisionQueries queries, BudgetLedger budgetLedger) {
		this.budgetLedger = budgetLedger;
		this.queries = queries;
		this.repository = repository;
		this.clock = clock;
		this.budgetCycleRolloverService = budgetCycleRolloverService;
	}

	@Transactional
	public DecisionOutcome complete(UUID userId, CompleteDecisionCommand request) {
		NormalizedDecisionRequest normalized = normalize(request);
		UserContext user = repository.requireDecisionUser(userId);
		SavedItem item = repository.lockSavedItem(userId, normalized.itemId());
		if (repository.decisionExists(item.id())) {
			return queries.getResult(userId, repository.findDecisionIdByItemId(item.id()));
		}
		if (!Objects.equals(item.status(), "SAVED")) {
			throw new DecisionConflictException("Only saved wishlist items can be decided.");
		}

		ZoneId zoneId = zoneId(user.timezone());
		OffsetDateTime now = OffsetDateTime.now(clock);
		String yearMonth = YearMonth.now(clock.withZone(zoneId)).toString();
		budgetCycleRolloverService.ensureBudgetCycle(userId, yearMonth);
		BudgetCycle budgetCycle = budgetLedger.lockBudgetCycle(userId, yearMonth);
		Integer finalPrice = resolveFinalPrice(normalized.result(), normalized.finalPrice(), item.listedPrice());
		Rationality rationality = rationality(normalized.selfCheckAnswers());
		Integer similarCategorySpendAmount = repository.similarCategorySpendAmount(userId, item.category(), zoneId, now.toInstant());
		int budgetAfterAmount = budgetCycle.spentAmount() + (normalized.result() == PurchaseDecisionResult.GO ? finalPrice : 0);
		BudgetExhaustion budgetExhaustion = budgetExhaustion(
			budgetCycle.monthlyBudgetAmount(),
			budgetCycle.spentAmount(),
			budgetAfterAmount
		);
		UUID decisionId = UUID.randomUUID();

		repository.insertDecision(
			decisionId,
			userId,
			item,
			budgetCycle.id(),
			normalized.result(),
			finalPrice,
			budgetAfterAmount,
			similarCategorySpendAmount,
			rationality,
			normalized.rationaleText(),
			now
		);
		repository.insertSelfCheck(decisionId, rationality, normalized.selfCheckAnswers(), now);
		repository.updateItemStatus(item.id(), normalized.result(), now);
		if (normalized.result() == PurchaseDecisionResult.GO) {
			budgetLedger.incrementBudgetSpent(budgetCycle.id(), finalPrice, now);
		}

		DecisionData.MascotReaction mascotReaction = repository.updateMascotReaction(userId, item.id(), decisionId, normalized.result(), rationality.result(), now);
		Reminder reminder = repository.maybeScheduleRegretReminder(userId, item.id(), decisionId, normalized.result(), now);
		boolean budgetExhausted = budgetCycle.monthlyBudgetAmount() > 0 && budgetAfterAmount >= budgetCycle.monthlyBudgetAmount();

		return new DecisionOutcome(
			decisionId,
			item.id(),
			item.title(),
			normalized.result().name(),
			normalized.result().name(),
			finalPrice,
			yearMonth,
			budgetAfterAmount,
			budgetExhaustion.exhaustedAfter(),
			budgetExhaustion.becameExhausted(),
			similarCategorySpendAmount,
			rationality.yesCount(),
			rationality.result().name(),
			resultMessage(normalized.result(), reminder),
			new MascotReaction(mascotReaction.state().name(), mascotReaction.message()),
			reminder,
			now.toInstant(),
			budgetExhausted
		);
	}

	@Transactional
	public DecisionOutcome updateResult(UUID userId, UUID decisionId, ChangeDecisionCommand request) {
		NormalizedDecisionUpdateRequest normalized = normalizeUpdate(request);
		UserContext user = repository.requireDecisionUser(userId);
		DecisionForUpdate decision = repository.lockDecision(userId, decisionId);
		OffsetDateTime now = OffsetDateTime.now(clock);
		PurchaseDecisionResult previousResult = PurchaseDecisionResult.valueOf(decision.result());
		Integer newFinalPrice = resolveUpdatedFinalPrice(
			normalized.result(),
			normalized.finalPrice(),
			decision.finalPrice(),
			decision.itemListedPrice()
		);
		Rationality newRationality = normalized.selfCheckAnswers() == null
			? new Rationality(decision.selfCheckYesCount(), RationalityResult.valueOf(decision.rationalityResult()))
			: rationality(normalized.selfCheckAnswers());
		Integer previousGoAmount = previousResult == PurchaseDecisionResult.GO ? Objects.requireNonNullElse(decision.finalPrice(), 0) : 0;
		Integer newGoAmount = normalized.result() == PurchaseDecisionResult.GO ? Objects.requireNonNullElse(newFinalPrice, 0) : 0;
		int budgetDelta = newGoAmount - previousGoAmount;
		BudgetUpdate budgetUpdate = budgetLedger.updateBudgetForDecisionChange(decision.budgetCycleId(), budgetDelta, now);
		int budgetAfterAmount = budgetUpdate.spentAmount();

		repository.updateDecisionResult(decision, normalized.result(), newFinalPrice, newRationality, budgetAfterAmount, now);
		repository.updateItemStatus(decision.itemId(), normalized.result(), now);
		repository.updateSelfCheckIfNeeded(decision.id(), normalized.selfCheckAnswers(), newRationality, now);
		repository.insertDecisionChangeLog(decision, normalized, newFinalPrice, newRationality, now);
		repository.updateReminderForResultChange(
			userId,
			decision.itemId(),
			decision.id(),
			previousResult,
			normalized.result(),
			now
		);

		return queries.getResult(userId, decisionId, budgetUpdate.exhaustion());
	}

}
