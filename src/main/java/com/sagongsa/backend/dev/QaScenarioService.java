package com.sagongsa.backend.dev;

import com.sagongsa.backend.notification.ReminderNotificationWorker;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QaScenarioService {

	private static final String QA_NICKNAME_PREFIX = "QA너굴";
	private static final String QA_PROVIDER_USER_ID_PREFIX = "DEV_QA:";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final List<String> SELF_CHECK_CODES = List.of("NEED", "BUDGET", "ALTERNATIVE", "DELAY");

	private final JdbcTemplate jdbcTemplate;
	private final ReminderNotificationWorker reminderNotificationWorker;

	public QaScenarioService(JdbcTemplate jdbcTemplate, ReminderNotificationWorker reminderNotificationWorker) {
		this.jdbcTemplate = jdbcTemplate;
		this.reminderNotificationWorker = reminderNotificationWorker;
	}

	@Transactional
	public QaUserScenarioResponse createQaUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String yearMonth = currentYearMonth();

		jdbcTemplate.update(
			"insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId,
			now,
			now
		);
		jdbcTemplate.update(
			"""
			insert into social_accounts (
				id, user_id, provider, provider_user_id, email, profile_image_url, created_at, updated_at
			)
			values (?, ?, 'KAKAO', ?, null, null, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			QA_PROVIDER_USER_ID_PREFIX + userId,
			now,
			now
		);

		Long seq = jdbcTemplate.queryForObject("select nextval('user_nickname_seq')", Long.class);
		String nickname = QA_NICKNAME_PREFIX + seq;
		jdbcTemplate.update(
			"""
			insert into user_profiles (
				user_id, nickname, mascot_name, timezone, profile_image_url, notification_enabled, created_at, updated_at
			)
			values (?, ?, '너구리', 'Asia/Seoul', null, true, ?, ?)
			""",
			userId,
			nickname,
			now,
			now
		);

		jdbcTemplate.update(
			"""
			insert into user_notification_settings (
				user_id, push_enabled, regret_reminder_enabled, wishlist_reminder_enabled,
				budget_warning_enabled, social_vote_enabled, updated_at
			)
			values (?, true, true, false, false, false, ?)
			""",
			userId,
			now
		);

		jdbcTemplate.update(
			"""
			insert into mascot_profiles (
				user_id, mascot_state, last_reaction_message, last_state_changed_at, reaction_expires_at, updated_at
			)
			values (?, 'DEFAULT', null, ?, null, ?)
			""",
			userId,
			now,
			now
		);

		UUID budgetCycleId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into budget_cycles (
				id, user_id, year_month, monthly_budget_amount, spent_amount,
				warning_threshold_rate, created_at, updated_at
			)
			values (?, ?, ?, 500000, 0, 80.00, ?, ?)
			""",
			budgetCycleId,
			userId,
			yearMonth,
			now,
			now
		);

		return new QaUserScenarioResponse(
			userId,
			nickname,
			budgetCycleId,
			yearMonth,
			paths(userId)
		);
	}

	@Transactional
	public QaBasicScenarioResponse createBasicScenario() {
		QaUserScenarioResponse user = createQaUser();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		UUID decidedItemId = insertSavedItem(
			user.userId(),
			"QA 기본팩 무선 이어폰",
			120000,
			"DIGITAL",
			"GO",
			now
		);
		UUID decisionId = insertDecision(
			user.userId(), decidedItemId, user.budgetCycleId(), "GO", 120000, 120000, "RATIONAL", 1, now
		);
		UUID deliberationItemId = insertSavedItem(
			user.userId(),
			"QA 숙려 진입용 키보드",
			45000,
			"DIGITAL",
			"SAVED",
			now.minusHours(25)
		);
		UUID notificationId = insertUnreadNotification(user.userId(), now);

		jdbcTemplate.update(
			"""
			update budget_cycles
			   set monthly_budget_amount = 100000,
			       spent_amount = 120000,
			       updated_at = ?
			 where id = ?
			""",
			now,
			user.budgetCycleId()
		);

		Map<String, String> paths = new LinkedHashMap<>(user.paths());
		paths.put("deliberation", "/api/v1/deliberations/items/" + deliberationItemId);
		paths.put("consumption", "/api/v1/my/consumption?month=" + user.yearMonth());

		return new QaBasicScenarioResponse(
			user.userId(),
			user.nickname(),
			user.budgetCycleId(),
			user.yearMonth(),
			List.of(deliberationItemId, decidedItemId),
			List.of(decisionId),
			deliberationItemId,
			List.of(notificationId),
			paths
		);
	}

	@Transactional
	public QaDecisionScenarioResponse createResultCombinationsScenario() {
		QaUserScenarioResponse user = createQaUser();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		Map<String, UUID> decisionIds = new LinkedHashMap<>();
		List<UUID> itemIds = new ArrayList<>();
		int budgetAfter = 0;

		budgetAfter = insertDecisionCase(
			user, itemIds, decisionIds, "goRational", "QA GO 합리 결과", "GO", 25000, budgetAfter, "RATIONAL", 1, now
		);
		budgetAfter = insertDecisionCase(
			user, itemIds, decisionIds, "goIrrational", "QA GO 비합리 결과", "GO", 70000, budgetAfter, "IRRATIONAL", 3, now.minusMinutes(1)
		);
		budgetAfter = insertDecisionCase(
			user, itemIds, decisionIds, "stopRational", "QA STOP 합리 결과", "STOP", 35000, budgetAfter, "RATIONAL", 0, now.minusMinutes(2)
		);
		insertDecisionCase(
			user, itemIds, decisionIds, "stopIrrational", "QA STOP 비합리 결과", "STOP", 90000, budgetAfter, "IRRATIONAL", 4, now.minusMinutes(3)
		);

		jdbcTemplate.update(
			"update budget_cycles set spent_amount = ?, updated_at = ? where id = ?",
			budgetAfter,
			now,
			user.budgetCycleId()
		);

		Map<String, String> paths = new LinkedHashMap<>(user.paths());
		paths.put("consumption", "/api/v1/my/consumption?month=" + user.yearMonth());

		return new QaDecisionScenarioResponse(
			user.userId(),
			user.nickname(),
			user.budgetCycleId(),
			user.yearMonth(),
			itemIds,
			decisionIds,
			paths
		);
	}

	@Transactional
	public QaRegretReminderScenarioResponse createRegretNotificationReadyScenario() {
		QaUserScenarioResponse user = createQaUser();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UUID itemId = insertSavedItem(user.userId(), "QA 회고 알림용 구매", 88000, "DIGITAL", "GO", now.minusDays(7));
		UUID decisionId = insertDecision(
			user.userId(), itemId, user.budgetCycleId(), "GO", 88000, 88000, "RATIONAL", 1, now.minusDays(7)
		);
		UUID reminderId = insertDueRegretReminder(user.userId(), itemId, decisionId, now.minusMinutes(1), now);
		jdbcTemplate.update("update budget_cycles set spent_amount = 88000, updated_at = ? where id = ?", now, user.budgetCycleId());

		Map<String, String> paths = new LinkedHashMap<>(user.paths());
		paths.put("processDueReminder", "/api/dev/qa/reminders/" + reminderId + "/process");
		paths.put("reflection", "/api/v1/reflections");

		return new QaRegretReminderScenarioResponse(
			user.userId(),
			user.nickname(),
			user.budgetCycleId(),
			user.yearMonth(),
			itemId,
			decisionId,
			reminderId,
			paths
		);
	}

	public QaReminderProcessResponse processDueReminder(UUID reminderId) {
		UUID userId = reminderOwnerId(reminderId);
		if (!isQaUser(userId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only QA scenario reminders can be processed.");
		}
		return new QaReminderProcessResponse(reminderNotificationWorker.processDueReminder(reminderId));
	}

	@Transactional
	public void deleteQaUser(UUID userId) {
		if (!userExists(userId)) {
			return;
		}
		if (!isQaUser(userId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only QA scenario users can be deleted.");
		}

		deleteSocialData(userId);
		deleteDecisionData(userId);
		deleteUserSettings(userId);
		jdbcTemplate.update("delete from users where id = ?", userId);
	}

	private boolean userExists(UUID userId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"select exists(select 1 from users where id = ?)",
			Boolean.class,
			userId
		);
		return Boolean.TRUE.equals(exists);
	}

	private boolean isQaUser(UUID userId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"""
			select exists(
				select 1
				from social_accounts
				where user_id = ?
				  and provider_user_id = ?
			)
			""",
			Boolean.class,
			userId,
			QA_PROVIDER_USER_ID_PREFIX + userId
		);
		return Boolean.TRUE.equals(exists);
	}

	private UUID reminderOwnerId(UUID reminderId) {
		return jdbcTemplate.query(
				"select user_id from reminder_schedules where id = ?",
				(rs, rowNumber) -> rs.getObject("user_id", UUID.class),
				reminderId
			)
			.stream()
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder was not found."));
	}

	private UUID insertSavedItem(
		UUID userId,
		String title,
		int listedPrice,
		String category,
		String status,
		OffsetDateTime createdAt
	) {
		UUID itemId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into saved_items (
				id, user_id, input_source, title, listed_price, currency_code, category,
				category_locked_by_user, status, created_at, updated_at
			)
			values (?, ?, 'DIRECT_INPUT', ?, ?, 'KRW', ?, false, ?, ?, ?)
			""",
			itemId,
			userId,
			title,
			listedPrice,
			category,
			status,
			createdAt,
			createdAt
		);
		return itemId;
	}

	private int insertDecisionCase(
		QaUserScenarioResponse user,
		List<UUID> itemIds,
		Map<String, UUID> decisionIds,
		String key,
		String title,
		String result,
		int listedPrice,
		int currentBudgetAfter,
		String rationality,
		int yesCount,
		OffsetDateTime decidedAt
	) {
		UUID itemId = insertSavedItem(user.userId(), title, listedPrice, "DIGITAL", result, decidedAt);
		Integer finalPrice = "GO".equals(result) ? listedPrice : null;
		int nextBudgetAfter = finalPrice == null ? currentBudgetAfter : currentBudgetAfter + finalPrice;
		UUID decisionId = insertDecision(
			user.userId(), itemId, user.budgetCycleId(), result, finalPrice, nextBudgetAfter, rationality, yesCount, decidedAt
		);
		itemIds.add(itemId);
		decisionIds.put(key, decisionId);
		return nextBudgetAfter;
	}

	private UUID insertDecision(
		UUID userId,
		UUID itemId,
		UUID budgetCycleId,
		String result,
		Integer finalPrice,
		int budgetAfterAmount,
		String rationality,
		int yesCount,
		OffsetDateTime decidedAt
	) {
		UUID decisionId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into purchase_decisions (
				id, user_id, item_id, budget_cycle_id, result, final_price, budget_after_amount,
				similar_category_spend_amount, rationality_result, self_check_yes_count,
				is_changed, change_count, decided_at, created_at, updated_at
			)
			values (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, false, 0, ?, ?, ?)
			""",
			decisionId,
			userId,
			itemId,
			budgetCycleId,
			result,
			finalPrice,
			budgetAfterAmount,
			rationality,
			yesCount,
			decidedAt,
			decidedAt,
			decidedAt
		);
		insertSelfCheck(decisionId, rationality, yesCount, decidedAt);
		return decisionId;
	}

	private void insertSelfCheck(UUID decisionId, String rationality, int yesCount, OffsetDateTime submittedAt) {
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
			yesCount,
			rationality,
			submittedAt,
			submittedAt,
			submittedAt
		);
		for (int i = 0; i < SELF_CHECK_CODES.size(); i++) {
			jdbcTemplate.update(
				"""
				insert into self_check_answers (
					id, response_set_id, question_code, answer_boolean, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?)
				""",
				UUID.randomUUID(),
				responseSetId,
				SELF_CHECK_CODES.get(i),
				i < yesCount,
				submittedAt,
				submittedAt
			);
		}
	}

	private UUID insertDueRegretReminder(
		UUID userId,
		UUID itemId,
		UUID decisionId,
		OffsetDateTime scheduledFor,
		OffsetDateTime now
	) {
		UUID reminderId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into reminder_schedules (
				id, user_id, item_id, decision_id, reminder_type, scheduled_for,
				status, sent_at, canceled_at, cancel_reason, created_at, updated_at
			)
			values (?, ?, ?, ?, 'REGRET_CHECK_7_DAYS', ?, 'SCHEDULED', null, null, null, ?, ?)
			""",
			reminderId,
			userId,
			itemId,
			decisionId,
			scheduledFor,
			now,
			now
		);
		return reminderId;
	}

	private UUID insertUnreadNotification(UUID userId, OffsetDateTime now) {
		UUID notificationId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into notifications (
				id, user_id, notification_type, title, body, item_id, decision_id, reminder_id,
				target_path, is_read, read_at, created_at, updated_at
			)
			values (?, ?, 'BUDGET_WARNING', 'QA 예산 초과 알림', '이번 달 예산을 초과한 상태입니다.',
				null, null, null, '/api/v1/home/summary', false, null, ?, ?)
			""",
			notificationId,
			userId,
			now,
			now
		);
		return notificationId;
	}

	private void deleteSocialData(UUID userId) {
		jdbcTemplate.update(
			"""
			delete from share_tokens
			where feed_post_id in (
				select id from feed_posts where user_id = ?
			)
			""",
			userId
		);
		jdbcTemplate.update(
			"""
			delete from post_reports
			where reporter_user_id = ?
			   or (target_type = 'POST' and target_id in (
					select id from feed_posts where user_id = ?
			   ))
			   or (target_type = 'COMMENT' and target_id in (
					select id
					from post_comments
					where user_id = ?
					   or post_id in (select id from feed_posts where user_id = ?)
			   ))
			   or (target_type = 'USER' and target_id = ?)
			""",
			userId,
			userId,
			userId,
			userId,
			userId
		);
		jdbcTemplate.update("delete from user_blocks where blocker_user_id = ? or blocked_user_id = ?", userId, userId);
		jdbcTemplate.update(
			"""
			delete from post_votes
			where user_id = ?
			   or post_id in (select id from feed_posts where user_id = ?)
			""",
			userId,
			userId
		);
		jdbcTemplate.update(
			"""
			delete from post_comments
			where user_id = ?
			   or post_id in (select id from feed_posts where user_id = ?)
			""",
			userId,
			userId
		);
		jdbcTemplate.update("delete from feed_posts where user_id = ?", userId);
	}

	private void deleteDecisionData(UUID userId) {
		jdbcTemplate.update("delete from notifications where user_id = ?", userId);
		jdbcTemplate.update("delete from purchase_reflections where user_id = ?", userId);
		jdbcTemplate.update("delete from reminder_schedules where user_id = ?", userId);
		jdbcTemplate.update("delete from mascot_state_events where user_id = ?", userId);
		jdbcTemplate.update(
			"""
			delete from self_check_answers
			where response_set_id in (
				select sc.id
				from self_check_response_sets sc
				join purchase_decisions pd on pd.id = sc.decision_id
				where pd.user_id = ?
			)
			""",
			userId
		);
		jdbcTemplate.update(
			"""
			delete from self_check_response_sets
			where decision_id in (
				select id from purchase_decisions where user_id = ?
			)
			""",
			userId
		);
		jdbcTemplate.update("delete from purchase_decision_change_logs where user_id = ?", userId);
		jdbcTemplate.update("delete from purchase_decisions where user_id = ?", userId);
		jdbcTemplate.update(
			"""
			delete from item_source_metadata
			where item_id in (
				select id from saved_items where user_id = ?
			)
			""",
			userId
		);
		jdbcTemplate.update("delete from saved_items where user_id = ?", userId);
		jdbcTemplate.update("delete from budget_cycles where user_id = ?", userId);
	}

	private void deleteUserSettings(UUID userId) {
		jdbcTemplate.update(
			"""
			delete from survey_answers
			where response_set_id in (
				select id from survey_response_sets where user_id = ?
			)
			""",
			userId
		);
		jdbcTemplate.update("delete from survey_response_sets where user_id = ?", userId);
		jdbcTemplate.update("delete from user_terms_agreements where user_id = ?", userId);
		jdbcTemplate.update("delete from marketing_consent_histories where user_id = ?", userId);
		jdbcTemplate.update("delete from device_push_tokens where user_id = ?", userId);
		jdbcTemplate.update("delete from user_notification_settings where user_id = ?", userId);
		jdbcTemplate.update("delete from refresh_tokens where user_id = ?", userId);
		jdbcTemplate.update("delete from social_accounts where user_id = ?", userId);
		jdbcTemplate.update("delete from mascot_profiles where user_id = ?", userId);
		jdbcTemplate.update("delete from user_profiles where user_id = ?", userId);
	}

	private Map<String, String> paths(UUID userId) {
		Map<String, String> paths = new LinkedHashMap<>();
		paths.put("home", "/api/v1/home/summary");
		paths.put("notifications", "/api/v1/notifications");
		paths.put("wishlist", "/api/v1/wishlist/items");
		paths.put("mypage", "/api/v1/users/me");
		paths.put("cleanup", "/api/dev/qa/users/" + userId);
		return paths;
	}

	private String currentYearMonth() {
		return YearMonth.now(SEOUL_ZONE).toString();
	}
}
