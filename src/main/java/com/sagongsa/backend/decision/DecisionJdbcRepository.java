package com.sagongsa.backend.decision;

import static com.sagongsa.backend.decision.DecisionData.*;
import static com.sagongsa.backend.decision.DecisionPolicy.*;

import com.sagongsa.backend.decision.DecisionOutcome.Reminder;
import com.sagongsa.backend.domain.enums.MascotState;
import com.sagongsa.backend.domain.enums.PurchaseDecisionResult;
import com.sagongsa.backend.domain.enums.RationalityResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;


@org.springframework.stereotype.Repository
class DecisionJdbcRepository {
	private static final String DEFAULT_ZONE_ID = "Asia/Seoul";
	private final JdbcTemplate jdbcTemplate;

	DecisionJdbcRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	UserContext requireDecisionUser(UUID userId) {
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

	SavedItem lockSavedItem(UUID userId, UUID itemId) {
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

	boolean purchaseOutcomeExists(UUID itemId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists(select 1 from purchase_outcomes where item_id = ?)", Boolean.class, itemId));
	}

	boolean decisionExists(UUID itemId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"select exists(select 1 from purchase_decisions where item_id = ?)",
			Boolean.class,
			itemId
		);
		return Boolean.TRUE.equals(exists);
	}

	UUID findDecisionIdByItemId(UUID itemId) {
		return jdbcTemplate.queryForObject(
			"select id from purchase_decisions where item_id = ?",
			UUID.class,
			itemId
		);
	}


	DecisionForUpdate lockDecision(UUID userId, UUID decisionId) {
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

	Integer similarCategorySpendAmount(UUID userId, String category, ZoneId zoneId, Instant decidedAt) {
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

	void insertDecision(
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

	void insertSelfCheck(UUID decisionId, Rationality rationality, List<NormalizedSelfCheckAnswer> answers, OffsetDateTime now) {
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

	void updateItemStatus(UUID itemId, PurchaseDecisionResult result, OffsetDateTime now) {
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



	void updateDecisionResult(
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

	void updateSelfCheckIfNeeded(
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

	void insertDecisionChangeLog(
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

	MascotReaction updateMascotReaction(
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

	Optional<String> findMascotState(UUID userId) {
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

	Reminder maybeScheduleRegretReminder(
		UUID userId,
		UUID itemId,
		UUID decisionId,
		PurchaseDecisionResult result,
		OffsetDateTime now
	) {
		if (result != PurchaseDecisionResult.GO || !regretReminderEnabled(userId)) {
			return null;
		}

		UUID reminderId = UUID.randomUUID();
		OffsetDateTime scheduledFor = scheduledRegretReminderAt(now.toInstant());
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
		return new Reminder(reminderId, "REGRET_CHECK_7_DAYS", "SCHEDULED", scheduledFor.toInstant());
	}

	void updateReminderForResultChange(
		UUID userId,
		UUID itemId,
		UUID decisionId,
		PurchaseDecisionResult previousResult,
		PurchaseDecisionResult newResult,
		OffsetDateTime now
	) {
		if (previousResult == PurchaseDecisionResult.GO && newResult == PurchaseDecisionResult.STOP) {
			cancelScheduledRegretReminder(decisionId, now);
			return;
		}
		if (previousResult == PurchaseDecisionResult.STOP && newResult == PurchaseDecisionResult.GO && regretReminderEnabled(userId)) {
			upsertScheduledRegretReminder(userId, itemId, decisionId, now);
		}
	}

	void cancelScheduledRegretReminder(UUID decisionId, OffsetDateTime now) {
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

	void upsertScheduledRegretReminder(
		UUID userId,
		UUID itemId,
		UUID decisionId,
		OffsetDateTime now
	) {
		OffsetDateTime scheduledFor = scheduledRegretReminderAt(now.toInstant());
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

	boolean regretReminderEnabled(UUID userId) {
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

	Optional<DecisionResult> findDecisionResult(UUID userId, UUID decisionId) {
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
				bc.monthly_budget_amount as budget_monthly_budget_amount,
				pd.budget_after_amount,
				pd.similar_category_spend_amount,
				pd.self_check_yes_count,
				pd.rationality_result,
				mse.new_state as mascot_state,
				mse.reaction_message as last_reaction_message,
				pd.decided_at,
				case when bc.monthly_budget_amount > 0 and bc.spent_amount >= bc.monthly_budget_amount
				     then true else false end as budget_exhausted
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

	Optional<Reminder> findReminder(UUID decisionId) {
		List<Reminder> reminders = jdbcTemplate.query(
			"""
			select id, reminder_type, status, scheduled_for
			from reminder_schedules
			where decision_id = ?
			  and reminder_type = 'REGRET_CHECK_7_DAYS'
			limit 1
			""",
			(rs, rowNumber) -> new Reminder(
				rs.getObject("id", UUID.class),
				rs.getString("reminder_type"),
				rs.getString("status"),
				readInstant(rs, "scheduled_for")
			),
			decisionId
		);
		return reminders.stream().findFirst();
	}

	UserContext mapUserContext(ResultSet rs, int rowNumber) throws SQLException {
		return new UserContext(
			rs.getString("status"),
			rs.getString("onboarding_status"),
			rs.getString("timezone")
		);
	}

	SavedItem mapSavedItem(ResultSet rs, int rowNumber) throws SQLException {
		return new SavedItem(
			rs.getObject("id", UUID.class),
			rs.getObject("user_id", UUID.class),
			rs.getString("status"),
			rs.getString("title"),
			getInteger(rs, "listed_price"),
			rs.getString("category")
		);
	}


	DecisionForUpdate mapDecisionForUpdate(ResultSet rs, int rowNumber) throws SQLException {
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

	DecisionResult mapDecisionResult(ResultSet rs, int rowNumber) throws SQLException {
		return new DecisionResult(
			rs.getObject("id", UUID.class),
			rs.getObject("item_id", UUID.class),
			rs.getString("item_title"),
			rs.getString("item_status"),
			rs.getString("result"),
			getInteger(rs, "final_price"),
			rs.getString("budget_year_month"),
			getInteger(rs, "budget_monthly_budget_amount"),
			getInteger(rs, "budget_after_amount"),
			getInteger(rs, "similar_category_spend_amount"),
			rs.getInt("self_check_yes_count"),
			rs.getString("rationality_result"),
			Optional.ofNullable(rs.getString("mascot_state")).orElse(MascotState.DEFAULT.name()),
			rs.getString("last_reaction_message"),
			readInstant(rs, "decided_at"),
			rs.getBoolean("budget_exhausted")
		);
	}

	Integer getInteger(ResultSet rs, String columnName) throws SQLException {
		int value = rs.getInt(columnName);
		return rs.wasNull() ? null : value;
	}

	Instant readInstant(ResultSet rs, String columnName) throws SQLException {
		Timestamp value = rs.getTimestamp(columnName);
		return value == null ? null : value.toInstant();
	}
}
