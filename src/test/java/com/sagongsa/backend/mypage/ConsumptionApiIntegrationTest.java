package com.sagongsa.backend.mypage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ConsumptionApiIntegrationTest extends PostgreSqlContainerTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String BASE_PATH = "/api/v1/my/consumption";
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void getMonthlyConsumption_returnsDecisionsForMonth() throws Exception {
		UUID userId = createUser();
		String month = YearMonth.now(SEOUL).toString();
		UUID budgetCycleId = insertBudgetCycle(userId, month, 500_000, 35_000);
		UUID decisionId = insertGoDecision(userId, budgetCycleId, "에어팟 프로", 35_000);

		mockMvc.perform(get(BASE_PATH)
				.header(USER_ID_HEADER, userId)
				.param("month", month))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.month").value(month))
			.andExpect(jsonPath("$.items").isArray())
			.andExpect(jsonPath("$.items[0].id").value(decisionId.toString()))
			.andExpect(jsonPath("$.items[0].itemTitle").value("에어팟 프로"))
			.andExpect(jsonPath("$.items[0].price").value(35_000))
			.andExpect(jsonPath("$.items[0].result").value("GO"))
			.andExpect(jsonPath("$.items[0].isChanged").value(false));
	}

	@Test
	void getMonthlyConsumption_returnsEmptyListWhenNoDecisions() throws Exception {
		UUID userId = createUser();
		String month = YearMonth.now(SEOUL).toString();

		mockMvc.perform(get(BASE_PATH)
				.header(USER_ID_HEADER, userId)
				.param("month", month))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.month").value(month))
			.andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void getMonthlyConsumption_rejectsBadMonthFormat() throws Exception {
		UUID userId = createUser();

		mockMvc.perform(get(BASE_PATH)
				.header(USER_ID_HEADER, userId)
				.param("month", "2026/05"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void changeDecision_goToStop_syncsBudgetAndStatus() throws Exception {
		UUID userId = createUser();
		String month = YearMonth.now(SEOUL).toString();
		UUID budgetCycleId = insertBudgetCycle(userId, month, 500_000, 35_000);
		UUID decisionId = insertGoDecision(userId, budgetCycleId, "운동화", 35_000);

		mockMvc.perform(patch(BASE_PATH + "/{id}", decisionId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"result": "STOP"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(decisionId.toString()))
			.andExpect(jsonPath("$.result").value("STOP"))
			.andExpect(jsonPath("$.isChanged").value(true))
			.andExpect(jsonPath("$.changeCount").value(1));

		assertThat(queryString("SELECT result FROM purchase_decisions WHERE id = ?", decisionId)).isEqualTo("STOP");
		assertThat(queryString("SELECT status FROM saved_items si JOIN purchase_decisions pd ON si.id = pd.item_id WHERE pd.id = ?", decisionId)).isEqualTo("STOP");
		assertThat(queryInteger("SELECT spent_amount FROM budget_cycles WHERE id = ?", budgetCycleId)).isEqualTo(0);
	}

	@Test
	void changeDecision_stopToGo_syncsBudgetAndStatus() throws Exception {
		UUID userId = createUser();
		String month = YearMonth.now(SEOUL).toString();
		UUID budgetCycleId = insertBudgetCycle(userId, month, 500_000, 0);
		UUID decisionId = insertStopDecision(userId, budgetCycleId, "책상", 120_000);

		mockMvc.perform(patch(BASE_PATH + "/{id}", decisionId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"result": "GO"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("GO"))
			.andExpect(jsonPath("$.isChanged").value(true));

		assertThat(queryString("SELECT result FROM purchase_decisions WHERE id = ?", decisionId)).isEqualTo("GO");
		assertThat(queryInteger("SELECT spent_amount FROM budget_cycles WHERE id = ?", budgetCycleId)).isEqualTo(120_000);
	}

	@Test
	void changeDecision_notFound_returns404() throws Exception {
		UUID userId = createUser();

		mockMvc.perform(patch(BASE_PATH + "/{id}", UUID.randomUUID())
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"result": "STOP"}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void changeDecision_invalidResult_returns400() throws Exception {
		UUID userId = createUser();
		String month = YearMonth.now(SEOUL).toString();
		UUID budgetCycleId = insertBudgetCycle(userId, month, 500_000, 10_000);
		UUID decisionId = insertGoDecision(userId, budgetCycleId, "키보드", 10_000);

		mockMvc.perform(patch(BASE_PATH + "/{id}", decisionId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"result": "MAYBE"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	private UUID createUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO users (id, status, onboarding_status, created_at, updated_at) VALUES (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId, now, now
		);
		jdbcTemplate.update(
			"INSERT INTO user_profiles (user_id, nickname, mascot_name, timezone, created_at, updated_at) VALUES (?, '너굴1', '너구리', 'Asia/Seoul', ?, ?)",
			userId, now, now
		);
		return userId;
	}

	private UUID insertBudgetCycle(UUID userId, String yearMonth, int monthlyBudget, int spentAmount) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO budget_cycles (id, user_id, year_month, monthly_budget_amount, spent_amount, warning_threshold_rate, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 80.00, ?, ?)",
			id, userId, yearMonth, monthlyBudget, spentAmount, now, now
		);
		return id;
	}

	private UUID insertGoDecision(UUID userId, UUID budgetCycleId, String itemTitle, int price) {
		UUID itemId = insertSavedItem(userId, itemTitle, price, "GO");
		UUID decisionId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO purchase_decisions (id, user_id, item_id, budget_cycle_id, result, final_price, rationality_result, self_check_yes_count, is_changed, change_count, decided_at, created_at, updated_at) VALUES (?, ?, ?, ?, 'GO', ?, 'RATIONAL', 0, false, 0, ?, ?, ?)",
			decisionId, userId, itemId, budgetCycleId, price, now, now, now
		);
		return decisionId;
	}

	private UUID insertStopDecision(UUID userId, UUID budgetCycleId, String itemTitle, int price) {
		UUID itemId = insertSavedItem(userId, itemTitle, price, "STOP");
		UUID decisionId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO purchase_decisions (id, user_id, item_id, budget_cycle_id, result, final_price, rationality_result, self_check_yes_count, is_changed, change_count, decided_at, created_at, updated_at) VALUES (?, ?, ?, ?, 'STOP', ?, 'RATIONAL', 0, false, 0, ?, ?, ?)",
			decisionId, userId, itemId, budgetCycleId, price, now, now, now
		);
		return decisionId;
	}

	private UUID insertSavedItem(UUID userId, String title, int price, String status) {
		UUID itemId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO saved_items (id, user_id, input_source, title, listed_price, currency_code, category, category_locked_by_user, status, created_at, updated_at) VALUES (?, ?, 'DIRECT_INPUT', ?, ?, 'KRW', 'DIGITAL', false, ?, ?, ?)",
			itemId, userId, title, price, status, now, now
		);
		return itemId;
	}

	private String queryString(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}
}
