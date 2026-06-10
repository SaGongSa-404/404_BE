package com.sagongsa.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
	"app.notification.reminder-worker.enabled=false",
	"app.notification.trigger-worker.enabled=false"
})
class NotificationTriggerWorkerIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private NotificationTriggerWorker notificationTriggerWorker;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockBean
	private PushNotificationService pushNotificationService;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void createsDueVoteRegretAndWishlistNotifications() {
		OffsetDateTime now = OffsetDateTime.of(2026, 6, 10, 12, 0, 0, 0, ZoneOffset.UTC);

		UUID postOwnerId = createReadyUser(now.minusDays(10));
		UUID firstVoterId = createReadyUser(now.minusDays(10));
		UUID secondVoterId = createReadyUser(now.minusDays(10));
		UUID socialItemId = insertSavedItem(postOwnerId, "투표 대상 상품", "SAVED", now.minusHours(1));
		UUID postId = insertPost(postOwnerId, socialItemId, now.minusDays(8));
		insertVote(postId, firstVoterId, "GO", now.minusDays(7));
		insertVote(postId, secondVoterId, "STOP", now.minusDays(7));

		UUID regretUserId = createReadyUser(now.minusDays(10));
		UUID regretItemId = insertSavedItem(regretUserId, "1234567890123456", "GO", now.minusDays(10));
		UUID decisionId = insertDecision(regretUserId, regretItemId, now.minusDays(9));
		insertNotification(regretUserId, "REGRET_CHECK_READY", "위시 돌아보기", "1차", regretItemId,
			decisionId, null, "/reflections?decisionId=" + decisionId, null, now.minusDays(3));

		UUID reminderUserId = createReadyUser(now.minusDays(10));
		UUID reminderItemId = insertSavedItem(reminderUserId, "리마인드 대상 상품", "SAVED", now.minusDays(4));

		int createdCount = notificationTriggerWorker.processDueNotifications(now);

		assertThat(createdCount).isEqualTo(4);
		assertThat(queryCount("SOCIAL_VOTE_SUMMARY")).isEqualTo(1);
		assertThat(queryCount("SOCIAL_DECISION_NUDGE")).isEqualTo(1);
		assertThat(queryCount("REGRET_CHECK_FOLLOW_UP")).isEqualTo(1);
		assertThat(queryCount("WISHLIST_REMINDER")).isEqualTo(1);
		assertThat(queryString("select body from notifications where notification_type = 'SOCIAL_VOTE_SUMMARY'"))
			.isEqualTo("🗳️24시간 동안 총 2명이 투표했어요! 결과 확인해보세요");
		assertThat(queryString("select body from notifications where notification_type = 'REGRET_CHECK_FOLLOW_UP'"))
			.isEqualTo("'123456789012345...' 아직 확인 안 하셨어요!");
		assertThat(queryString("select target_path from notifications where notification_type = 'WISHLIST_REMINDER'"))
			.isEqualTo("/wishlist/items/" + reminderItemId);
		verify(pushNotificationService, times(4)).send(any(NotificationPushMessage.class));

		int duplicateCount = notificationTriggerWorker.processDueNotifications(now);

		assertThat(duplicateCount).isZero();
		assertThat(queryInteger("select count(*) from notifications")).isEqualTo(5);
	}

	@Test
	void createsBudgetResetOnlyAfterFirstDayNineAmInKorea() {
		UUID firstUserId = createReadyUser(OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC));
		UUID secondUserId = createReadyUser(OffsetDateTime.of(2026, 6, 2, 0, 0, 0, 0, ZoneOffset.UTC));
		createUser("ACTIVE", "NOT_STARTED", OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC));

		int beforeNineCount = notificationTriggerWorker.processDueNotifications(
			OffsetDateTime.of(2026, 6, 30, 23, 59, 0, 0, ZoneOffset.UTC)
		);
		int afterNineCount = notificationTriggerWorker.processDueNotifications(
			OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC)
		);

		assertThat(beforeNineCount).isZero();
		assertThat(afterNineCount).isEqualTo(2);
		assertThat(queryInteger("select count(*) from notifications where notification_type = 'BUDGET_RESET'"))
			.isEqualTo(2);
		assertThat(queryInteger("select count(*) from notifications where user_id in (?, ?)", firstUserId, secondUserId))
			.isEqualTo(2);
		assertThat(queryString("select target_path from notifications where notification_type = 'BUDGET_RESET' limit 1"))
			.isEqualTo("/home");

		int duplicateCount = notificationTriggerWorker.processDueNotifications(
			OffsetDateTime.of(2026, 7, 1, 0, 1, 0, 0, ZoneOffset.UTC)
		);

		assertThat(duplicateCount).isZero();
	}

	private UUID createReadyUser(OffsetDateTime createdAt) {
		return createUser("ACTIVE", "COMPLETED", createdAt);
	}

	private UUID createUser(String status, String onboardingStatus, OffsetDateTime createdAt) {
		UUID userId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, created_at, updated_at)
			values (?, ?, ?, ?, ?)
			""",
			userId,
			status,
			onboardingStatus,
			createdAt,
			createdAt
		);
		return userId;
	}

	private UUID insertSavedItem(UUID userId, String title, String status, OffsetDateTime createdAt) {
		UUID itemId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into saved_items (
				id, user_id, input_source, title, listed_price, currency_code,
				category, category_locked_by_user, status, created_at, updated_at
			)
			values (?, ?, 'DIRECT_INPUT', ?, 10000, 'KRW', 'DIGITAL', false, ?, ?, ?)
			""",
			itemId,
			userId,
			title,
			status,
			createdAt,
			createdAt
		);
		return itemId;
	}

	private UUID insertPost(UUID userId, UUID itemId, OffsetDateTime createdAt) {
		UUID postId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into feed_posts (
				id, user_id, item_id, title, body, go_count, stop_count, created_at, updated_at
			)
			values (?, ?, ?, '테스트 게시글', '내용', 0, 0, ?, ?)
			""",
			postId,
			userId,
			itemId,
			createdAt,
			createdAt
		);
		return postId;
	}

	private void insertVote(UUID postId, UUID userId, String voteType, OffsetDateTime createdAt) {
		jdbcTemplate.update(
			"""
			insert into post_votes (id, post_id, user_id, vote_type, created_at, updated_at)
			values (?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			postId,
			userId,
			voteType,
			createdAt,
			createdAt
		);
	}

	private UUID insertDecision(UUID userId, UUID itemId, OffsetDateTime decidedAt) {
		UUID decisionId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into purchase_decisions (
				id, user_id, item_id, result, final_price, budget_after_amount,
				similar_category_spend_amount, rationality_result, self_check_yes_count,
				decided_at, created_at, updated_at
			)
			values (?, ?, ?, 'GO', 10000, 10000, 0, 'RATIONAL', 1, ?, ?, ?)
			""",
			decisionId,
			userId,
			itemId,
			decidedAt,
			decidedAt,
			decidedAt
		);
		return decisionId;
	}

	private void insertNotification(
		UUID userId,
		String type,
		String title,
		String body,
		UUID itemId,
		UUID decisionId,
		UUID reminderId,
		String targetPath,
		String dedupeKey,
		OffsetDateTime createdAt
	) {
		jdbcTemplate.update(
			"""
			insert into notifications (
				id, user_id, notification_type, title, body, item_id, decision_id,
				reminder_id, target_path, dedupe_key, is_read, created_at, updated_at
			)
			values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			type,
			title,
			body,
			itemId,
			decisionId,
			reminderId,
			targetPath,
			dedupeKey,
			createdAt,
			createdAt
		);
	}

	private Integer queryCount(String notificationType) {
		return queryInteger("select count(*) from notifications where notification_type = ?", notificationType);
	}

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	private String queryString(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}
}
