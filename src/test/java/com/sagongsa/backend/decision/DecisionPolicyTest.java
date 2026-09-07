package com.sagongsa.backend.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.domain.enums.PurchaseDecisionResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DecisionPolicyTest {

	@Test
	void stopClearsPriceAndGoUsesPreviousPriceBeforeListedPrice() {
		assertThat(DecisionPolicy.resolveUpdatedFinalPrice(PurchaseDecisionResult.STOP, 50, 30, 20)).isNull();
		assertThat(DecisionPolicy.resolveUpdatedFinalPrice(PurchaseDecisionResult.GO, null, 30, 20)).isEqualTo(30);
		assertThat(DecisionPolicy.resolveUpdatedFinalPrice(PurchaseDecisionResult.GO, null, null, 20)).isEqualTo(20);
		assertThatThrownBy(() -> DecisionPolicy.resolveFinalPrice(PurchaseDecisionResult.GO, null, null))
			.isInstanceOf(DecisionBadRequestException.class);
	}

	@Test
	void budgetWarningRepresentsThresholdCrossingAndZeroBudgetNeverExhausts() {
		assertThat(com.sagongsa.backend.domain.budget.BudgetLedger.budgetExhaustion(100, 99, 100))
			.isEqualTo(new com.sagongsa.backend.domain.budget.BudgetLedger.BudgetExhaustion(true, true));
		assertThat(com.sagongsa.backend.domain.budget.BudgetLedger.budgetExhaustion(100, 100, 110))
			.isEqualTo(new com.sagongsa.backend.domain.budget.BudgetLedger.BudgetExhaustion(true, false));
		assertThat(com.sagongsa.backend.domain.budget.BudgetLedger.budgetExhaustion(0, 0, 110))
			.isEqualTo(new com.sagongsa.backend.domain.budget.BudgetLedger.BudgetExhaustion(false, false));
	}

	@Test
	void reminderIsSevenElapsedDaysAcrossMonthBoundary() {
		assertThat(DecisionPolicy.scheduledRegretReminderAt(Instant.parse("2026-01-31T23:30:00Z")).toInstant())
			.isEqualTo(Instant.parse("2026-02-07T23:30:00Z"));
	}
}
