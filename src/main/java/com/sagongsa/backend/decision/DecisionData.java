package com.sagongsa.backend.decision;

import com.sagongsa.backend.domain.enums.MascotState;
import com.sagongsa.backend.domain.enums.PurchaseDecisionResult;
import com.sagongsa.backend.domain.enums.RationalityResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


final class DecisionData {
	private DecisionData() {}

	record NormalizedDecisionRequest(
		UUID itemId,
		PurchaseDecisionResult result,
		Integer finalPrice,
		String rationaleText,
		List<NormalizedSelfCheckAnswer> selfCheckAnswers
	) {
	}

	record NormalizedDecisionUpdateRequest(
		PurchaseDecisionResult result,
		Integer finalPrice,
		String changeReason,
		List<NormalizedSelfCheckAnswer> selfCheckAnswers
	) {
	}

	record NormalizedSelfCheckAnswer(String questionCode, boolean answerBoolean) {
	}

	record UserContext(String status, String onboardingStatus, String timezone) {
	}

	record SavedItem(UUID id, UUID userId, String status, String title, Integer listedPrice, String category) {
	}





	record Rationality(int yesCount, RationalityResult result) {
	}

	record MascotReaction(MascotState state, String message) {
	}

	record DecisionResult(
		UUID id,
		UUID itemId,
		String itemTitle,
		String itemStatus,
		String result,
		Integer finalPrice,
		String budgetYearMonth,
		Integer budgetMonthlyBudgetAmount,
		Integer budgetAfterAmount,
		Integer similarCategorySpendAmount,
		int selfCheckYesCount,
		String rationalityResult,
		String mascotState,
		String mascotMessage,
		Instant decidedAt,
		boolean budgetExhausted
	) {
	}

	record DecisionForUpdate(
		UUID id,
		UUID userId,
		UUID itemId,
		UUID budgetCycleId,
		String result,
		Integer finalPrice,
		String rationalityResult,
		int selfCheckYesCount,
		Integer itemListedPrice
	) {
	}
}
