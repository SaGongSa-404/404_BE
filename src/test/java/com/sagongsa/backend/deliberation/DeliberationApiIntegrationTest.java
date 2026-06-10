package com.sagongsa.backend.deliberation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DeliberationApiIntegrationTest extends PostgreSqlContainerTest {

	private static final String BASE = "/api/v1/deliberations/items";
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── 정상 응답 ─────────────────────────────────────────────────────────

	@Test
	void 숙려_요약_조회_200_응답_필드_확인() throws Exception {
		UUID userId = insertUser("ACTIVE", "COMPLETED");
		UUID itemId = insertSavedItem(userId, "에어팟 프로", 350_000, "DIGITAL", "SAVED");

		mockMvc.perform(get(BASE + "/{itemId}", itemId)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.item").exists())
			.andExpect(jsonPath("$.item.id").value(itemId.toString()))
			.andExpect(jsonPath("$.item.title").value("에어팟 프로"))
			.andExpect(jsonPath("$.item.listedPrice").value(350_000))
			.andExpect(jsonPath("$.budget").exists())
			.andExpect(jsonPath("$.opportunityCostMessage").isString())
			.andExpect(jsonPath("$.similarCategorySpendAmount").isNumber());
	}

	@Test
	void 예산_사이클_있으면_예산_정보_포함() throws Exception {
		UUID userId = insertUser("ACTIVE", "COMPLETED");
		UUID itemId = insertSavedItem(userId, "맥북", 1_500_000, "DIGITAL", "SAVED");
		insertBudgetCycle(userId, YearMonth.now(KST).toString(), 2_000_000, 300_000);

		mockMvc.perform(get(BASE + "/{itemId}", itemId)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget.monthlyBudgetAmount").value(2_000_000))
			.andExpect(jsonPath("$.budget.spentAmount").value(300_000));
	}

	@Test
	void 가격_있으면_기회비용_메시지에_금액_포함() throws Exception {
		UUID userId = insertUser("ACTIVE", "COMPLETED");
		UUID itemId = insertSavedItem(userId, "운동화", 120_000, "FASHION", "SAVED");

		mockMvc.perform(get(BASE + "/{itemId}", itemId)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.opportunityCostMessage").value(containsString("120,000원")));
	}

	@Test
	void 가격_없으면_기회비용_안내_메시지() throws Exception {
		UUID userId = insertUser("ACTIVE", "COMPLETED");
		UUID itemId = insertSavedItem(userId, "가격_미입력_상품", null, "FASHION", "SAVED");

		mockMvc.perform(get(BASE + "/{itemId}", itemId)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.opportunityCostMessage").isString());
	}

	// ── 접근 제어 ─────────────────────────────────────────────────────────

	@Test
	void 존재하지_않는_유저_404() throws Exception {
		mockMvc.perform(get(BASE + "/{itemId}", UUID.randomUUID())
				.header("X-User-Id", UUID.randomUUID()))
			.andExpect(status().isNotFound());
	}

	@Test
	void 온보딩_미완료_유저_403() throws Exception {
		UUID userId = insertUser("ACTIVE", "NOT_STARTED");
		UUID itemId = insertSavedItem(userId, "상품", 10_000, "DIGITAL", "SAVED");

		mockMvc.perform(get(BASE + "/{itemId}", itemId)
				.header("X-User-Id", userId))
			.andExpect(status().isForbidden());
	}

	@Test
	void SUSPENDED_유저_403() throws Exception {
		UUID userId = insertUser("SUSPENDED", "COMPLETED");
		UUID itemId = insertSavedItem(userId, "상품", 10_000, "DIGITAL", "SAVED");

		mockMvc.perform(get(BASE + "/{itemId}", itemId)
				.header("X-User-Id", userId))
			.andExpect(status().isForbidden());
	}

	// ── 아이템 상태 검증 ──────────────────────────────────────────────────

	@Test
	void 존재하지_않는_아이템_404() throws Exception {
		UUID userId = insertUser("ACTIVE", "COMPLETED");

		mockMvc.perform(get(BASE + "/{itemId}", UUID.randomUUID())
				.header("X-User-Id", userId))
			.andExpect(status().isNotFound());
	}

	@Test
	void DROPPED_아이템_409() throws Exception {
		UUID userId = insertUser("ACTIVE", "COMPLETED");
		UUID itemId = insertSavedItem(userId, "포기한 상품", 50_000, "DIGITAL", "DROPPED");

		mockMvc.perform(get(BASE + "/{itemId}", itemId)
				.header("X-User-Id", userId))
			.andExpect(status().isConflict());
	}

	@Test
	void 타인_아이템_접근_404() throws Exception {
		UUID owner = insertUser("ACTIVE", "COMPLETED");
		UUID other = insertUser("ACTIVE", "COMPLETED");
		UUID itemId = insertSavedItem(owner, "타인 상품", 30_000, "DIGITAL", "SAVED");

		mockMvc.perform(get(BASE + "/{itemId}", itemId)
				.header("X-User-Id", other))
			.andExpect(status().isNotFound());
	}

	// ── 헬퍼 ─────────────────────────────────────────────────────────────

	private UUID insertUser(String status, String onboardingStatus) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO users (id, status, onboarding_status, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
			userId, status, onboardingStatus, now, now);
		jdbcTemplate.update(
			"INSERT INTO user_profiles (user_id, nickname, mascot_name, timezone, created_at, updated_at) VALUES (?, '너굴이', '너구리', 'Asia/Seoul', ?, ?)",
			userId, now, now);
		return userId;
	}

	private UUID insertSavedItem(UUID userId, String title, Integer price, String category, String status) {
		UUID itemId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			INSERT INTO saved_items (id, user_id, input_source, title, listed_price, currency_code, category, category_locked_by_user, status, created_at, updated_at)
			VALUES (?, ?, 'DIRECT_INPUT', ?, ?, 'KRW', ?, false, ?, ?, ?)
			""",
			itemId, userId, title, price, category, status, now, now);
		return itemId;
	}

	private void insertBudgetCycle(UUID userId, String yearMonth, int monthlyBudget, int spentAmount) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO budget_cycles (id, user_id, year_month, monthly_budget_amount, spent_amount, warning_threshold_rate, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 80.00, ?, ?)",
			UUID.randomUUID(), userId, yearMonth, monthlyBudget, spentAmount, now, now);
	}
}
