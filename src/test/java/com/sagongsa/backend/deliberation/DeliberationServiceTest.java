package com.sagongsa.backend.deliberation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class DeliberationServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private DeliberationService deliberationService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── TC1: 접근 제어 ──────────────────────────────────────────────────────

	@Test
	void throwsNotFoundWhenUserDoesNotExist() {
		assertThatThrownBy(() -> deliberationService.getSummary(UUID.randomUUID(), UUID.randomUUID()))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void throwsForbiddenWhenOnboardingNotCompleted() {
		UUID userId = insertUser("NOT_STARTED");
		assertThatThrownBy(() -> deliberationService.getSummary(userId, UUID.randomUUID()))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
	}

	@Test
	void throwsForbiddenWhenUserIsSuspended() {
		UUID userId = insertUser("SUSPENDED", "COMPLETED");
		assertThatThrownBy(() -> deliberationService.getSummary(userId, UUID.randomUUID()))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
	}

	// ── TC2: 아이템 상태 검증 ───────────────────────────────────────────────

	@Test
	void throwsNotFoundWhenItemDoesNotExist() {
		UUID userId = insertActiveUser();
		assertThatThrownBy(() -> deliberationService.getSummary(userId, UUID.randomUUID()))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void throwsConflictWhenItemIsDropped() {
		UUID userId = insertActiveUser();
		UUID itemId = insertSavedItem(userId, "DIGITAL", 30000, "DROPPED");
		assertThatThrownBy(() -> deliberationService.getSummary(userId, itemId))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void returnsSummaryForSavedItem() {
		UUID userId = insertActiveUser();
		UUID itemId = insertSavedItem(userId, "DIGITAL", 50000, "SAVED");
		DeliberationSummaryResponse response = deliberationService.getSummary(userId, itemId);
		assertThat(response.item().id()).isEqualTo(itemId);
		assertThat(response.item().listedPrice()).isEqualTo(50000);
	}

	// ── TC3: 예산 계산 ─────────────────────────────────────────────────────

	@Test
	void returnZeroBudgetWhenNoBudgetCycleExists() {
		UUID userId = insertActiveUser();
		UUID itemId = insertSavedItem(userId, "DIGITAL", 10000, "SAVED");
		DeliberationSummaryResponse response = deliberationService.getSummary(userId, itemId);

		assertThat(response.budget().monthlyBudgetAmount()).isZero();
		assertThat(response.budget().spentAmount()).isZero();
		assertThat(response.budget().projectedUsageRate()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void calculatesProjectedSpendAsSpentPlusItemPrice() {
		UUID userId = insertActiveUser();
		UUID itemId = insertSavedItem(userId, "DIGITAL", 30000, "SAVED");
		String yearMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
		insertBudgetCycle(userId, yearMonth, 200000, 50000);

		DeliberationSummaryResponse response = deliberationService.getSummary(userId, itemId);

		assertThat(response.budget().spentAmount()).isEqualTo(50000);
		assertThat(response.budget().projectedSpentAmount()).isEqualTo(80000);
	}

	@Test
	void calculatesUsageRateCorrectly() {
		UUID userId = insertActiveUser();
		UUID itemId = insertSavedItem(userId, "DIGITAL", 0, "SAVED");
		String yearMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
		insertBudgetCycle(userId, yearMonth, 100000, 50000);

		DeliberationSummaryResponse response = deliberationService.getSummary(userId, itemId);

		assertThat(response.budget().projectedUsageRate()).isEqualByComparingTo("50.00");
	}

	@Test
	void returnsZeroUsageRateWhenMonthlyBudgetIsZero() {
		UUID userId = insertActiveUser();
		UUID itemId = insertSavedItem(userId, "DIGITAL", 10000, "SAVED");
		String yearMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
		insertBudgetCycle(userId, yearMonth, 0, 0);

		DeliberationSummaryResponse response = deliberationService.getSummary(userId, itemId);

		assertThat(response.budget().projectedUsageRate()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	// ── TC4: 기회비용 메시지 ─────────────────────────────────────────────────

	@Test
	void returnsPlaceholderMessageWhenPriceIsNull() {
		UUID userId = insertActiveUser();
		UUID itemId = insertSavedItem(userId, "DIGITAL", null, "SAVED");
		DeliberationSummaryResponse response = deliberationService.getSummary(userId, itemId);
		assertThat(response.opportunityCostMessage())
			.isIn(DeliberationService.NO_PRICE_OPPORTUNITY_COST_MESSAGES);
	}

	@Test
	void returnsPlaceholderMessageWhenPriceIsZero() {
		UUID userId = insertActiveUser();
		UUID itemId = insertSavedItem(userId, "DIGITAL", 0, "SAVED");
		DeliberationSummaryResponse response = deliberationService.getSummary(userId, itemId);
		assertThat(response.opportunityCostMessage())
			.isIn(DeliberationService.NO_PRICE_OPPORTUNITY_COST_MESSAGES);
	}

	@Test
	void returnsPriceFormattedMessageWhenPriceIsPositive() {
		UUID userId = insertActiveUser();
		UUID itemId = insertSavedItem(userId, "DIGITAL", 39000, "SAVED");
		DeliberationSummaryResponse response = deliberationService.getSummary(userId, itemId);
		assertThat(response.opportunityCostMessage())
			.isIn(DeliberationService.PRICE_OPPORTUNITY_COST_MESSAGE_FORMATS.stream()
				.map(format -> format.formatted(39000))
				.toList());
	}

	// ── TC5: 유사 카테고리 소비 집계 ────────────────────────────────────────

	@Test
	void aggregatesOnlyGoDecisionsInSameCategory() {
		UUID userId = insertActiveUser();
		UUID targetItemId = insertSavedItem(userId, "FASHION", 50000, "SAVED");
		String yearMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
		insertBudgetCycle(userId, yearMonth, 300000, 0);

		UUID goItem = insertSavedItem(userId, "FASHION", null, "GO");
		UUID stopItem = insertSavedItem(userId, "FASHION", null, "STOP");
		UUID otherCategoryItem = insertSavedItem(userId, "DIGITAL", null, "GO");

		insertPurchaseDecision(userId, goItem, "GO", 30000);
		insertPurchaseDecision(userId, stopItem, "STOP", 20000);
		insertPurchaseDecision(userId, otherCategoryItem, "GO", 40000);

		DeliberationSummaryResponse response = deliberationService.getSummary(userId, targetItemId);

		assertThat(response.similarCategorySpendAmount()).isEqualTo(30000);
	}

	@Test
	void returnsZeroWhenNoSimilarCategoryDecisions() {
		UUID userId = insertActiveUser();
		UUID itemId = insertSavedItem(userId, "FASHION", 10000, "SAVED");

		DeliberationSummaryResponse response = deliberationService.getSummary(userId, itemId);

		assertThat(response.similarCategorySpendAmount()).isZero();
	}

	// ── TC6: 타임존 처리 ───────────────────────────────────────────────────

	@Test
	void fallsBackToSeoulWhenTimezoneIsInvalid() {
		UUID userId = insertActiveUser();
		insertUserProfileWithTimezone(userId, "INVALID/ZONE");
		UUID itemId = insertSavedItem(userId, "DIGITAL", 10000, "SAVED");

		String expectedYearMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
		DeliberationSummaryResponse response = deliberationService.getSummary(userId, itemId);

		assertThat(response.budget().yearMonth()).isEqualTo(expectedYearMonth);
	}

	// ── 헬퍼 ─────────────────────────────────────────────────────────────────

	private UUID insertActiveUser() {
		return insertUser("ACTIVE", "COMPLETED");
	}

	private UUID insertUser(String onboardingStatus) {
		return insertUser("ACTIVE", onboardingStatus);
	}

	private UUID insertUser(String status, String onboardingStatus) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"insert into users (id, status, onboarding_status, created_at, updated_at) values (?, ?, ?, ?, ?)",
			userId, status, onboardingStatus, now, now
		);
		return userId;
	}

	private UUID insertSavedItem(UUID userId, String category, Integer listedPrice, String status) {
		UUID itemId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into saved_items (id, user_id, input_source, normalized_url, title, listed_price, category, status, created_at, updated_at)
			values (?, ?, 'SHARE', ?, '테스트 상품', ?, ?, ?, ?, ?)
			""",
			itemId, userId, "https://shop.example.com/" + itemId, listedPrice, category, status, now, now
		);
		return itemId;
	}

	private void insertBudgetCycle(UUID userId, String yearMonth, int monthlyBudget, int spentAmount) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into budget_cycles (id, user_id, year_month, monthly_budget_amount, spent_amount, warning_threshold_rate, created_at, updated_at)
			values (?, ?, ?, ?, ?, 80.00, ?, ?)
			""",
			UUID.randomUUID(), userId, yearMonth, monthlyBudget, spentAmount, now, now
		);
	}

	private void insertPurchaseDecision(UUID userId, UUID itemId, String result, Integer finalPrice) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into purchase_decisions (id, user_id, item_id, result, final_price, rationality_result, self_check_yes_count, decided_at, created_at, updated_at)
			values (?, ?, ?, ?, ?, 'RATIONAL', 0, ?, ?, ?)
			""",
			UUID.randomUUID(), userId, itemId, result, finalPrice, now, now, now
		);
	}

	private void insertUserProfileWithTimezone(UUID userId, String timezone) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into user_profiles (user_id, nickname, mascot_name, timezone, created_at, updated_at)
			values (?, '너굴1', '위굴', ?, ?, ?)
			""",
			userId, timezone, now, now
		);
	}
}
