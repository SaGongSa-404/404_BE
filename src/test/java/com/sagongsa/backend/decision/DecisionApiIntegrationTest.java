package com.sagongsa.backend.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DecisionApiIntegrationTest extends PostgreSqlContainerTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String DECISIONS_PATH = "/api/v1/decisions";
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
	void completesGoDecisionAndSchedulesReminder() throws Exception {
		UUID userId = createReadyUser();
		String yearMonth = YearMonth.now(SEOUL_ZONE).toString();
		insertBudgetCycle(userId, yearMonth, 500_000, 90_000);
		UUID itemId = insertSavedItem(userId, "Decision target", "FASHION", "SAVED", 15_000);
		insertPreviousGoDecision(userId, "Previous fashion", "FASHION", 20_000);

		mockMvc.perform(post(DECISIONS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionRequest(itemId, "GO", null, true, false, false, false)))
			.andExpect(status().isCreated())
			.andExpect(header().exists(HttpHeaders.LOCATION))
			.andExpect(jsonPath("$.itemId").value(itemId.toString()))
			.andExpect(jsonPath("$.itemStatus").value("GO"))
			.andExpect(jsonPath("$.result").value("GO"))
			.andExpect(jsonPath("$.finalPrice").value(15_000))
			.andExpect(jsonPath("$.budgetAfterAmount").value(105_000))
			.andExpect(jsonPath("$.similarCategorySpendAmount").value(20_000))
			.andExpect(jsonPath("$.selfCheckYesCount").value(1))
			.andExpect(jsonPath("$.rationalityResult").value("RATIONAL"))
			.andExpect(jsonPath("$.mascot.state").value("SMILE"))
			.andExpect(jsonPath("$.mascot.message").value("합리적으로 잘 결정했어요"))
			.andExpect(jsonPath("$.reminder.status").value("SCHEDULED"))
			.andExpect(jsonPath("$.resultMessage").value("7일 뒤에 어떤지 물어볼게!"));

		assertThat(queryString("select status from saved_items where id = ?", itemId)).isEqualTo("GO");
		assertThat(queryInteger("select spent_amount from budget_cycles where user_id = ? and year_month = ?", userId, yearMonth))
			.isEqualTo(105_000);
		assertThat(queryInteger("select count(*) from purchase_decisions where item_id = ?", itemId)).isEqualTo(1);
		assertThat(queryInteger("select count(*) from self_check_answers")).isEqualTo(4);
		assertThat(queryInteger("select count(*) from reminder_schedules where item_id = ?", itemId)).isEqualTo(1);
		assertThat(queryString("select mascot_state from mascot_profiles where user_id = ?", userId)).isEqualTo("SMILE");
		assertThat(queryInteger("select count(*) from mascot_state_events where item_id = ?", itemId)).isEqualTo(1);
	}

	@Test
	void returnsExistingDecisionResultWhenCompletingSameItemAgain() throws Exception {
		UUID userId = createReadyUser();
		insertBudgetCycle(userId, YearMonth.now(SEOUL_ZONE).toString(), 500_000, 90_000);
		UUID itemId = insertSavedItem(userId, "Idempotent target", "FASHION", "SAVED", 15_000);

		String firstBody = mockMvc.perform(post(DECISIONS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionRequest(itemId, "GO", null, true, false, false, false)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String firstDecisionId = objectMapper.readTree(firstBody).get("decisionId").asText();

		String secondBody = mockMvc.perform(post(DECISIONS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionRequest(itemId, "GO", null, true, false, false, false)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String secondDecisionId = objectMapper.readTree(secondBody).get("decisionId").asText();

		assertThat(secondDecisionId).isEqualTo(firstDecisionId);
		assertThat(queryInteger("select count(*) from purchase_decisions where item_id = ?", itemId)).isEqualTo(1);
	}

	@Test
	void completesStopIrrationalDecisionWithoutBudgetOrReminder() throws Exception {
		UUID userId = createReadyUser();
		String yearMonth = YearMonth.now(SEOUL_ZONE).toString();
		insertBudgetCycle(userId, yearMonth, 500_000, 90_000);
		UUID itemId = insertSavedItem(userId, "Stop target", "BEAUTY", "SAVED", 30_000);

		mockMvc.perform(post(DECISIONS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionRequest(itemId, "STOP", null, true, true, false, false)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.itemStatus").value("STOP"))
			.andExpect(jsonPath("$.result").value("STOP"))
			.andExpect(jsonPath("$.finalPrice").value(nullValue()))
			.andExpect(jsonPath("$.budgetAfterAmount").value(90_000))
			.andExpect(jsonPath("$.selfCheckYesCount").value(2))
			.andExpect(jsonPath("$.rationalityResult").value("IRRATIONAL"))
			.andExpect(jsonPath("$.mascot.state").value("VERY_HAPPY"))
			.andExpect(jsonPath("$.reminder").value(nullValue()));

		assertThat(queryString("select status from saved_items where id = ?", itemId)).isEqualTo("STOP");
		assertThat(queryInteger("select spent_amount from budget_cycles where user_id = ? and year_month = ?", userId, yearMonth))
			.isEqualTo(90_000);
		assertThat(queryInteger("select count(*) from reminder_schedules where item_id = ?", itemId)).isZero();
	}

	@Test
	void getsDecisionResult() throws Exception {
		UUID userId = createReadyUser();
		insertBudgetCycle(userId, YearMonth.now(SEOUL_ZONE).toString(), 500_000, 0);
		UUID itemId = insertSavedItem(userId, "Readable target", "FOOD", "SAVED", 12_000);

		String body = mockMvc.perform(post(DECISIONS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionRequest(itemId, "GO", 12_000, false, false, false, false)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode json = objectMapper.readTree(body);
		String decisionId = json.get("decisionId").asText();
		jdbcTemplate.update(
			"""
			update mascot_profiles
			   set mascot_state = 'SAD',
			       last_reaction_message = '다른 결정의 최신 반응',
			       updated_at = ?
			 where user_id = ?
			""",
			OffsetDateTime.now(ZoneOffset.UTC),
			userId
		);

		mockMvc.perform(get(DECISIONS_PATH + "/{decisionId}/result", decisionId)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.decisionId").value(decisionId))
			.andExpect(jsonPath("$.itemTitle").value("Readable target"))
			.andExpect(jsonPath("$.rationalityResult").value("RATIONAL"))
			.andExpect(jsonPath("$.mascot.state").value("SMILE"))
			.andExpect(jsonPath("$.mascot.message").value("합리적으로 잘 결정했어요"))
			.andExpect(jsonPath("$.reminder.status").value("SCHEDULED"));
	}

	@Test
	void doesNotReactivateSentReminderWhenDecisionChangesBackToGo() throws Exception {
		UUID userId = createReadyUser();
		insertBudgetCycle(userId, YearMonth.now(SEOUL_ZONE).toString(), 500_000, 0);
		UUID itemId = insertSavedItem(userId, "Already reminded target", "FOOD", "SAVED", 12_000);

		String body = mockMvc.perform(post(DECISIONS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionRequest(itemId, "GO", 12_000, false, false, false, false)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String decisionId = objectMapper.readTree(body).get("decisionId").asText();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			update reminder_schedules
			   set status = 'SENT',
			       sent_at = ?,
			       updated_at = ?
			 where decision_id = ?
			""",
			now,
			now,
			UUID.fromString(decisionId)
		);

		mockMvc.perform(patch(DECISIONS_PATH + "/{decisionId}/result", decisionId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"result": "STOP",
					"changeReason": "마음이 바뀜"
				}
				"""))
			.andExpect(status().isOk());

		mockMvc.perform(patch(DECISIONS_PATH + "/{decisionId}/result", decisionId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"result": "GO",
					"finalPrice": 12000,
					"changeReason": "다시 구매로 변경"
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reminder.status").value("SENT"));

		assertThat(queryString("select status from reminder_schedules where decision_id = ?", UUID.fromString(decisionId))).isEqualTo("SENT");
		assertThat(queryInteger("select count(*) from reminder_schedules where decision_id = ?", UUID.fromString(decisionId))).isEqualTo(1);
	}

	@Test
	void rejectsDuplicatedSelfCheckQuestion() throws Exception {
		UUID userId = createReadyUser();
		insertBudgetCycle(userId, YearMonth.now(SEOUL_ZONE).toString(), 500_000, 0);
		UUID itemId = insertSavedItem(userId, "Bad self-check target", "LIVING", "SAVED", 50_000);

		mockMvc.perform(post(DECISIONS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"itemId": "%s",
					"result": "GO",
					"selfCheckAnswers": [
						{"questionCode": "NEED", "answerBoolean": true},
						{"questionCode": "NEED", "answerBoolean": false},
						{"questionCode": "BUDGET", "answerBoolean": false},
						{"questionCode": "DELAY", "answerBoolean": false}
					]
				}
				""".formatted(itemId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void rejectsUnknownSelfCheckQuestionCode() throws Exception {
		UUID userId = createReadyUser();
		insertBudgetCycle(userId, YearMonth.now(SEOUL_ZONE).toString(), 500_000, 0);
		UUID itemId = insertSavedItem(userId, "Bad question target", "LIVING", "SAVED", 50_000);

		mockMvc.perform(post(DECISIONS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"itemId": "%s",
					"result": "GO",
					"selfCheckAnswers": [
						{"questionCode": "NEED", "answerBoolean": true},
						{"questionCode": "BUDGET", "answerBoolean": false},
						{"questionCode": "UNKNOWN", "answerBoolean": false},
						{"questionCode": "DELAY", "answerBoolean": false}
					]
				}
				""".formatted(itemId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void blocksNonSavedItemDecision() throws Exception {
		UUID userId = createReadyUser();
		insertBudgetCycle(userId, YearMonth.now(SEOUL_ZONE).toString(), 500_000, 0);
		UUID itemId = insertSavedItem(userId, "Already decided target", "DIGITAL", "GO", 100_000);

		mockMvc.perform(post(DECISIONS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionRequest(itemId, "GO", 100_000, false, false, false, false)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	private String decisionRequest(UUID itemId, String result, Integer finalPrice, boolean first, boolean second, boolean third, boolean fourth) {
		String finalPriceField = finalPrice == null ? "" : "\"finalPrice\": %d,".formatted(finalPrice);
		return """
			{
				"itemId": "%s",
				"result": "%s",
				%s
				"selfCheckAnswers": [
					{"questionCode": "NEED", "answerBoolean": %s},
					{"questionCode": "BUDGET", "answerBoolean": %s},
					{"questionCode": "ALTERNATIVE", "answerBoolean": %s},
					{"questionCode": "DELAY", "answerBoolean": %s}
				]
			}
			""".formatted(itemId, result, finalPriceField, first, second, third, fourth);
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
			insert into mascot_profiles (
				user_id, mascot_state, last_state_changed_at, updated_at
			)
			values (?, 'DEFAULT', ?, ?)
			""",
			userId,
			now,
			now
		);
		return userId;
	}

	private void insertBudgetCycle(UUID userId, String yearMonth, int monthlyBudgetAmount, int spentAmount) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into budget_cycles (
				id, user_id, year_month, monthly_budget_amount, spent_amount,
				warning_threshold_rate, created_at, updated_at
			)
			values (?, ?, ?, ?, ?, 80.00, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			yearMonth,
			monthlyBudgetAmount,
			spentAmount,
			now,
			now
		);
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

	private void insertPreviousGoDecision(UUID userId, String title, String category, int finalPrice) {
		UUID itemId = insertSavedItem(userId, title, category, "GO", finalPrice);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
		jdbcTemplate.update(
			"""
			insert into purchase_decisions (
				id, user_id, item_id, result, final_price, rationality_result,
				self_check_yes_count, decided_at, created_at, updated_at
			)
			values (?, ?, ?, 'GO', ?, 'RATIONAL', 0, ?, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			itemId,
			finalPrice,
			now,
			now,
			now
		);
	}

	private String queryString(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}
}
