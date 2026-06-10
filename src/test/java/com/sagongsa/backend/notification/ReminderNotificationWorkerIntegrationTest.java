package com.sagongsa.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "app.notification.reminder-worker.enabled=false")
class ReminderNotificationWorkerIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private ReminderNotificationWorker reminderNotificationWorker;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockBean
	private PushNotificationService pushNotificationService;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void createsNotificationAndMarksDueReminderSent() {
		UUID userId = createReadyUser();
		UUID itemId = insertSavedItem(userId, "만족도 확인 상품", "GO");
		UUID decisionId = insertDecision(userId, itemId, "GO");
		UUID reminderId = insertReminder(userId, itemId, decisionId, "SCHEDULED", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

		int processedCount = reminderNotificationWorker.processDueReminders();

		assertThat(processedCount).isEqualTo(1);
		assertThat(queryString("select status from reminder_schedules where id = ?", reminderId)).isEqualTo("SENT");
		assertThat(queryInteger("select count(*) from notifications where reminder_id = ?", reminderId)).isEqualTo(1);
		assertThat(queryString("select notification_type from notifications where reminder_id = ?", reminderId)).isEqualTo("REGRET_CHECK_READY");
		assertThat(queryString("select title from notifications where reminder_id = ?", reminderId)).isEqualTo("위시 돌아보기");
		assertThat(queryString("select body from notifications where reminder_id = ?", reminderId))
			.isEqualTo("'만족도 확인 상품' 구매한 지 일주일이 지났어요. 만족스러우신가요?");
		assertThat(queryString("select channel_id from notifications where reminder_id = ?", reminderId))
			.isEqualTo("consumption_management");
		assertThat(queryString("select target_path from notifications where reminder_id = ?", reminderId))
			.isEqualTo("/reflections?decisionId=" + decisionId);
		UUID notificationId = queryUuid("select id from notifications where reminder_id = ?", reminderId);
		verify(pushNotificationService).send(argThat(message ->
			message.userId().equals(userId)
				&& message.notificationId().equals(notificationId)
				&& message.notificationType().equals("REGRET_CHECK_READY")
				&& message.targetPath().equals("/reflections?decisionId=" + decisionId)
				&& message.channelId().equals("consumption_management")
		));
	}

	@Test
	void doesNotCreateDuplicateNotificationForSameReminder() {
		UUID userId = createReadyUser();
		UUID itemId = insertSavedItem(userId, "중복 방지 상품", "GO");
		UUID decisionId = insertDecision(userId, itemId, "GO");
		UUID reminderId = insertReminder(userId, itemId, decisionId, "SCHEDULED", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
		insertNotification(userId, itemId, decisionId, reminderId);

		int processedCount = reminderNotificationWorker.processDueReminders();

		assertThat(processedCount).isEqualTo(1);
		assertThat(queryString("select status from reminder_schedules where id = ?", reminderId)).isEqualTo("SENT");
		assertThat(queryInteger("select count(*) from notifications where reminder_id = ?", reminderId)).isEqualTo(1);
		verify(pushNotificationService, never()).send(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void ignoresFutureAndCanceledReminders() {
		UUID userId = createReadyUser();
		UUID futureItemId = insertSavedItem(userId, "미래 알림 상품", "GO");
		UUID futureDecisionId = insertDecision(userId, futureItemId, "GO");
		UUID canceledItemId = insertSavedItem(userId, "취소 알림 상품", "GO");
		UUID canceledDecisionId = insertDecision(userId, canceledItemId, "GO");
		UUID futureReminderId = insertReminder(userId, futureItemId, futureDecisionId, "SCHEDULED", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
		UUID canceledReminderId = insertReminder(userId, canceledItemId, canceledDecisionId, "CANCELED", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

		int processedCount = reminderNotificationWorker.processDueReminders();

		assertThat(processedCount).isZero();
		assertThat(queryString("select status from reminder_schedules where id = ?", futureReminderId)).isEqualTo("SCHEDULED");
		assertThat(queryString("select status from reminder_schedules where id = ?", canceledReminderId)).isEqualTo("CANCELED");
		assertThat(queryInteger("select count(*) from notifications where user_id = ?", userId)).isZero();
	}

	@Test
	void skipsReminderWhenRegretReminderSettingWasDisabledAfterScheduling() {
		UUID userId = createReadyUser();
		disableRegretReminder(userId);
		UUID itemId = insertSavedItem(userId, "설정 해제 상품", "GO");
		UUID decisionId = insertDecision(userId, itemId, "GO");
		UUID reminderId = insertReminder(userId, itemId, decisionId, "SCHEDULED", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

		int processedCount = reminderNotificationWorker.processDueReminders();

		assertThat(processedCount).isZero();
		assertThat(queryString("select status from reminder_schedules where id = ?", reminderId)).isEqualTo("SCHEDULED");
		assertThat(queryInteger("select count(*) from notifications where reminder_id = ?", reminderId)).isZero();
	}

	@Test
	void processesEligibleReminderEvenWhenOlderDisabledRemindersFillBatch() {
		UUID disabledUserId = createReadyUser();
		disableRegretReminder(disabledUserId);
		OffsetDateTime oldDue = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
		for (int index = 0; index < 50; index++) {
			UUID disabledItemId = insertSavedItem(disabledUserId, "설정 해제 상품 " + index, "GO");
			UUID disabledDecisionId = insertDecision(disabledUserId, disabledItemId, "GO");
			insertReminder(disabledUserId, disabledItemId, disabledDecisionId, "SCHEDULED", oldDue.plusSeconds(index));
		}
		UUID userId = createReadyUser();
		UUID itemId = insertSavedItem(userId, "정상 처리 상품", "GO");
		UUID decisionId = insertDecision(userId, itemId, "GO");
		UUID reminderId = insertReminder(userId, itemId, decisionId, "SCHEDULED", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

		int processedCount = reminderNotificationWorker.processDueReminders();

		assertThat(processedCount).isEqualTo(1);
		assertThat(queryString("select status from reminder_schedules where id = ?", reminderId)).isEqualTo("SENT");
		assertThat(queryInteger("select count(*) from notifications where reminder_id = ?", reminderId)).isEqualTo(1);
		assertThat(queryInteger("select count(*) from notifications where user_id = ?", disabledUserId)).isZero();
	}

	@Test
	void skipsReminderForInactiveUser() {
		UUID userId = createReadyUser();
		jdbcTemplate.update(
			"update users set status = 'WITHDRAWN', withdrawn_at = ?, updated_at = ? where id = ?",
			OffsetDateTime.now(ZoneOffset.UTC),
			OffsetDateTime.now(ZoneOffset.UTC),
			userId
		);
		UUID itemId = insertSavedItem(userId, "비활성 사용자 상품", "GO");
		UUID decisionId = insertDecision(userId, itemId, "GO");
		UUID reminderId = insertReminder(userId, itemId, decisionId, "SCHEDULED", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

		int processedCount = reminderNotificationWorker.processDueReminders();

		assertThat(processedCount).isZero();
		assertThat(queryString("select status from reminder_schedules where id = ?", reminderId)).isEqualTo("SCHEDULED");
		assertThat(queryInteger("select count(*) from notifications where reminder_id = ?", reminderId)).isZero();
	}

	private UUID createReadyUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, created_at, updated_at)
			values (?, 'ACTIVE', 'COMPLETED', ?, ?)
			""",
			userId,
			now,
			now
		);
		return userId;
	}

	private UUID insertSavedItem(UUID userId, String title, String status) {
		UUID itemId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into saved_items (
				id, user_id, input_source, title, listed_price, currency_code,
				category, category_locked_by_user, status, created_at, updated_at
			)
			values (?, ?, 'DIRECT_INPUT', ?, 30000, 'KRW', 'BEAUTY', false, ?, ?, ?)
			""",
			itemId,
			userId,
			title,
			status,
			now,
			now
		);
		return itemId;
	}

	private UUID insertDecision(UUID userId, UUID itemId, String result) {
		UUID decisionId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
		jdbcTemplate.update(
			"""
			insert into purchase_decisions (
				id, user_id, item_id, result, final_price, budget_after_amount,
				similar_category_spend_amount, rationality_result, self_check_yes_count,
				decided_at, created_at, updated_at
			)
			values (?, ?, ?, ?, 30000, 30000, 0, 'RATIONAL', 1, ?, ?, ?)
			""",
			decisionId,
			userId,
			itemId,
			result,
			now,
			now,
			now
		);
		return decisionId;
	}

	private UUID insertReminder(UUID userId, UUID itemId, UUID decisionId, String status, OffsetDateTime scheduledFor) {
		UUID reminderId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime sentAt = status.equals("SENT") ? now : null;
		OffsetDateTime canceledAt = status.equals("CANCELED") ? now : null;
		jdbcTemplate.update(
			"""
			insert into reminder_schedules (
				id, user_id, item_id, decision_id, reminder_type, scheduled_for,
				status, sent_at, canceled_at, created_at, updated_at
			)
			values (?, ?, ?, ?, 'REGRET_CHECK_7_DAYS', ?, ?, ?, ?, ?, ?)
			""",
			reminderId,
			userId,
			itemId,
			decisionId,
			scheduledFor,
			status,
			sentAt,
			canceledAt,
			now,
			now
		);
		return reminderId;
	}

	private void insertNotification(UUID userId, UUID itemId, UUID decisionId, UUID reminderId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into notifications (
				id, user_id, notification_type, title, body, item_id,
				decision_id, reminder_id, target_path, is_read, created_at, updated_at
			)
			values (?, ?, 'REGRET_CHECK_READY', '이미 생성됨', '이미 생성됨', ?, ?, ?, '/reflections', false, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			itemId,
			decisionId,
			reminderId,
			now,
			now
		);
	}

	private void disableRegretReminder(UUID userId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into user_notification_settings (
				user_id, regret_reminder_enabled, wishlist_reminder_enabled, updated_at
			)
			values (?, false, false, ?)
			""",
			userId,
			now
		);
	}

	private String queryString(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	private UUID queryUuid(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, UUID.class, args);
	}
}
