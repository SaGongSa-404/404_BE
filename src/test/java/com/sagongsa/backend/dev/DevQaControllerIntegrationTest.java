package com.sagongsa.backend.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest(properties = "app.notification.reminder-worker.enabled=false")
@AutoConfigureMockMvc
class DevQaControllerIntegrationTest extends PostgreSqlContainerTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void feedReadyScenarioCreatesMinePeerCommentAndVoteState() throws Exception {
		String body = mockMvc.perform(post("/api/dev/qa/scenarios/feed-ready"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.postIds.length()").value(2))
			.andExpect(jsonPath("$.commentIds.length()").value(1))
			.andExpect(jsonPath("$.voteIds.length()").value(1))
			.andExpect(jsonPath("$.paths.feed").value("/api/v1/social/posts"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		UUID userId = UUID.fromString(objectMapper.readTree(body).get("userId").asText());
		UUID peerUserId = UUID.fromString(objectMapper.readTree(body).get("peerUserId").asText());
		UUID myPostId = UUID.fromString(objectMapper.readTree(body).get("myPostId").asText());
		UUID peerPostId = UUID.fromString(objectMapper.readTree(body).get("peerPostId").asText());

		mockMvc.perform(get("/api/v1/social/posts")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts.length()").value(2));

		mockMvc.perform(get("/api/v1/social/posts/{postId}", peerPostId)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mine").value(false))
			.andExpect(jsonPath("$.myVote").value("GO"))
			.andExpect(jsonPath("$.goCount").value(1))
			.andExpect(jsonPath("$.commentCount").value(1));

		mockMvc.perform(post("/api/v1/social/posts/{postId}/votes", myPostId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"voteType":"GO"}
					"""))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/social/posts/{postId}", myPostId)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mine").value(true))
			.andExpect(jsonPath("$.myVote").value(nullValue()))
			.andExpect(jsonPath("$.goCount").value(0));

		mockMvc.perform(get("/api/v1/social/posts/{postId}/comments", peerPostId)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.comments[0].mine").value(true));

		mockMvc.perform(get("/api/v1/users/me/posts")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts[0].id").value(myPostId.toString()))
			.andExpect(jsonPath("$.posts[0].mine").value(true));

		mockMvc.perform(get("/api/v1/users/me/votes")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts[0].id").value(peerPostId.toString()))
			.andExpect(jsonPath("$.posts[0].myVote").value("GO"));

		mockMvc.perform(delete("/api/dev/qa/users/{userId}", userId))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/dev/qa/users/{userId}", peerUserId))
			.andExpect(status().isNoContent());

		assertThat(queryInteger("select count(*) from users where id in (?, ?)", userId, peerUserId)).isZero();
		assertThat(queryInteger("select count(*) from feed_posts where id in (?, ?)", myPostId, peerPostId)).isZero();
	}

	@Test
	void mypageConsumptionScenarioSupportsStatsMonthsAndWishHistory() throws Exception {
		String body = mockMvc.perform(post("/api/dev/qa/scenarios/mypage-consumption"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths.statsMonths").value("/api/v1/users/me/stats/months"))
			.andExpect(jsonPath("$.paths.stats").exists())
			.andExpect(jsonPath("$.paths.wishHistory").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();

		UUID userId = UUID.fromString(objectMapper.readTree(body).get("userId").asText());
		String yearMonth = objectMapper.readTree(body).get("yearMonth").asText();

		mockMvc.perform(get("/api/v1/users/me/stats/months")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.months").isArray())
			.andExpect(jsonPath("$.months[0]").value(yearMonth));

		mockMvc.perform(get("/api/v1/users/me/stats")
				.header(USER_ID_HEADER, userId)
				.param("yearMonth", yearMonth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.yearMonth").value(yearMonth))
			.andExpect(jsonPath("$.boughtCount").value(2))
			.andExpect(jsonPath("$.restrainedCount").value(2));

		mockMvc.perform(get("/api/v1/users/me/wishes/history")
				.header(USER_ID_HEADER, userId)
				.param("yearMonth", yearMonth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.wishes.length()").value(4));
	}

	@Test
	void budgetZeroScenarioSupportsHomeAndMypageBudgetState() throws Exception {
		String body = mockMvc.perform(post("/api/dev/qa/scenarios/budget-zero"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths.home").value("/api/v1/home/summary"))
			.andExpect(jsonPath("$.paths.stats").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();

		UUID userId = UUID.fromString(objectMapper.readTree(body).get("userId").asText());
		String yearMonth = objectMapper.readTree(body).get("yearMonth").asText();

		mockMvc.perform(get("/api/v1/home/summary")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget.monthlyBudgetAmount").value(0))
			.andExpect(jsonPath("$.budget.spentAmount").value(0))
			.andExpect(jsonPath("$.budget.remainingAmount").value(0))
			.andExpect(jsonPath("$.budget.exhausted").value(false));

		mockMvc.perform(get("/api/v1/users/me/stats")
				.header(USER_ID_HEADER, userId)
				.param("yearMonth", yearMonth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.yearMonth").value(yearMonth))
			.andExpect(jsonPath("$.budgetAmount").value(0))
			.andExpect(jsonPath("$.spentAmount").value(0))
			.andExpect(jsonPath("$.usageRate").value(nullValue()));
	}

	@Test
	void resultCombinationsScenarioCreatesGoStopAndRationalityCases() throws Exception {
		String body = mockMvc.perform(post("/api/dev/qa/scenarios/result-combinations"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.decisionIds.goRational").exists())
			.andExpect(jsonPath("$.decisionIds.goIrrational").exists())
			.andExpect(jsonPath("$.decisionIds.stopRational").exists())
			.andExpect(jsonPath("$.decisionIds.stopIrrational").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();

		UUID userId = UUID.fromString(objectMapper.readTree(body).get("userId").asText());
		String yearMonth = objectMapper.readTree(body).get("yearMonth").asText();

		mockMvc.perform(get("/api/v1/my/consumption")
				.header(USER_ID_HEADER, userId)
				.param("month", yearMonth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(4));

		assertThat(queryInteger(
			"select count(*) from purchase_decisions where user_id = ? and result = 'GO'",
			userId
		)).isEqualTo(2);
		assertThat(queryInteger(
			"select count(*) from purchase_decisions where user_id = ? and result = 'STOP'",
			userId
		)).isEqualTo(2);
		assertThat(queryInteger(
			"select count(*) from purchase_decisions where user_id = ? and rationality_result = 'RATIONAL'",
			userId
		)).isEqualTo(2);
		assertThat(queryInteger(
			"select count(*) from purchase_decisions where user_id = ? and rationality_result = 'IRRATIONAL'",
			userId
		)).isEqualTo(2);
	}

	@Test
	void regretNotificationReadyScenarioCanProcessDueReminderAndCreateReflection() throws Exception {
		String body = mockMvc.perform(post("/api/dev/qa/scenarios/regret-notification-ready"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths.processDueReminder").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();

		UUID userId = UUID.fromString(objectMapper.readTree(body).get("userId").asText());
		UUID decisionId = UUID.fromString(objectMapper.readTree(body).get("decisionId").asText());
		UUID reminderId = UUID.fromString(objectMapper.readTree(body).get("reminderId").asText());

		mockMvc.perform(post("/api/dev/qa/reminders/{reminderId}/process", reminderId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.processedCount").value(1));

		assertThat(queryString("select status from reminder_schedules where id = ?", reminderId)).isEqualTo("SENT");
		mockMvc.perform(get("/api/v1/notifications")
				.header(USER_ID_HEADER, userId)
				.param("unreadOnly", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].type").value("REGRET_CHECK_READY"))
			.andExpect(jsonPath("$[0].reminderId").value(reminderId.toString()));

		mockMvc.perform(post("/api/v1/reflections")
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"decisionId": "%s",
						"satisfactionScore": 5,
						"regretLevel": "NONE",
						"stillUsing": true,
						"reflectionNote": "QA 회고 확인"
					}
					""".formatted(decisionId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.decisionId").value(decisionId.toString()))
			.andExpect(jsonPath("$.reminderId").value(reminderId.toString()));
	}

	@Test
	void rejectsProcessingNonQaReminder() throws Exception {
		UUID userId = insertNonQaUserWithQaNickname();
		UUID reminderId = insertNonQaDueReminder(userId);

		mockMvc.perform(post("/api/dev/qa/reminders/{reminderId}/process", reminderId))
			.andExpect(status().isBadRequest());

		assertThat(queryString("select status from reminder_schedules where id = ?", reminderId)).isEqualTo("SCHEDULED");
		assertThat(queryInteger(
			"select count(*) from notifications where reminder_id = ?",
			reminderId
		)).isZero();
	}

	@Test
	void basicScenarioCreatesHomeNotificationWishlistAndDeliberationState() throws Exception {
		String body = mockMvc.perform(post("/api/dev/qa/scenarios/basic"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value(startsWith("QA너굴")))
			.andExpect(jsonPath("$.itemIds.length()").value(2))
			.andExpect(jsonPath("$.decisionIds.length()").value(1))
			.andExpect(jsonPath("$.notificationIds.length()").value(1))
			.andExpect(jsonPath("$.paths.deliberation").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();

		UUID userId = UUID.fromString(objectMapper.readTree(body).get("userId").asText());
		UUID deliberationItemId = UUID.fromString(objectMapper.readTree(body).get("deliberationItemId").asText());

		mockMvc.perform(get("/api/v1/home/summary")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget.monthlyBudgetAmount").value(100000))
			.andExpect(jsonPath("$.budget.spentAmount").value(120000))
			.andExpect(jsonPath("$.budget.exhausted").value(true))
			.andExpect(jsonPath("$.notifications.unreadCount").value(1));

		mockMvc.perform(get("/api/v1/notifications")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].type").value("BUDGET_WARNING"))
			.andExpect(jsonPath("$[0].read").value(false));

		mockMvc.perform(get("/api/v1/wishlist/items")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(deliberationItemId.toString()))
			.andExpect(jsonPath("$.items[0].status").value("SAVED"));

		mockMvc.perform(get("/api/v1/deliberations/items/{itemId}", deliberationItemId)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.item.id").value(deliberationItemId.toString()))
			.andExpect(jsonPath("$.budget.monthlyBudgetAmount").value(100000))
			.andExpect(jsonPath("$.budget.spentAmount").value(120000))
			.andExpect(jsonPath("$.similarCategorySpendAmount").value(120000));

		mockMvc.perform(delete("/api/dev/qa/users/{userId}", userId))
			.andExpect(status().isNoContent());

		assertThat(queryInteger("select count(*) from users where id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from saved_items where user_id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from purchase_decisions where user_id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from notifications where user_id = ?", userId)).isZero();
	}

	@Test
	void createsQaUserWithCurrentBudgetAndScenarioPaths() throws Exception {
		String body = mockMvc.perform(post("/api/dev/qa/users"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value(startsWith("QA너굴")))
			.andExpect(jsonPath("$.yearMonth").value(YearMonth.now(SEOUL_ZONE).toString()))
			.andExpect(jsonPath("$.paths.home").value("/api/v1/home/summary"))
			.andExpect(jsonPath("$.paths.mypage").value("/api/v1/users/me"))
			.andExpect(jsonPath("$.paths.cleanup").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();

		UUID userId = UUID.fromString(objectMapper.readTree(body).get("userId").asText());

		assertThat(queryInteger("select count(*) from users where id = ? and status = 'ACTIVE'", userId)).isOne();
		assertThat(queryInteger(
			"select count(*) from social_accounts where user_id = ? and provider_user_id = ?",
			userId,
			"DEV_QA:" + userId
		)).isOne();
		assertThat(queryInteger("select count(*) from user_profiles where user_id = ?", userId)).isOne();
		assertThat(queryInteger("select count(*) from user_notification_settings where user_id = ?", userId)).isOne();
		assertThat(queryInteger("select count(*) from mascot_profiles where user_id = ?", userId)).isOne();
		assertThat(queryInteger(
			"select count(*) from budget_cycles where user_id = ? and year_month = ?",
			userId,
			YearMonth.now(SEOUL_ZONE).toString()
		)).isOne();

		mockMvc.perform(get("/api/v1/home/summary")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userProfile.nickname").value(startsWith("QA너굴")))
			.andExpect(jsonPath("$.budget.yearMonth").value(YearMonth.now(SEOUL_ZONE).toString()));
	}

	@Test
	void deletesOnlyQaScenarioUser() throws Exception {
		String body = mockMvc.perform(post("/api/dev/qa/users"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID userId = UUID.fromString(objectMapper.readTree(body).get("userId").asText());

		mockMvc.perform(delete("/api/dev/qa/users/{userId}", userId))
			.andExpect(status().isNoContent());

		assertThat(queryInteger("select count(*) from users where id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from social_accounts where user_id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from user_profiles where user_id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from budget_cycles where user_id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from user_notification_settings where user_id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from mascot_profiles where user_id = ?", userId)).isZero();
	}

	@Test
	void rejectsDeletingNonQaUser() throws Exception {
		UUID userId = insertNonQaUserWithQaNickname();

		mockMvc.perform(delete("/api/dev/qa/users/{userId}", userId))
			.andExpect(status().isBadRequest());

		assertThat(queryInteger("select count(*) from users where id = ?", userId)).isOne();
		assertThat(queryInteger("select count(*) from user_profiles where user_id = ?", userId)).isOne();
	}

	private UUID insertNonQaUserWithQaNickname() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId,
			now,
			now
		);
		jdbcTemplate.update(
			"""
			insert into user_profiles (
				user_id, nickname, mascot_name, timezone, profile_image_url, notification_enabled, created_at, updated_at
			)
			values (?, 'QA너굴99999', '너구리', 'Asia/Seoul', null, true, ?, ?)
			""",
			userId,
			now,
			now
		);
		return userId;
	}

	private UUID insertNonQaDueReminder(UUID userId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UUID itemId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into saved_items (
				id, user_id, input_source, title, listed_price, currency_code, category,
				category_locked_by_user, status, created_at, updated_at
			)
			values (?, ?, 'DIRECT_INPUT', '일반 유저 회고 상품', 10000, 'KRW', 'DIGITAL', false, 'GO', ?, ?)
			""",
			itemId,
			userId,
			now,
			now
		);
		UUID decisionId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into purchase_decisions (
				id, user_id, item_id, budget_cycle_id, result, final_price, rationality_result,
				self_check_yes_count, is_changed, change_count, decided_at, created_at, updated_at
			)
			values (?, ?, ?, null, 'GO', 10000, 'RATIONAL', 1, false, 0, ?, ?, ?)
			""",
			decisionId,
			userId,
			itemId,
			now.minusDays(7),
			now.minusDays(7),
			now.minusDays(7)
		);
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
			now.minusMinutes(1),
			now,
			now
		);
		return reminderId;
	}

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	private String queryString(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}
}
