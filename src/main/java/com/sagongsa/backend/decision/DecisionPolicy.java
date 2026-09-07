package com.sagongsa.backend.decision;

import com.sagongsa.backend.domain.budget.BudgetLedger;
import static com.sagongsa.backend.domain.budget.BudgetLedger.*;

import static com.sagongsa.backend.decision.DecisionData.*;

import com.sagongsa.backend.decision.CompleteDecisionCommand.SelfCheckAnswer;
import com.sagongsa.backend.decision.DecisionOutcome.Reminder;
import com.sagongsa.backend.domain.enums.MascotState;
import com.sagongsa.backend.domain.enums.PurchaseDecisionResult;
import com.sagongsa.backend.domain.enums.RationalityResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;


final class DecisionPolicy {
	private static final String DEFAULT_ZONE_ID = "Asia/Seoul";
	private static final int RATIONALE_TEXT_MAX_LENGTH = 1_000;
	private static final int CHANGE_REASON_MAX_LENGTH = 1_000;
	private static final List<String> SELF_CHECK_QUESTION_CODES = List.of("NEED", "BUDGET", "ALTERNATIVE", "DELAY");

	private DecisionPolicy() {}

	static NormalizedDecisionRequest normalize(CompleteDecisionCommand request) {
		if (request == null) {
			throw new DecisionBadRequestException("Request body is required.");
		}
		if (request.itemId() == null) {
			throw new DecisionBadRequestException("itemId is required.");
		}

		PurchaseDecisionResult result = parseResult(request.result());
		Integer finalPrice = validateFinalPrice(request.finalPrice());
		String rationaleText = cleanOptional(request.rationaleText(), "rationaleText", RATIONALE_TEXT_MAX_LENGTH);
		List<NormalizedSelfCheckAnswer> answers = normalizeSelfCheckAnswers(request.selfCheckAnswers());
		return new NormalizedDecisionRequest(request.itemId(), result, finalPrice, rationaleText, answers);
	}

	static NormalizedDecisionUpdateRequest normalizeUpdate(ChangeDecisionCommand request) {
		if (request == null) {
			throw new DecisionBadRequestException("Request body is required.");
		}
		PurchaseDecisionResult result = parseResult(request.result());
		Integer finalPrice = validateFinalPrice(request.finalPrice());
		String changeReason = cleanOptional(request.changeReason(), "changeReason", CHANGE_REASON_MAX_LENGTH);
		List<NormalizedSelfCheckAnswer> answers = request.selfCheckAnswers() == null
			? null
			: normalizeSelfCheckAnswerUpdates(request.selfCheckAnswers());
		return new NormalizedDecisionUpdateRequest(result, finalPrice, changeReason, answers);
	}

	static PurchaseDecisionResult parseResult(String rawResult) {
		if (!StringUtils.hasText(rawResult)) {
			throw new DecisionBadRequestException("result is required.");
		}
		try {
			return PurchaseDecisionResult.valueOf(rawResult.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException exception) {
			throw new DecisionBadRequestException("result must be GO or STOP.");
		}
	}

	static Integer validateFinalPrice(Integer finalPrice) {
		if (finalPrice != null && finalPrice < 0) {
			throw new DecisionBadRequestException("finalPrice must be zero or greater.");
		}
		return finalPrice;
	}

	static List<NormalizedSelfCheckAnswer> normalizeSelfCheckAnswers(List<SelfCheckAnswer> answers) {
		if (answers == null || answers.size() != 4) {
			throw new DecisionBadRequestException("selfCheckAnswers must contain exactly 4 answers.");
		}

		Map<String, NormalizedSelfCheckAnswer> normalized = new LinkedHashMap<>();
		for (SelfCheckAnswer answer : answers) {
			if (answer == null) {
				throw new DecisionBadRequestException("selfCheckAnswers must not contain null.");
			}
			String questionCode = cleanRequired(answer.questionCode(), "questionCode", 80);
			if (!SELF_CHECK_QUESTION_CODES.contains(questionCode)) {
				throw new DecisionBadRequestException("questionCode must be one of NEED, BUDGET, ALTERNATIVE, DELAY.");
			}
			if (answer.answerBoolean() == null) {
				throw new DecisionBadRequestException("answerBoolean is required.");
			}
			if (normalized.putIfAbsent(questionCode, new NormalizedSelfCheckAnswer(questionCode, answer.answerBoolean())) != null) {
				throw new DecisionBadRequestException("selfCheckAnswers must not contain duplicated questionCode.");
			}
		}
		return List.copyOf(normalized.values());
	}

	static List<NormalizedSelfCheckAnswer> normalizeSelfCheckAnswerUpdates(
		List<ChangeDecisionCommand.SelfCheckAnswer> answers
	) {
		if (answers.size() != 4) {
			throw new DecisionBadRequestException("selfCheckAnswers must contain exactly 4 answers.");
		}

		Map<String, NormalizedSelfCheckAnswer> normalized = new LinkedHashMap<>();
		for (ChangeDecisionCommand.SelfCheckAnswer answer : answers) {
			if (answer == null) {
				throw new DecisionBadRequestException("selfCheckAnswers must not contain null.");
			}
			String questionCode = cleanRequired(answer.questionCode(), "questionCode", 80);
			if (!SELF_CHECK_QUESTION_CODES.contains(questionCode)) {
				throw new DecisionBadRequestException("questionCode must be one of NEED, BUDGET, ALTERNATIVE, DELAY.");
			}
			if (answer.answerBoolean() == null) {
				throw new DecisionBadRequestException("answerBoolean is required.");
			}
			if (normalized.putIfAbsent(questionCode, new NormalizedSelfCheckAnswer(questionCode, answer.answerBoolean())) != null) {
				throw new DecisionBadRequestException("selfCheckAnswers must not contain duplicated questionCode.");
			}
		}
		return List.copyOf(normalized.values());
	}

	static Integer resolveFinalPrice(PurchaseDecisionResult result, Integer requestedFinalPrice, Integer listedPrice) {
		if (result == PurchaseDecisionResult.STOP) {
			return null;
		}
		Integer finalPrice = requestedFinalPrice == null ? listedPrice : requestedFinalPrice;
		if (result == PurchaseDecisionResult.GO && finalPrice == null) {
			throw new DecisionBadRequestException("finalPrice is required for GO when item price is missing.");
		}
		return finalPrice;
	}

	static Integer resolveUpdatedFinalPrice(
		PurchaseDecisionResult result,
		Integer requestedFinalPrice,
		Integer previousFinalPrice,
		Integer listedPrice
	) {
		if (result == PurchaseDecisionResult.STOP) {
			return null;
		}
		Integer finalPrice = requestedFinalPrice;
		if (finalPrice == null) {
			finalPrice = previousFinalPrice == null ? listedPrice : previousFinalPrice;
		}
		if (result == PurchaseDecisionResult.GO && finalPrice == null) {
			throw new DecisionBadRequestException("finalPrice is required for GO when item price is missing.");
		}
		return finalPrice;
	}

	static Rationality rationality(List<NormalizedSelfCheckAnswer> answers) {
		int yesCount = (int) answers.stream()
			.filter(NormalizedSelfCheckAnswer::answerBoolean)
			.count();
		RationalityResult result = yesCount <= 1 ? RationalityResult.RATIONAL : RationalityResult.IRRATIONAL;
		return new Rationality(yesCount, result);
	}

	static BudgetExhaustion budgetExhaustion(DecisionResult decision) {
		if (decision.budgetMonthlyBudgetAmount() == null || decision.budgetAfterAmount() == null) {
			return new BudgetExhaustion(false, false);
		}
		int goAmount = PurchaseDecisionResult.GO.name().equals(decision.result())
			? Objects.requireNonNullElse(decision.finalPrice(), 0)
			: 0;
		int budgetBeforeAmount = decision.budgetAfterAmount() - goAmount;
		return BudgetLedger.budgetExhaustion(decision.budgetMonthlyBudgetAmount(), budgetBeforeAmount, decision.budgetAfterAmount());
	}



	static MascotReaction mascotReaction(PurchaseDecisionResult result, RationalityResult rationalityResult) {
		if (result == PurchaseDecisionResult.GO && rationalityResult == RationalityResult.IRRATIONAL) {
			return new MascotReaction(MascotState.SAD, "비합리적인데 그냥 샀어요");
		}
		if (result == PurchaseDecisionResult.STOP && rationalityResult == RationalityResult.IRRATIONAL) {
			return new MascotReaction(MascotState.VERY_HAPPY, "비합리적인 걸 알고도 잘 참았어요");
		}
		if (result == PurchaseDecisionResult.STOP) {
			return new MascotReaction(MascotState.SMILE, "합리적으로 잘 참았어요");
		}
		return new MascotReaction(MascotState.SMILE, "합리적으로 잘 결정했어요");
	}

	static OffsetDateTime scheduledRegretReminderAt(Instant decidedAt) {
		return OffsetDateTime.ofInstant(decidedAt.plus(java.time.Duration.ofDays(7)), ZoneOffset.UTC);
	}

	static String resultMessage(PurchaseDecisionResult result, Reminder reminder) {
		if (result == PurchaseDecisionResult.GO) {
			return reminder == null ? "구매 결정이 저장됐어요." : "7일 뒤에 어떤지 물어볼게!";
		}
		return "위시리스트에서 정리했어요.";
	}

	static String cleanRequired(String value, String fieldName, int maxLength) {
		String cleaned = cleanOptional(value, fieldName);
		if (cleaned == null) {
			throw new DecisionBadRequestException(fieldName + " is required.");
		}
		if (cleaned.length() > maxLength) {
			throw new DecisionBadRequestException(fieldName + " must be " + maxLength + " characters or fewer.");
		}
		return cleaned;
	}

	static String cleanOptional(String value, String fieldName) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	static String cleanOptional(String value, String fieldName, int maxLength) {
		String cleaned = cleanOptional(value, fieldName);
		if (cleaned != null && cleaned.length() > maxLength) {
			throw new DecisionBadRequestException(fieldName + " must be " + maxLength + " characters or fewer.");
		}
		return cleaned;
	}

	static ZoneId zoneId(String rawTimezone) {
		try {
			return ZoneId.of(StringUtils.hasText(rawTimezone) ? rawTimezone : DEFAULT_ZONE_ID);
		}
		catch (RuntimeException exception) {
			return ZoneId.of(DEFAULT_ZONE_ID);
		}
	}

	static OffsetDateTime toUtc(ZonedDateTime zonedDateTime) {
		return OffsetDateTime.ofInstant(zonedDateTime.toInstant(), ZoneOffset.UTC);
	}
}
