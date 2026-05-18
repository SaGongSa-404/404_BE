package com.sagongsa.backend.decision;

import com.sagongsa.backend.decision.DecisionCompleteRequest.SelfCheckAnswerRequest;
import com.sagongsa.backend.decision.DecisionResultResponse.MascotReactionResponse;
import com.sagongsa.backend.decision.DecisionResultResponse.ReminderResponse;
import com.sagongsa.backend.domain.enums.MascotState;
import com.sagongsa.backend.domain.enums.PurchaseDecisionResult;
import com.sagongsa.backend.domain.enums.RationalityResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DecisionService {

	private static final String DEFAULT_ZONE_ID = "Asia/Seoul";
	private static final LocalTime REGRET_REMINDER_TIME = LocalTime.of(9, 0);
	private static final int RATIONALE_TEXT_MAX_LENGTH = 1_000;
	private static final int CHANGE_REASON_MAX_LENGTH = 1_000;
	private static final List<String> SELF_CHECK_QUESTION_CODES = List.of("NEED", "BUDGET", "ALTERNATIVE", "DELAY");

	private final JdbcTemplate jdbcTemplate;

	public DecisionService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public DecisionResultResponse complete(UUID userId, DecisionCompleteRequest request) {
		NormalizedDecisionRequest normalized = normalize(request);
		UserContext user = requireDecisionUser(userId);
		SavedItem item = lockSavedItem(userId, normalized.itemId());
		if (!Objects.equals(item.status(), "SAVED")) {
			throw new DecisionConflictException("Only saved wishlist items can be decided.");
		}
		if (decisionExists(item.id())) {
			throw new DecisionConflictException("Purchase decision already exists for this item.");
		}

		ZoneId zoneId = zoneId(user.timezone());
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String yearMonth = YearMonth.now(zoneId).toString();
		BudgetCycle budgetCycle = lockBudgetCycle(userId, yearMonth);
		Integer finalPrice = resolveFinalPrice(normalized.result(), normalized.finalPrice(), item.listedPrice());
		Rationality rationality = rationality(normalized.selfCheckAnswers());
		Integer similarCategorySpendAmount = similarCategorySpendAmount(userId, item.category(), zoneId, now.toInstant());
		int budgetAfterAmount = budgetCycle.spentAmount() + (normalized.result() == PurchaseDecisionResult.GO ? finalPrice : 0);
		UUID decisionId = UUID.randomUUID();

		insertDecision(
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
		insertSelfCheck(decisionId, rationality, normalized.selfCheckAnswers(), now);
		updateItemStatus(item.id(), normalized.result(), now);
		if (normalized.result() == PurchaseDecisionResult.GO) {
			incrementBudgetSpent(budgetCycle.id(), finalPrice, now);
		}

		MascotReaction mascotReaction = updateMascotReaction(userId, item.id(), decisionId, normalized.result(), rationality.result(), now);
		ReminderResponse reminder = maybeScheduleRegretReminder(userId, item.id(), decisionId, normalized.result(), zoneId, now);

		return new DecisionResultResponse(
			decisionId,
			item.id(),
			item.title(),
			normalized.result().name(),
			normalized.result().name(),
			finalPrice,
			yearMonth,
			budgetAfterAmount,
			similarCategorySpendAmount,
			rationality.yesCount(),
			rationality.result().name(),
			resultMessage(normalized.result(), reminder),
			new MascotReactionResponse(mascotReaction.state().name(), mascotReaction.message()),
			reminder,
			now.toInstant()
		);
	}

	@Transactional(readOnly = true)
	public DecisionResultResponse getResult(UUID userId, UUID decisionId) {
		requireDecisionUser(userId);
		DecisionResult decision = findDecisionResult(userId, decisionId)
			.orElseThrow(() -> new DecisionNotFoundException("Purchase decision result was not found."));
		ReminderResponse reminder = findReminder(decisionId).orElse(null);
		MascotReactionResponse mascot = new MascotReactionResponse(decision.mascotState(), decision.mascotMessage());

		return new DecisionResultResponse(
			decision.id(),
			decision.itemId(),
			decision.itemTitle(),
			decision.itemStatus(),
			decision.result(),
			decision.finalPrice(),
			decision.budgetYearMonth(),
			decision.budgetAfterAmount(),
			decision.similarCategorySpendAmount(),
			decision.selfCheckYesCount(),
			decision.rationalityResult(),
			resultMessage(PurchaseDecisionResult.valueOf(decision.result()), reminder),
			mascot,
			reminder,
			decision.decidedAt()
		);
	}

	@Transactional
	public DecisionResultResponse updateResult(UUID userId, UUID decisionId, DecisionResultUpdateRequest request) {
		NormalizedDecisionUpdateRequest normalized = normalizeUpdate(request);
		UserContext user = requireDecisionUser(userId);
		DecisionForUpdate decision = lockDecision(userId, decisionId);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
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
		int budgetAfterAmount = updateBudgetForDecisionChange(decision.budgetCycleId(), budgetDelta, now);

		updateDecisionResult(decision, normalized.result(), newFinalPrice, newRationality, budgetAfterAmount, now);
		updateItemStatus(decision.itemId(), normalized.result(), now);
		updateSelfCheckIfNeeded(decision.id(), normalized.selfCheckAnswers(), newRationality, now);
		insertDecisionChangeLog(decision, normalized, newFinalPrice, newRationality, now);
		updateReminderForResultChange(
			userId,
			decision.itemId(),
			decision.id(),
			previousResult,
			normalized.result(),
			zoneId(user.timezone()),
			now
		);

		return getResult(userId, decisionId);
	}

	private NormalizedDecisionRequest normalize(DecisionCompleteRequest request) {
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

	private NormalizedDecisionUpdateRequest normalizeUpdate(DecisionResultUpdateRequest request) {
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

	private PurchaseDecisionResult parseResult(String rawResult) {
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

	private Integer validateFinalPrice(Integer finalPrice) {
		if (finalPrice != null && finalPrice < 0) {
			throw new DecisionBadRequestException("finalPrice must be zero or greater.");
		}
		return finalPrice;
	}

	private List<NormalizedSelfCheckAnswer> normalizeSelfCheckAnswers(List<SelfCheckAnswerRequest> answers) {
		if (answers == null || answers.size() != 4) {
			throw new DecisionBadRequestException("selfCheckAnswers must contain exactly 4 answers.");
		}

		Map<String, NormalizedSelfCheckAnswer> normalized = new LinkedHashMap<>();
		for (SelfCheckAnswerRequest answer : answers) {
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

	private List<NormalizedSelfCheckAnswer> normalizeSelfCheckAnswerUpdates(
		List<DecisionResultUpdateRequest.SelfCheckAnswerUpdateRequest> answers
	) {
		if (answers.size() != 4) {
			throw new DecisionBadRequestException("selfCheckAnswers must contain exactly 4 answers.");
		}

		Map<String, NormalizedSelfCheckAnswer> normalized = new LinkedHashMap<>();
		for (DecisionResultUpdateRequest.SelfCheckAnswerUpdateRequest answer : answers) {
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

	private UserContext requireDecisionUser(UUID userId) {
		UserContext user;
		try {
			user = jdbcTemplate.queryForObject(
				"""
				select u.status, u.onboarding_status, coalesce(up.timezone, ?) as timezone
				from users u
				left join user_profiles up on up.user_id = u.id
				where u.id = ?
				""",
				this::mapUserContext,
				DEFAULT_ZONE_ID,
				userId
			);
		}
		catch (EmptyResultDataAccessException exception) {
			throw new DecisionNotFoundException("User was not found.");
		}

		if (!Objects.equals(user.status(), "ACTIVE") || !Objects.equals(user.onboardingStatus(), "COMPLETED")) {
			throw new DecisionForbiddenException("Decision can be completed only by active users who completed onboarding.");
		}
		return user;
	}

	private SavedItem lockSavedItem(UUID userId, UUID itemId) {
		try {
			return jdbcTemplate.queryForObject(
				"""
				select id, user_id, status, title, listed_price, category
				from saved_items
				where user_id = ?
				  and id = ?
				for update
				""",
				this::mapSavedItem,
				userId,
				itemId
			);
		}
		catch (EmptyResultDataAccessException exception) {
			throw new DecisionNotFoundException("Saved wishlist item was not found.");
		}
	}

	private boolean decisionExists(UUID itemId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"select exists(select 1 from purchase_decisions where item_id = ?)",
			Boolean.class,
			itemId
		);
		return Boolean.TRUE.equals(exists);
	}

	private BudgetCycle lockBudgetCycle(UUID userId, String yearMonth) {
		try {
			return jdbcTemplate.queryForObject(
				"""
				select id, year_month, spent_amount
				from budget_cycles
				where user_id = ?
				  and year_month = ?
				for update
				""",
				this::mapBudgetCycle,
				userId,
				yearMonth
			);
		}
		catch (EmptyResultDataAccessException exception) {
			throw new DecisionConflictException("Current budget cycle was not found.");
		}
	}

	private DecisionForUpdate lockDecision(UUID userId, UUID decisionId) {
		try {
			return jdbcTemplate.queryForObject(
				"""
				select
					pd.id,
					pd.user_id,
					pd.item_id,
					pd.budget_cycle_id,
					pd.result,
					pd.final_price,
					pd.rationality_result,
					pd.self_check_yes_count,
					si.listed_price as item_listed_price
				from purchase_decisions pd
				join saved_items si on si.id = pd.item_id
				where pd.user_id = ?
				  and pd.id = ?
				for update of pd, si
				""",
				this::mapDecisionForUpdate,
				userId,
				decisionId
			);
		}
		catch (EmptyResultDataAccessException exception) {
			throw new DecisionNotFoundException("Purchase decision result was not found.");
		}
	}

	private Integer resolveFinalPrice(PurchaseDecisionResult result, Integer requestedFinalPrice, Integer listedPrice) {
		if (result == PurchaseDecisionResult.STOP) {
			return null;
		}
		Integer finalPrice = requestedFinalPrice == null ? listedPrice : requestedFinalPrice;
		if (result == PurchaseDecisionResult.GO && finalPrice == null) {
			throw new DecisionBadRequestException("finalPrice is required for GO when item price is missing.");
		}
		return finalPrice;
	}

	private Integer resolveUpdatedFinalPrice(
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

	private Rationality rationality(List<NormalizedSelfCheckAnswer> answers) {
		int yesCount = (int) answers.stream()
			.filter(NormalizedSelfCheckAnswer::answerBoolean)
			.count();
		RationalityResult result = yesCount <= 1 ? RationalityResult.RATIONAL : RationalityResult.IRRATIONAL;
		return new Rationality(yesCount, result);
	}

	private Integer similarCategorySpendAmount(UUID userId, String category, ZoneId zoneId, Instant decidedAt) {
		YearMonth yearMonth = YearMonth.from(ZonedDateTime.ofInstant(decidedAt, zoneId));
		OffsetDateTime start = toUtc(yearMonth.atDay(1).atStartOfDay(zoneId));
		OffsetDateTime end = toUtc(yearMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId));
		Integer amount = jdbcTemplate.queryForObject(
			"""
			select coalesce(sum(coalesce(pd.final_price, 0)), 0)::integer
			from purchase_decisions pd
			join saved_items si on si.id = pd.item_id
			where pd.user_id = ?
			  and pd.result = 'GO'
			  and si.category = ?
			  and pd.decided_at >= ?
			  and pd.decided_at < ?
			""",
			Integer.class,
			userId,
			category,
			start,
			end
		);
		return amount == null ? 0 : amount;
	}

	private void insertDecision(
		UUID decisionId,
		UUID userId,
		SavedItem item,
		UUID budgetCycleId,
		PurchaseDecisionResult result,
		Integer finalPrice,
		int budgetAfterAmount,
		int similarCategorySpendAmount,
		Rationality rationality,
		String rationaleText,
		OffsetDateTime now
	) {
		try {
			jdbcTemplate.update(
				"""
				insert into purchase_decisions (
					id, user_id, item_id, budget_cycle_id, result, final_price,
					budget_after_amount, similar_category_spend_amount, rationality_result,
					self_check_yes_count, rationale_text, decided_at, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				decisionId,
				userId,
				item.id(),
				budgetCycleId,
				result.name(),
				finalPrice,
				budgetAfterAmount,
				similarCategorySpendAmount,
				rationality.result().name(),
				rationality.yesCount(),
				rationaleText,
				now,
				now,
				now
			);
		}
		catch (DataIntegrityViolationException exception) {
			throw new DecisionConflictException("Purchase decision already exists for this item.");
		}
	}

	private void insertSelfCheck(UUID decisionId, Rationality rationality, List<NormalizedSelfCheckAnswer> answers, OffsetDateTime now) {
		UUID responseSetId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into self_check_response_sets (
				id, decision_id, yes_count, rationality_result, submitted_at, created_at, updated_at
			)
			values (?, ?, ?, ?, ?, ?, ?)
			""",
			responseSetId,
			decisionId,
			rationality.yesCount(),
			rationality.result().name(),
			now,
			now,
			now
		);

		for (NormalizedSelfCheckAnswer answer : answers) {
			jdbcTemplate.update(
				"""
				insert into self_check_answers (
					id, response_set_id, question_code, answer_boolean, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?)
				""",
				UUID.randomUUID(),
				responseSetId,
				answer.questionCode(),
				answer.answerBoolean(),
				now,
				now
			);
		}
	}

	private void updateItemStatus(UUID itemId, PurchaseDecisionResult result, OffsetDateTime now) {
		jdbcTemplate.update(
			"""
			update saved_items
			set status = ?,
				updated_at = ?
			where id = ?
			""",
			result.name(),
			now,
			itemId
		);
	}

	private void incrementBudgetSpent(UUID budgetCycleId, int amount, OffsetDateTime now) {
		jdbcTemplate.update(
			"""
			update budget_cycles
			set spent_amount = spent_amount + ?,
				updated_at = ?
			where id = ?
			""",
			amount,
			now,
			budgetCycleId
		);
	}

	private int updateBudgetForDecisionChange(UUID budgetCycleId, int delta, OffsetDateTime now) {
		if (budgetCycleId == null) {
			return 0;
		}
		BudgetSpent budget = jdbcTemplate.queryForObject(
			"""
			update budget_cycles
			set spent_amount = greatest(spent_amount + ?, 0),
				updated_at = ?
			where id = ?
			returning spent_amount
			""",
			(rs, rowNumber) -> new BudgetSpent(rs.getInt("spent_amount")),
			delta,
			now,
			budgetCycleId
		);
		return budget == null ? 0 : budget.spentAmount();
	}

	private void updateDecisionResult(
		DecisionForUpdate decision,
		PurchaseDecisionResult result,
		Integer finalPrice,
		Rationality rationality,
		int budgetAfterAmount,
		OffsetDateTime now
	) {
		jdbcTemplate.update(
			"""
			update purchase_decisions
			set result = ?,
				final_price = ?,
				budget_after_amount = ?,
				rationality_result = ?,
				self_check_yes_count = ?,
				is_changed = true,
				change_count = change_count + 1,
				changed_at = ?,
				updated_at = ?
			where id = ?
			""",
			result.name(),
			finalPrice,
			budgetAfterAmount,
			rationality.result().name(),
			rationality.yesCount(),
			now,
			now,
			decision.id()
		);
	}

	private void updateSelfCheckIfNeeded(
		UUID decisionId,
		List<NormalizedSelfCheckAnswer> answers,
		Rationality rationality,
		OffsetDateTime now
	) {
		if (answers == null) {
			return;
		}

		UUID responseSetId = jdbcTemplate.queryForObject(
			"select id from self_check_response_sets where decision_id = ?",
			UUID.class,
			decisionId
		);
		jdbcTemplate.update(
			"""
			update self_check_response_sets
			set yes_count = ?,
				rationality_result = ?,
				submitted_at = ?,
				updated_at = ?
			where id = ?
			""",
			rationality.yesCount(),
			rationality.result().name(),
			now,
			now,
			responseSetId
		);
		jdbcTemplate.update("delete from self_check_answers where response_set_id = ?", responseSetId);
		for (NormalizedSelfCheckAnswer answer : answers) {
			jdbcTemplate.update(
				"""
				insert into self_check_answers (
					id, response_set_id, question_code, answer_boolean, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?)
				""",
				UUID.randomUUID(),
				responseSetId,
				answer.questionCode(),
				answer.answerBoolean(),
				now,
				now
			);
		}
	}

	private void insertDecisionChangeLog(
		DecisionForUpdate decision,
		NormalizedDecisionUpdateRequest normalized,
		Integer newFinalPrice,
		Rationality newRationality,
		OffsetDateTime now
	) {
		jdbcTemplate.update(
			"""
			insert into purchase_decision_change_logs (
				id, decision_id, user_id, item_id, previous_result, new_result,
				previous_final_price, new_final_price,
				previous_rationality_result, new_rationality_result,
				previous_self_check_yes_count, new_self_check_yes_count,
				reason_text, changed_at
			)
			values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			decision.id(),
			decision.userId(),
			decision.itemId(),
			decision.result(),
			normalized.result().name(),
			decision.finalPrice(),
			newFinalPrice,
			decision.rationalityResult(),
			newRationality.result().name(),
			decision.selfCheckYesCount(),
			newRationality.yesCount(),
			normalized.changeReason(),
			now
		);
	}

	private MascotReaction updateMascotReaction(
		UUID userId,
		UUID itemId,
		UUID decisionId,
		PurchaseDecisionResult result,
		RationalityResult rationalityResult,
		OffsetDateTime now
	) {
		MascotReaction reaction = mascotReaction(result, rationalityResult);
		String previousState = findMascotState(userId).orElse(MascotState.DEFAULT.name());
		jdbcTemplate.update(
			"""
			insert into mascot_profiles (
				user_id, mascot_state, last_reaction_message, last_state_changed_at, reaction_expires_at, updated_at
			)
			values (?, ?, ?, ?, null, ?)
			on conflict (user_id) do update
			   set mascot_state = excluded.mascot_state,
			       last_reaction_message = excluded.last_reaction_message,
			       last_state_changed_at = excluded.last_state_changed_at,
			       reaction_expires_at = null,
			       updated_at = excluded.updated_at
			""",
			userId,
			reaction.state().name(),
			reaction.message(),
			now,
			now
		);
		jdbcTemplate.update(
			"""
			insert into mascot_state_events (
				id, user_id, item_id, decision_id, event_type, previous_state,
				new_state, reaction_message, created_at, updated_at
			)
			values (?, ?, ?, ?, 'DECISION_REACTION', ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			itemId,
			decisionId,
			previousState,
			reaction.state().name(),
			reaction.message(),
			now,
			now
		);
		return reaction;
	}

	private Optional<String> findMascotState(UUID userId) {
		try {
			return Optional.ofNullable(jdbcTemplate.queryForObject(
				"select mascot_state from mascot_profiles where user_id = ?",
				String.class,
				userId
			));
		}
		catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	private MascotReaction mascotReaction(PurchaseDecisionResult result, RationalityResult rationalityResult) {
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

	private ReminderResponse maybeScheduleRegretReminder(
		UUID userId,
		UUID itemId,
		UUID decisionId,
		PurchaseDecisionResult result,
		ZoneId zoneId,
		OffsetDateTime now
	) {
		if (result != PurchaseDecisionResult.GO || !regretReminderEnabled(userId)) {
			return null;
		}

		UUID reminderId = UUID.randomUUID();
		OffsetDateTime scheduledFor = scheduledRegretReminderAt(now.toInstant(), zoneId);
		jdbcTemplate.update(
			"""
			insert into reminder_schedules (
				id, user_id, item_id, decision_id, reminder_type, scheduled_for,
				status, created_at, updated_at
			)
			values (?, ?, ?, ?, 'REGRET_CHECK_7_DAYS', ?, 'SCHEDULED', ?, ?)
			""",
			reminderId,
			userId,
			itemId,
			decisionId,
			scheduledFor,
			now,
			now
		);
		return new ReminderResponse(reminderId, "REGRET_CHECK_7_DAYS", "SCHEDULED", scheduledFor.toInstant());
	}

	private void updateReminderForResultChange(
		UUID userId,
		UUID itemId,
		UUID decisionId,
		PurchaseDecisionResult previousResult,
		PurchaseDecisionResult newResult,
		ZoneId zoneId,
		OffsetDateTime now
	) {
		if (previousResult == PurchaseDecisionResult.GO && newResult == PurchaseDecisionResult.STOP) {
			cancelScheduledRegretReminder(decisionId, now);
			return;
		}
		if (previousResult == PurchaseDecisionResult.STOP && newResult == PurchaseDecisionResult.GO && regretReminderEnabled(userId)) {
			upsertScheduledRegretReminder(userId, itemId, decisionId, zoneId, now);
		}
	}

	private void cancelScheduledRegretReminder(UUID decisionId, OffsetDateTime now) {
		jdbcTemplate.update(
			"""
			update reminder_schedules
			set status = 'CANCELED',
				canceled_at = ?,
				cancel_reason = 'DECISION_CHANGED_TO_STOP',
				updated_at = ?
			where decision_id = ?
			  and reminder_type = 'REGRET_CHECK_7_DAYS'
			  and status = 'SCHEDULED'
			""",
			now,
			now,
			decisionId
		);
	}

	private void upsertScheduledRegretReminder(
		UUID userId,
		UUID itemId,
		UUID decisionId,
		ZoneId zoneId,
		OffsetDateTime now
	) {
		OffsetDateTime scheduledFor = scheduledRegretReminderAt(now.toInstant(), zoneId);
		jdbcTemplate.update(
			"""
			insert into reminder_schedules (
				id, user_id, item_id, decision_id, reminder_type, scheduled_for,
				status, sent_at, canceled_at, cancel_reason, created_at, updated_at
			)
			values (?, ?, ?, ?, 'REGRET_CHECK_7_DAYS', ?, 'SCHEDULED', null, null, null, ?, ?)
			on conflict (decision_id, reminder_type) do update
			   set scheduled_for = excluded.scheduled_for,
			       status = 'SCHEDULED',
			       sent_at = null,
			       canceled_at = null,
			       cancel_reason = null,
			       updated_at = excluded.updated_at
			 where reminder_schedules.status <> 'SENT'
			""",
			UUID.randomUUID(),
			userId,
			itemId,
			decisionId,
			scheduledFor,
			now,
			now
		);
	}

	private boolean regretReminderEnabled(UUID userId) {
		try {
			Boolean enabled = jdbcTemplate.queryForObject(
				"select regret_reminder_enabled from user_notification_settings where user_id = ?",
				Boolean.class,
				userId
			);
			return !Boolean.FALSE.equals(enabled);
		}
		catch (EmptyResultDataAccessException exception) {
			return true;
		}
	}

	private OffsetDateTime scheduledRegretReminderAt(Instant decidedAt, ZoneId zoneId) {
		ZonedDateTime scheduledLocal = ZonedDateTime.ofInstant(decidedAt, zoneId)
			.toLocalDate()
			.plusDays(7)
			.atTime(REGRET_REMINDER_TIME)
			.atZone(zoneId);
		return toUtc(scheduledLocal);
	}

	private Optional<DecisionResult> findDecisionResult(UUID userId, UUID decisionId) {
		List<DecisionResult> results = jdbcTemplate.query(
			"""
			select
				pd.id,
				pd.item_id,
				si.title as item_title,
				si.status as item_status,
				pd.result,
				pd.final_price,
				bc.year_month as budget_year_month,
				pd.budget_after_amount,
				pd.similar_category_spend_amount,
				pd.self_check_yes_count,
				pd.rationality_result,
				mse.new_state as mascot_state,
				mse.reaction_message as last_reaction_message,
				pd.decided_at
			from purchase_decisions pd
			join saved_items si on si.id = pd.item_id
			left join budget_cycles bc on bc.id = pd.budget_cycle_id
			left join mascot_state_events mse on mse.decision_id = pd.id
			 and mse.event_type = 'DECISION_REACTION'
			where pd.user_id = ?
			  and pd.id = ?
			""",
			this::mapDecisionResult,
			userId,
			decisionId
		);
		return results.stream().findFirst();
	}

	private Optional<ReminderResponse> findReminder(UUID decisionId) {
		List<ReminderResponse> reminders = jdbcTemplate.query(
			"""
			select id, reminder_type, status, scheduled_for
			from reminder_schedules
			where decision_id = ?
			  and reminder_type = 'REGRET_CHECK_7_DAYS'
			limit 1
			""",
			(rs, rowNumber) -> new ReminderResponse(
				rs.getObject("id", UUID.class),
				rs.getString("reminder_type"),
				rs.getString("status"),
				readInstant(rs, "scheduled_for")
			),
			decisionId
		);
		return reminders.stream().findFirst();
	}

	private String resultMessage(PurchaseDecisionResult result, ReminderResponse reminder) {
		if (result == PurchaseDecisionResult.GO) {
			return reminder == null ? "구매 결정이 저장됐어요." : "7일 뒤에 어떤지 물어볼게!";
		}
		return "위시리스트에서 정리했어요.";
	}

	private String cleanRequired(String value, String fieldName, int maxLength) {
		String cleaned = cleanOptional(value, fieldName);
		if (cleaned == null) {
			throw new DecisionBadRequestException(fieldName + " is required.");
		}
		if (cleaned.length() > maxLength) {
			throw new DecisionBadRequestException(fieldName + " must be " + maxLength + " characters or fewer.");
		}
		return cleaned;
	}

	private String cleanOptional(String value, String fieldName) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	private String cleanOptional(String value, String fieldName, int maxLength) {
		String cleaned = cleanOptional(value, fieldName);
		if (cleaned != null && cleaned.length() > maxLength) {
			throw new DecisionBadRequestException(fieldName + " must be " + maxLength + " characters or fewer.");
		}
		return cleaned;
	}

	private ZoneId zoneId(String rawTimezone) {
		try {
			return ZoneId.of(StringUtils.hasText(rawTimezone) ? rawTimezone : DEFAULT_ZONE_ID);
		}
		catch (RuntimeException exception) {
			return ZoneId.of(DEFAULT_ZONE_ID);
		}
	}

	private OffsetDateTime toUtc(ZonedDateTime zonedDateTime) {
		return OffsetDateTime.ofInstant(zonedDateTime.toInstant(), ZoneOffset.UTC);
	}

	private UserContext mapUserContext(ResultSet rs, int rowNumber) throws SQLException {
		return new UserContext(
			rs.getString("status"),
			rs.getString("onboarding_status"),
			rs.getString("timezone")
		);
	}

	private SavedItem mapSavedItem(ResultSet rs, int rowNumber) throws SQLException {
		return new SavedItem(
			rs.getObject("id", UUID.class),
			rs.getObject("user_id", UUID.class),
			rs.getString("status"),
			rs.getString("title"),
			getInteger(rs, "listed_price"),
			rs.getString("category")
		);
	}

	private BudgetCycle mapBudgetCycle(ResultSet rs, int rowNumber) throws SQLException {
		return new BudgetCycle(
			rs.getObject("id", UUID.class),
			rs.getString("year_month"),
			rs.getInt("spent_amount")
		);
	}

	private DecisionForUpdate mapDecisionForUpdate(ResultSet rs, int rowNumber) throws SQLException {
		return new DecisionForUpdate(
			rs.getObject("id", UUID.class),
			rs.getObject("user_id", UUID.class),
			rs.getObject("item_id", UUID.class),
			rs.getObject("budget_cycle_id", UUID.class),
			rs.getString("result"),
			getInteger(rs, "final_price"),
			rs.getString("rationality_result"),
			rs.getInt("self_check_yes_count"),
			getInteger(rs, "item_listed_price")
		);
	}

	private DecisionResult mapDecisionResult(ResultSet rs, int rowNumber) throws SQLException {
		return new DecisionResult(
			rs.getObject("id", UUID.class),
			rs.getObject("item_id", UUID.class),
			rs.getString("item_title"),
			rs.getString("item_status"),
			rs.getString("result"),
			getInteger(rs, "final_price"),
			rs.getString("budget_year_month"),
			getInteger(rs, "budget_after_amount"),
			getInteger(rs, "similar_category_spend_amount"),
			rs.getInt("self_check_yes_count"),
			rs.getString("rationality_result"),
			Optional.ofNullable(rs.getString("mascot_state")).orElse(MascotState.DEFAULT.name()),
			rs.getString("last_reaction_message"),
			readInstant(rs, "decided_at")
		);
	}

	private Integer getInteger(ResultSet rs, String columnName) throws SQLException {
		int value = rs.getInt(columnName);
		return rs.wasNull() ? null : value;
	}

	private Instant readInstant(ResultSet rs, String columnName) throws SQLException {
		Timestamp value = rs.getTimestamp(columnName);
		return value == null ? null : value.toInstant();
	}

	private record NormalizedDecisionRequest(
		UUID itemId,
		PurchaseDecisionResult result,
		Integer finalPrice,
		String rationaleText,
		List<NormalizedSelfCheckAnswer> selfCheckAnswers
	) {
	}

	private record NormalizedDecisionUpdateRequest(
		PurchaseDecisionResult result,
		Integer finalPrice,
		String changeReason,
		List<NormalizedSelfCheckAnswer> selfCheckAnswers
	) {
	}

	private record NormalizedSelfCheckAnswer(String questionCode, boolean answerBoolean) {
	}

	private record UserContext(String status, String onboardingStatus, String timezone) {
	}

	private record SavedItem(UUID id, UUID userId, String status, String title, Integer listedPrice, String category) {
	}

	private record BudgetCycle(UUID id, String yearMonth, int spentAmount) {
	}

	private record BudgetSpent(int spentAmount) {
	}

	private record Rationality(int yesCount, RationalityResult result) {
	}

	private record MascotReaction(MascotState state, String message) {
	}

	private record DecisionResult(
		UUID id,
		UUID itemId,
		String itemTitle,
		String itemStatus,
		String result,
		Integer finalPrice,
		String budgetYearMonth,
		Integer budgetAfterAmount,
		Integer similarCategorySpendAmount,
		int selfCheckYesCount,
		String rationalityResult,
		String mascotState,
		String mascotMessage,
		Instant decidedAt
	) {
	}

	private record DecisionForUpdate(
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
