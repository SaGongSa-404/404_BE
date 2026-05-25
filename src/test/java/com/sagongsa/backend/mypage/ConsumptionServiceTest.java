package com.sagongsa.backend.mypage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ConsumptionServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private ConsumptionService consumptionService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── TC1: month 파라미터 검증 ──────────────────────────────────────────

	@Test
	void rejectsNullMonth() {
		UUID userId = insertUser();
		assertThatThrownBy(() -> consumptionService.getMonthlyConsumption(userId, null))
			.isInstanceOf(ConsumptionBadRequestException.class);
	}

	@Test
	void rejectsBlankMonth() {
		UUID userId = insertUser();
		assertThatThrownBy(() -> consumptionService.getMonthlyConsumption(userId, "  "))
			.isInstanceOf(ConsumptionBadRequestException.class);
	}

	@Test
	void rejectsMonthWithoutLeadingZero() {
		UUID userId = insertUser();
		assertThatThrownBy(() -> consumptionService.getMonthlyConsumption(userId, "2026-5"))
			.isInstanceOf(ConsumptionBadRequestException.class);
	}

	@Test
	void rejectsMonthWithSlashSeparator() {
		UUID userId = insertUser();
		assertThatThrownBy(() -> consumptionService.getMonthlyConsumption(userId, "2026/05"))
			.isInstanceOf(ConsumptionBadRequestException.class);
	}

	@Test
	void acceptsValidYearMonthFormat() {
		UUID userId = insertUser();
		ConsumptionListResponse response = consumptionService.getMonthlyConsumption(userId, "2026-05");
		assertThat(response.month()).isEqualTo("2026-05");
		assertThat(response.items()).isEmpty();
	}

	// ── TC2: 월별 소비 내역 조회 ─────────────────────────────────────────

	@Test
	void returnsEmptyListWhenNoDecisionsInMonth() {
		UUID userId = insertUser();
		String month = currentMonth();
		ConsumptionListResponse response = consumptionService.getMonthlyConsumption(userId, month);
		assertThat(response.items()).isEmpty();
	}

	@Test
	void returnsBothGoAndStopDecisions() {
		UUID userId = insertUser();
		String month = currentMonth();
		UUID budgetCycleId = insertBudgetCycle(userId, month);
		insertDecision(userId, budgetCycleId, "GO", 10000);
		insertDecision(userId, budgetCycleId, "STOP", 20000);

		ConsumptionListResponse response = consumptionService.getMonthlyConsumption(userId, month);

		assertThat(response.items()).hasSize(2);
		assertThat(response.items().stream().map(ConsumptionRecord::result))
			.containsExactlyInAnyOrder("GO", "STOP");
	}

	@Test
	void returnsDecisionsInDescendingOrder() {
		UUID userId = insertUser();
		String month = currentMonth();
		UUID budgetCycleId = insertBudgetCycle(userId, month);

		OffsetDateTime earlier = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
		OffsetDateTime later = OffsetDateTime.now(ZoneOffset.UTC);

		UUID firstDecision = insertDecisionAt(userId, budgetCycleId, "GO", 10000, later, "나중 상품");
		UUID secondDecision = insertDecisionAt(userId, budgetCycleId, "GO", 20000, earlier, "이전 상품");

		ConsumptionListResponse response = consumptionService.getMonthlyConsumption(userId, month);

		assertThat(response.items().get(0).id()).isEqualTo(firstDecision);
		assertThat(response.items().get(1).id()).isEqualTo(secondDecision);
	}

	@Test
	void excludesDecisionsFromOtherMonths() {
		UUID userId = insertUser();
		String thisMonth = currentMonth();
		String lastMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).minusMonths(1).toString();

		UUID thisCycleId = insertBudgetCycle(userId, thisMonth);
		UUID lastCycleId = insertBudgetCycle(userId, lastMonth);

		insertDecision(userId, thisCycleId, "GO", 10000);
		insertDecision(userId, lastCycleId, "STOP", 20000);

		ConsumptionListResponse response = consumptionService.getMonthlyConsumption(userId, thisMonth);

		assertThat(response.items()).hasSize(1);
		assertThat(response.items().getFirst().result()).isEqualTo("GO");
	}

	// ── TC3: 결정 결과 변경 ──────────────────────────────────────────────

	@Test
	void changesResultAndSetsIsChangedTrue() {
		UUID userId = insertUser();
		String month = currentMonth();
		UUID budgetCycleId = insertBudgetCycle(userId, month);
		UUID decisionId = insertDecision(userId, budgetCycleId, "GO", 30000);

		ConsumptionRecord result = consumptionService.changeDecisionResult(userId, decisionId, "STOP");

		assertThat(result.result()).isEqualTo("STOP");
		assertThat(result.isChanged()).isTrue();
		assertThat(result.changeCount()).isEqualTo(1);
	}

	@Test
	void incrementsChangeCountOnEachChange() {
		UUID userId = insertUser();
		String month = currentMonth();
		UUID budgetCycleId = insertBudgetCycle(userId, month);
		UUID decisionId = insertDecision(userId, budgetCycleId, "GO", 30000);

		consumptionService.changeDecisionResult(userId, decisionId, "STOP");
		ConsumptionRecord second = consumptionService.changeDecisionResult(userId, decisionId, "GO");

		assertThat(second.changeCount()).isEqualTo(2);
	}

	// ── 헬퍼 ─────────────────────────────────────────────────────────────

	private UUID insertUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId, now, now
		);
		return userId;
	}

	private UUID insertBudgetCycle(UUID userId, String yearMonth) {
		UUID cycleId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"insert into budget_cycles (id, user_id, year_month, monthly_budget_amount, spent_amount, warning_threshold_rate, created_at, updated_at) values (?, ?, ?, 500000, 0, 80.00, ?, ?)",
			cycleId, userId, yearMonth, now, now
		);
		return cycleId;
	}

	private UUID insertDecision(UUID userId, UUID budgetCycleId, String result, int price) {
		return insertDecisionAt(userId, budgetCycleId, result, price, OffsetDateTime.now(ZoneOffset.UTC), "테스트 상품");
	}

	private UUID insertDecisionAt(UUID userId, UUID budgetCycleId, String result, int price, OffsetDateTime decidedAt, String itemTitle) {
		String itemStatus = "GO".equals(result) ? "GO" : "STOP";
		UUID itemId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"insert into saved_items (id, user_id, input_source, title, listed_price, category, category_locked_by_user, status, created_at, updated_at) values (?, ?, 'DIRECT_INPUT', ?, ?, 'DIGITAL', false, ?, ?, ?)",
			itemId, userId, itemTitle, price, itemStatus, now, now
		);

		UUID decisionId = UUID.randomUUID();
		jdbcTemplate.update(
			"insert into purchase_decisions (id, user_id, item_id, budget_cycle_id, result, final_price, rationality_result, self_check_yes_count, is_changed, change_count, decided_at, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 'RATIONAL', 0, false, 0, ?, ?, ?)",
			decisionId, userId, itemId, budgetCycleId, result, price, decidedAt, now, now
		);
		return decisionId;
	}

	private String currentMonth() {
		return YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
	}
}
