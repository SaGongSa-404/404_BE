package com.sagongsa.backend.domain.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class BudgetCycleRolloverServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private BudgetCycleRolloverService budgetCycleRolloverService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void copiesLatestPreviousBudgetIntoMissingTargetMonth() {
		UUID userId = insertUser();
		YearMonth targetMonth = YearMonth.of(2026, 7);
		insertBudgetCycle(userId, targetMonth.minusMonths(2).toString(), 300_000, 200_000, "70.00");
		insertBudgetCycle(userId, targetMonth.minusMonths(1).toString(), 450_000, 120_000, "75.00");

		budgetCycleRolloverService.ensureBudgetCycle(userId, targetMonth.toString());

		assertThat(queryInteger(
			"select monthly_budget_amount from budget_cycles where user_id = ? and year_month = ?",
			userId,
			targetMonth.toString()
		)).isEqualTo(450_000);
		assertThat(queryInteger(
			"select spent_amount from budget_cycles where user_id = ? and year_month = ?",
			userId,
			targetMonth.toString()
		)).isZero();
		assertThat(queryBigDecimal(
			"select warning_threshold_rate from budget_cycles where user_id = ? and year_month = ?",
			userId,
			targetMonth.toString()
		)).isEqualByComparingTo("75.00");
	}

	@Test
	void doesNotCreateTargetMonthWithoutPreviousBudget() {
		UUID userId = insertUser();

		budgetCycleRolloverService.ensureBudgetCycle(userId, "2026-07");

		assertThat(queryInteger("select count(*) from budget_cycles where user_id = ?", userId)).isZero();
	}

	@Test
	void keepsExistingTargetMonthBudget() {
		UUID userId = insertUser();
		insertBudgetCycle(userId, "2026-06", 450_000, 120_000, "75.00");
		insertBudgetCycle(userId, "2026-07", 100_000, 10_000, "60.00");

		budgetCycleRolloverService.ensureBudgetCycle(userId, "2026-07");

		assertThat(queryInteger(
			"select monthly_budget_amount from budget_cycles where user_id = ? and year_month = '2026-07'",
			userId
		)).isEqualTo(100_000);
		assertThat(queryInteger(
			"select spent_amount from budget_cycles where user_id = ? and year_month = '2026-07'",
			userId
		)).isEqualTo(10_000);
		assertThat(queryInteger(
			"select count(*) from budget_cycles where user_id = ? and year_month = '2026-07'",
			userId
		)).isEqualTo(1);
	}

	private UUID insertUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId,
			now,
			now
		);
		return userId;
	}

	private void insertBudgetCycle(
		UUID userId,
		String yearMonth,
		int monthlyBudgetAmount,
		int spentAmount,
		String warningThresholdRate
	) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into budget_cycles (
				id, user_id, year_month, monthly_budget_amount, spent_amount,
				warning_threshold_rate, created_at, updated_at
			)
			values (?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			yearMonth,
			monthlyBudgetAmount,
			spentAmount,
			new BigDecimal(warningThresholdRate),
			now,
			now
		);
	}

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	private BigDecimal queryBigDecimal(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
	}
}
