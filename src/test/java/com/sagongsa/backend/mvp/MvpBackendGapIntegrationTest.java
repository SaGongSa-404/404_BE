package com.sagongsa.backend.mvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MvpBackendGapIntegrationTest extends PostgreSqlContainerTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void returnsDeliberationSummaryForSavedItem() throws Exception {
		UUID userId = createReadyUser();
		String yearMonth = YearMonth.now(SEOUL_ZONE).toString();
		insertBudgetCycle(userId, yearMonth, 500_000, 90_000);
		insertHistoricalGoDecision(userId, "이전 패션", "FASHION", 20_000);
		UUID itemId = insertSavedItem(userId, "새 패션", "FASHION", "SAVED", 15_000);

		mockMvc.perform(get("/api/v1/deliberations/items/{itemId}", itemId)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.item.id").value(itemId.toString()))
			.andExpect(jsonPath("$.item.status").value("SAVED"))
			.andExpect(jsonPath("$.budget.spentAmount").value(90_000))
			.andExpect(jsonPath("$.budget.projectedSpentAmount").value(105_000))
			.andExpect(jsonPath("$.budget.projectedUsageRate").value(21.00))
			.andExpect(jsonPath("$.similarCategorySpendAmount").value(20_000))
			.andExpect(jsonPath("$.opportunityCostMessage").value(containsString("15,000원")))
			.andExpect(jsonPath("$.questions.length()").value(4))
			.andExpect(jsonPath("$.questions[0].code").value("NEED"))
			.andExpect(jsonPath("$.questions[0].text").value("오늘 갑자기 갖고 싶어진 건가요?"))
			.andExpect(jsonPath("$.questions[1].code").value("BUDGET"))
			.andExpect(jsonPath("$.questions[2].code").value("ALTERNATIVE"))
			.andExpect(jsonPath("$.questions[3].code").value("DELAY"));
	}

	@Test
	void listsAndReadsNotifications() throws Exception {
		UUID userId = createReadyUser();
		UUID oldNotificationId = insertNotification(userId, "WISHLIST_REMINDER", "오래 고민 중", "아직 고민 중인 상품이 있어요.", false, 10);
		UUID latestNotificationId = insertNotification(userId, "REGRET_CHECK_READY", "7일 후 확인", "지금도 잘 샀다고 생각하나요?", false, 20);
		insertNotification(userId, "SOCIAL_VOTE", "투표", "투표가 달렸어요.", true, 30);

		mockMvc.perform(get("/api/v1/notifications")
				.header(USER_ID_HEADER, userId)
				.param("unreadOnly", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(latestNotificationId.toString()))
			.andExpect(jsonPath("$[1].id").value(oldNotificationId.toString()));

		mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", latestNotificationId)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(latestNotificationId.toString()))
			.andExpect(jsonPath("$.read").value(true));

		assertThat(queryInteger("select count(*) from notifications where user_id = ? and is_read = false", userId))
			.isEqualTo(1);
	}

	@Test
	void createsReflectionOnlyForGoDecision() throws Exception {
		UUID userId = createReadyUser();
		UUID goItemId = insertSavedItem(userId, "산 상품", "BEAUTY", "GO", 30_000);
		UUID goDecisionId = insertDecision(userId, goItemId, "GO", 30_000, "RATIONAL", 1, null);
		UUID reminderId = insertReminder(userId, goItemId, goDecisionId, "SENT");
		UUID stopItemId = insertSavedItem(userId, "참은 상품", "FOOD", "STOP", 12_000);
		UUID stopDecisionId = insertDecision(userId, stopItemId, "STOP", 12_000, "IRRATIONAL", 3, null);

		mockMvc.perform(post("/api/v1/reflections")
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"decisionId": "%s",
					"satisfactionScore": 5,
					"regretLevel": "NONE",
					"stillUsing": true,
					"reflectionNote": "잘 쓰고 있어요"
				}
				""".formatted(goDecisionId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.decisionId").value(goDecisionId.toString()))
			.andExpect(jsonPath("$.itemId").value(goItemId.toString()))
			.andExpect(jsonPath("$.reminderId").value(reminderId.toString()))
			.andExpect(jsonPath("$.regretLevel").value("NONE"));

		mockMvc.perform(post("/api/v1/reflections")
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"decisionId": "%s",
					"regretLevel": "LOW"
				}
				""".formatted(stopDecisionId)))
			.andExpect(status().isConflict());
	}

	@Test
	void updatesConsumptionRecordWithoutChangingMascotReaction() throws Exception {
		UUID userId = createReadyUser();
		String yearMonth = YearMonth.now(SEOUL_ZONE).toString();
		UUID budgetCycleId = insertBudgetCycle(userId, yearMonth, 500_000, 100_000);
		UUID itemId = insertSavedItem(userId, "수정할 상품", "FASHION", "GO", 40_000);
		UUID decisionId = insertDecision(userId, itemId, "GO", 40_000, "RATIONAL", 1, budgetCycleId);
		insertSelfCheck(decisionId, 1, "RATIONAL", true, false, false, false);
		insertMascotEvent(userId, itemId, decisionId);
		insertReminder(userId, itemId, decisionId, "SCHEDULED");

		mockMvc.perform(patch("/api/v1/decisions/{decisionId}/result", decisionId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"result": "STOP",
					"changeReason": "환불했어요",
					"selfCheckAnswers": [
						{"questionCode": "NEED", "answerBoolean": true},
						{"questionCode": "BUDGET", "answerBoolean": true},
						{"questionCode": "ALTERNATIVE", "answerBoolean": true},
						{"questionCode": "DELAY", "answerBoolean": false}
					]
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemStatus").value("STOP"))
			.andExpect(jsonPath("$.result").value("STOP"))
			.andExpect(jsonPath("$.budgetAfterAmount").value(60_000))
			.andExpect(jsonPath("$.budgetExhaustedAfter").value(false))
			.andExpect(jsonPath("$.budgetBecameExhausted").value(false))
			.andExpect(jsonPath("$.selfCheckYesCount").value(3))
			.andExpect(jsonPath("$.rationalityResult").value("IRRATIONAL"))
			.andExpect(jsonPath("$.reminder.status").value("CANCELED"))
			.andExpect(jsonPath("$.mascot.state").value("SMILE"));

		assertThat(queryString("select status from saved_items where id = ?", itemId)).isEqualTo("STOP");
		assertThat(queryInteger("select spent_amount from budget_cycles where id = ?", budgetCycleId)).isEqualTo(60_000);
		assertThat(queryInteger("select change_count from purchase_decisions where id = ?", decisionId)).isEqualTo(1);
		assertThat(queryInteger("select count(*) from purchase_decision_change_logs where decision_id = ?", decisionId)).isEqualTo(1);
		assertThat(queryString("select status from reminder_schedules where decision_id = ?", decisionId)).isEqualTo("CANCELED");
		assertThat(queryInteger("select count(*) from mascot_state_events where decision_id = ?", decisionId)).isEqualTo(1);
	}

	@Test
	void returnsBudgetExhaustionWhenConsumptionRecordUpdateCrossesBudgetLimit() throws Exception {
		UUID userId = createReadyUser();
		String yearMonth = YearMonth.now(SEOUL_ZONE).toString();
		UUID budgetCycleId = insertBudgetCycle(userId, yearMonth, 100_000, 90_000);
		UUID itemId = insertSavedItem(userId, "예산 소진 상품", "FASHION", "STOP", 20_000);
		UUID decisionId = insertDecision(userId, itemId, "STOP", 20_000, "RATIONAL", 1, budgetCycleId);
		insertSelfCheck(decisionId, 1, "RATIONAL", true, false, false, false);

		mockMvc.perform(patch("/api/v1/decisions/{decisionId}/result", decisionId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"result": "GO",
					"finalPrice": 20000,
					"changeReason": "결국 구매했어요"
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemStatus").value("GO"))
			.andExpect(jsonPath("$.budgetAfterAmount").value(110_000))
			.andExpect(jsonPath("$.budgetExhaustedAfter").value(true))
			.andExpect(jsonPath("$.budgetBecameExhausted").value(true));

		assertThat(queryInteger("select spent_amount from budget_cycles where id = ?", budgetCycleId)).isEqualTo(110_000);
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
		jdbcTemplate.update(
			"""
			insert into user_profiles (user_id, nickname, mascot_name, timezone, created_at, updated_at)
			values (?, 'tester', '너굴', 'Asia/Seoul', ?, ?)
			""",
			userId,
			now,
			now
		);
		jdbcTemplate.update(
			"""
			insert into mascot_profiles (user_id, mascot_state, last_state_changed_at, updated_at)
			values (?, 'DEFAULT', ?, ?)
			""",
			userId,
			now,
			now
		);
		return userId;
	}

	private UUID insertBudgetCycle(UUID userId, String yearMonth, int monthlyBudgetAmount, int spentAmount) {
		UUID budgetCycleId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into budget_cycles (
				id, user_id, year_month, monthly_budget_amount, spent_amount,
				warning_threshold_rate, created_at, updated_at
			)
			values (?, ?, ?, ?, ?, 80.00, ?, ?)
			""",
			budgetCycleId,
			userId,
			yearMonth,
			monthlyBudgetAmount,
			spentAmount,
			now,
			now
		);
		return budgetCycleId;
	}

	private UUID insertSavedItem(UUID userId, String title, String category, String status, int listedPrice) {
		UUID itemId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into saved_items (
				id, user_id, input_source, title, listed_price, currency_code,
				category, category_locked_by_user, status, created_at, updated_at
			)
			values (?, ?, 'DIRECT_INPUT', ?, ?, 'KRW', ?, false, ?, ?, ?)
			""",
			itemId,
			userId,
			title,
			listedPrice,
			category,
			status,
			now,
			now
		);
		return itemId;
	}

	private void insertHistoricalGoDecision(UUID userId, String title, String category, int finalPrice) {
		UUID itemId = insertSavedItem(userId, title, category, "GO", finalPrice);
		insertDecision(userId, itemId, "GO", finalPrice, "RATIONAL", 0, null);
	}

	private UUID insertDecision(
		UUID userId,
		UUID itemId,
		String result,
		int finalPrice,
		String rationalityResult,
		int yesCount,
		UUID budgetCycleId
	) {
		UUID decisionId = UUID.randomUUID();
		OffsetDateTime now = currentKstMonthFixtureTime();
		jdbcTemplate.update(
			"""
			insert into purchase_decisions (
				id, user_id, item_id, budget_cycle_id, result, final_price,
				budget_after_amount, similar_category_spend_amount, rationality_result,
				self_check_yes_count, decided_at, created_at, updated_at
			)
			values (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
			""",
			decisionId,
			userId,
			itemId,
			budgetCycleId,
			result,
			finalPrice,
			result.equals("GO") ? finalPrice : 0,
			rationalityResult,
			yesCount,
			now,
			now,
			now
		);
		return decisionId;
	}

	private OffsetDateTime currentKstMonthFixtureTime() {
		return YearMonth.now(SEOUL_ZONE)
			.atDay(1)
			.atStartOfDay(SEOUL_ZONE)
			.plusHours(1)
			.toOffsetDateTime()
			.withOffsetSameInstant(ZoneOffset.UTC);
	}

	private void insertSelfCheck(UUID decisionId, int yesCount, String rationalityResult, boolean first, boolean second, boolean third, boolean fourth) {
		UUID responseSetId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
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
			rationalityResult,
			now,
			now,
			now
		);
		insertSelfCheckAnswer(responseSetId, "NEED", first);
		insertSelfCheckAnswer(responseSetId, "BUDGET", second);
		insertSelfCheckAnswer(responseSetId, "ALTERNATIVE", third);
		insertSelfCheckAnswer(responseSetId, "DELAY", fourth);
	}

	private void insertSelfCheckAnswer(UUID responseSetId, String questionCode, boolean answerBoolean) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
		jdbcTemplate.update(
			"""
			insert into self_check_answers (
				id, response_set_id, question_code, answer_boolean, created_at, updated_at
			)
			values (?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			responseSetId,
			questionCode,
			answerBoolean,
			now,
			now
		);
	}

	private UUID insertReminder(UUID userId, UUID itemId, UUID decisionId, String status) {
		UUID reminderId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
		OffsetDateTime scheduledFor = now.plusDays(7);
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

	private void insertMascotEvent(UUID userId, UUID itemId, UUID decisionId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
		jdbcTemplate.update(
			"""
			insert into mascot_state_events (
				id, user_id, item_id, decision_id, event_type, previous_state,
				new_state, reaction_message, created_at, updated_at
			)
			values (?, ?, ?, ?, 'DECISION_REACTION', 'DEFAULT', 'SMILE', '합리적으로 잘 결정했어요', ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			itemId,
			decisionId,
			now,
			now
		);
	}

	private UUID insertNotification(UUID userId, String type, String title, String body, boolean read, int secondsOffset) {
		UUID notificationId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(secondsOffset);
		jdbcTemplate.update(
			"""
			insert into notifications (
				id, user_id, notification_type, title, body, target_path,
				is_read, read_at, created_at, updated_at
			)
			values (?, ?, ?, ?, ?, '/target', ?, ?, ?, ?)
			""",
			notificationId,
			userId,
			type,
			title,
			body,
			read,
			read ? now : null,
			now,
			now
		);
		return notificationId;
	}

	private String queryString(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}
}
