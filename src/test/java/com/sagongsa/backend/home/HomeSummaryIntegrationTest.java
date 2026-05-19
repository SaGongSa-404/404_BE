package com.sagongsa.backend.home;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HomeSummaryIntegrationTest extends PostgreSqlContainerTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant BASE_TIME = Instant.parse("2026-04-20T10:00:00Z");
	// 결정 통계 쿼리는 현재 월 기준으로 필터하므로 이번 달 시각을 기준으로 사용
	private static final Instant THIS_MONTH_BASE =
		YearMonth.now(SEOUL_ZONE).atDay(1).atStartOfDay(SEOUL_ZONE).toInstant();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void returnsFullHomeSummary() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000101");
		UUID firstNotificationId = UUID.fromString("00000000-0000-0000-0000-000000000201");
		UUID secondNotificationId = UUID.fromString("00000000-0000-0000-0000-000000000202");
		String yearMonth = YearMonth.now(SEOUL_ZONE).toString();

		insertUser(userId);
		insertUserProfile(userId, "Jae", "Neogul", "Asia/Seoul");
		insertMascotProfile(userId, "SMILE", "Nice choice", BASE_TIME.plusSeconds(10), Instant.now().plusSeconds(3600));
		insertBudgetCycle(userId, yearMonth, 500_000, 125_000, new BigDecimal("80.00"));
		insertNotification(firstNotificationId, userId, "BUDGET_WARNING", "Budget", "Close to limit", "/budget", false, null, BASE_TIME.plusSeconds(30));
		insertNotification(secondNotificationId, userId, "WISHLIST_REMINDER", "Wishlist", "Check saved item", "/items", true, BASE_TIME.plusSeconds(40), BASE_TIME.plusSeconds(20));
		insertDecision(userId, "GO", "RATIONAL", 1, THIS_MONTH_BASE.plusSeconds(50));
		insertDecision(userId, "STOP", "IRRATIONAL", 3, THIS_MONTH_BASE.plusSeconds(60));
		insertDecision(userId, "GO", "IRRATIONAL", 2, THIS_MONTH_BASE.plusSeconds(70));

		mockMvc.perform(get("/api/v1/home/summary").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userProfile.nickname").value("Jae"))
			.andExpect(jsonPath("$.userProfile.mascotName").value("Neogul"))
			.andExpect(jsonPath("$.userProfile.timezone").value("Asia/Seoul"))
			.andExpect(jsonPath("$.mascot.state").value("SMILE"))
			.andExpect(jsonPath("$.mascot.lastReactionMessage").value("Nice choice"))
			.andExpect(jsonPath("$.budget.yearMonth").value(yearMonth))
			.andExpect(jsonPath("$.budget.monthlyBudgetAmount").value(500_000))
			.andExpect(jsonPath("$.budget.spentAmount").value(125_000))
			.andExpect(jsonPath("$.budget.warningThresholdRate").value(80.00))
			.andExpect(jsonPath("$.budget.exhausted").value(false))
			.andExpect(jsonPath("$.budget.showBudgetExhaustionBubble").value(false))
			.andExpect(jsonPath("$.notifications.unreadCount").value(1))
			.andExpect(jsonPath("$.notifications.latestNotifications[0].id").value(firstNotificationId.toString()))
			.andExpect(jsonPath("$.notifications.latestNotifications[0].type").value("BUDGET_WARNING"))
			.andExpect(jsonPath("$.notifications.latestNotifications[0].read").value(false))
			.andExpect(jsonPath("$.notifications.latestNotifications[1].id").value(secondNotificationId.toString()))
			.andExpect(jsonPath("$.rationalChoiceRate").value(66.67));
	}

	@Test
	void returnsDefaultsWhenOptionalHomeDataIsMissing() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000102");
		String yearMonth = YearMonth.now(SEOUL_ZONE).toString();

		insertUser(userId);

		mockMvc.perform(get("/api/v1/home/summary").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userProfile.nickname").value(nullValue()))
			.andExpect(jsonPath("$.userProfile.mascotName").value(nullValue()))
			.andExpect(jsonPath("$.userProfile.timezone").value(nullValue()))
			.andExpect(jsonPath("$.mascot.state").value("DEFAULT"))
			.andExpect(jsonPath("$.mascot.lastReactionMessage").value(nullValue()))
			.andExpect(jsonPath("$.budget.yearMonth").value(yearMonth))
			.andExpect(jsonPath("$.budget.monthlyBudgetAmount").value(0))
			.andExpect(jsonPath("$.budget.spentAmount").value(0))
			.andExpect(jsonPath("$.budget.warningThresholdRate").value(0))
			.andExpect(jsonPath("$.budget.exhausted").value(false))
			.andExpect(jsonPath("$.budget.showBudgetExhaustionBubble").value(false))
			.andExpect(jsonPath("$.notifications.unreadCount").value(0))
			.andExpect(jsonPath("$.notifications.latestNotifications").isEmpty())
			.andExpect(jsonPath("$.rationalChoiceRate").value(nullValue()));
	}

	@Test
	void returnsUnreadCountAndLatestNotifications() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000103");
		UUID newestUnreadId = UUID.fromString("00000000-0000-0000-0000-000000000301");
		UUID latestReadId = UUID.fromString("00000000-0000-0000-0000-000000000302");
		UUID middleUnreadId = UUID.fromString("00000000-0000-0000-0000-000000000303");
		UUID olderUnreadId = UUID.fromString("00000000-0000-0000-0000-000000000304");

		insertUser(userId);
		insertNotification(olderUnreadId, userId, "REGRET_CHECK_READY", "Older", "Older unread", "/older", false, null, BASE_TIME.minusSeconds(60));
		insertNotification(middleUnreadId, userId, "WISHLIST_REMINDER", "Middle", "Middle unread", "/middle", false, null, BASE_TIME.plusSeconds(10));
		insertNotification(latestReadId, userId, "SOCIAL_VOTE", "Read", "Latest read", "/read", true, BASE_TIME.plusSeconds(30), BASE_TIME.plusSeconds(20));
		insertNotification(newestUnreadId, userId, "BUDGET_WARNING", "Newest", "Newest unread", "/newest", false, null, BASE_TIME.plusSeconds(40));

		mockMvc.perform(get("/api/v1/home/summary").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notifications.unreadCount").value(3))
			.andExpect(jsonPath("$.notifications.latestNotifications.length()").value(3))
			.andExpect(jsonPath("$.notifications.latestNotifications[0].id").value(newestUnreadId.toString()))
			.andExpect(jsonPath("$.notifications.latestNotifications[0].read").value(false))
			.andExpect(jsonPath("$.notifications.latestNotifications[1].id").value(latestReadId.toString()))
			.andExpect(jsonPath("$.notifications.latestNotifications[1].read").value(true))
			.andExpect(jsonPath("$.notifications.latestNotifications[2].id").value(middleUnreadId.toString()));
	}

	@Test
	void returnsDefaultMascotWhenReactionExpired() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000104");

		insertUser(userId);
		insertMascotProfile(userId, "SAD", "Old reaction", BASE_TIME, BASE_TIME.plusSeconds(1));

		mockMvc.perform(get("/api/v1/home/summary").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mascot.state").value("DEFAULT"))
			.andExpect(jsonPath("$.mascot.lastReactionMessage").value(nullValue()));
	}

	@Test
	void budgetExhaustedTrueAndShowBubbleTrueWhenSpentReachesLimit() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000105");
		String yearMonth = YearMonth.now(SEOUL_ZONE).toString();

		insertUser(userId);
		insertBudgetCycle(userId, yearMonth, 300_000, 300_000, new BigDecimal("80.00"));

		mockMvc.perform(get("/api/v1/home/summary").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget.exhausted").value(true))
			.andExpect(jsonPath("$.budget.showBudgetExhaustionBubble").value(true));
	}

	@Test
	void showBubbleFalseAfterBubbleMarkedSeen() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000106");
		String yearMonth = YearMonth.now(SEOUL_ZONE).toString();

		insertUser(userId);
		insertBudgetCycle(userId, yearMonth, 300_000, 300_000, new BigDecimal("80.00"));

		mockMvc.perform(post("/api/v1/home/budget-exhaustion-bubble/seen").header("X-User-Id", userId))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/home/summary").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget.exhausted").value(true))
			.andExpect(jsonPath("$.budget.showBudgetExhaustionBubble").value(false));
	}

	@Test
	void markBubbleSeenDoesNothingWhenBudgetNotYetExhausted() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000108");
		String yearMonth = YearMonth.now(SEOUL_ZONE).toString();

		insertUser(userId);
		insertBudgetCycle(userId, yearMonth, 300_000, 100_000, new BigDecimal("80.00"));

		mockMvc.perform(post("/api/v1/home/budget-exhaustion-bubble/seen").header("X-User-Id", userId))
			.andExpect(status().isNoContent());

		// 이후 예산이 소진되면 말풍선이 여전히 노출되어야 함
		jdbcTemplate.update(
			"update budget_cycles set spent_amount = 300000 where user_id = ? and year_month = ?",
			userId, yearMonth
		);

		mockMvc.perform(get("/api/v1/home/summary").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget.exhausted").value(true))
			.andExpect(jsonPath("$.budget.showBudgetExhaustionBubble").value(true));
	}

	@Test
	void budgetExhaustedFalseWhenSpentBelowLimit() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000107");
		String yearMonth = YearMonth.now(SEOUL_ZONE).toString();

		insertUser(userId);
		insertBudgetCycle(userId, yearMonth, 300_000, 299_999, new BigDecimal("80.00"));

		mockMvc.perform(get("/api/v1/home/summary").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget.exhausted").value(false))
			.andExpect(jsonPath("$.budget.showBudgetExhaustionBubble").value(false));
	}

	@Test
	void returnsNotFoundWhenUserDoesNotExist() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000199");

		mockMvc.perform(get("/api/v1/home/summary").header("X-User-Id", userId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("User Not Found"))
			.andExpect(jsonPath("$.detail").value("User not found: " + userId));
	}

	@Test
	void returnsForbiddenBeforeOnboardingCompletion() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000198");
		insertUser(userId, "ACTIVE", "NOT_STARTED");

		mockMvc.perform(get("/api/v1/home/summary").header("X-User-Id", userId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.title").value("Forbidden"));
	}

	private void insertUser(UUID userId) {
		insertUser(userId, "ACTIVE", "COMPLETED");
	}

	private void insertUser(UUID userId, String status, String onboardingStatus) {
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, created_at, updated_at)
			values (?, ?, ?, ?, ?)
			""",
			userId,
			status,
			onboardingStatus,
			timestamp(BASE_TIME),
			timestamp(BASE_TIME)
		);
	}

	private void insertUserProfile(UUID userId, String nickname, String mascotName, String timezone) {
		jdbcTemplate.update(
			"""
			insert into user_profiles (user_id, nickname, mascot_name, timezone, created_at, updated_at)
			values (?, ?, ?, ?, ?, ?)
			""",
			userId,
			nickname,
			mascotName,
			timezone,
			timestamp(BASE_TIME),
			timestamp(BASE_TIME)
		);
	}

	private void insertMascotProfile(
		UUID userId,
		String mascotState,
		String lastReactionMessage,
		Instant lastStateChangedAt,
		Instant reactionExpiresAt
	) {
		jdbcTemplate.update(
			"""
			insert into mascot_profiles (
				user_id,
				mascot_state,
				last_reaction_message,
				last_state_changed_at,
				reaction_expires_at,
				updated_at
			)
			values (?, ?, ?, ?, ?, ?)
			""",
			userId,
			mascotState,
			lastReactionMessage,
			timestamp(lastStateChangedAt),
			timestamp(reactionExpiresAt),
			timestamp(BASE_TIME)
		);
	}

	private void insertBudgetCycle(
		UUID userId,
		String yearMonth,
		int monthlyBudgetAmount,
		int spentAmount,
		BigDecimal warningThresholdRate
	) {
		jdbcTemplate.update(
			"""
			insert into budget_cycles (
				id,
				user_id,
				year_month,
				monthly_budget_amount,
				spent_amount,
				warning_threshold_rate,
				created_at,
				updated_at
			)
			values (?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			yearMonth,
			monthlyBudgetAmount,
			spentAmount,
			warningThresholdRate,
			timestamp(BASE_TIME),
			timestamp(BASE_TIME)
		);
	}

	private void insertNotification(
		UUID notificationId,
		UUID userId,
		String notificationType,
		String title,
		String body,
		String targetPath,
		boolean read,
		Instant readAt,
		Instant createdAt
	) {
		jdbcTemplate.update(
			"""
			insert into notifications (
				id,
				user_id,
				notification_type,
				title,
				body,
				target_path,
				is_read,
				read_at,
				created_at,
				updated_at
			)
			values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			notificationId,
			userId,
			notificationType,
			title,
			body,
			targetPath,
			read,
			timestamp(readAt),
			timestamp(createdAt),
			timestamp(createdAt)
		);
	}

	private void insertDecision(
		UUID userId,
		String result,
		String rationalityResult,
		int selfCheckYesCount,
		Instant decidedAt
	) {
		UUID itemId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into saved_items (
				id,
				user_id,
				input_source,
				title,
				category,
				category_locked_by_user,
				status,
				created_at,
				updated_at
			)
			values (?, ?, 'DIRECT_INPUT', 'Decision item', 'ETC', false, ?, ?, ?)
			""",
			itemId,
			userId,
			result,
			timestamp(decidedAt),
			timestamp(decidedAt)
		);
		jdbcTemplate.update(
			"""
			insert into purchase_decisions (
				id,
				user_id,
				item_id,
				result,
				rationality_result,
				self_check_yes_count,
				decided_at,
				created_at,
				updated_at
			)
			values (?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			itemId,
			result,
			rationalityResult,
			selfCheckYesCount,
			timestamp(decidedAt),
			timestamp(decidedAt),
			timestamp(decidedAt)
		);
	}

	private Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}
}
