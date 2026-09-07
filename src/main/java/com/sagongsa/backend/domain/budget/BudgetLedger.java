package com.sagongsa.backend.domain.budget;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 결정 명령이 연 트랜잭션 안에서 예산의 잠금과 증감을 담당한다. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class BudgetLedger {
	private final JdbcTemplate jdbcTemplate;
	public BudgetLedger(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

	public BudgetCycle lockBudgetCycle(UUID userId, String yearMonth) {
		try {
			return jdbcTemplate.queryForObject(
				"""
				select id, year_month, spent_amount, monthly_budget_amount
				from budget_cycles
				where user_id = ?
				  and year_month = ?
				for update
				""",
				(rs, row) -> new BudgetCycle(rs.getObject("id", UUID.class), rs.getString("year_month"), rs.getInt("spent_amount"), rs.getInt("monthly_budget_amount")),
				userId,
				yearMonth
			);
		}
		catch (EmptyResultDataAccessException exception) {
			throw new BudgetCycleMissingException();
		}
	}

	public void incrementBudgetSpent(UUID budgetCycleId, int amount, OffsetDateTime now) {
		jdbcTemplate.update(
			"""
			update budget_cycles
			set spent_amount = spent_amount + ?,
				updated_at = ?
			where id = ?
			""",
			amount,
			now,
			budgetCycleId
		);
	}

	public BudgetUpdate updateBudgetForDecisionChange(UUID budgetCycleId, int delta, OffsetDateTime now) {
		if (budgetCycleId == null) {
			return new BudgetUpdate(0, new BudgetExhaustion(false, false));
		}
		BudgetSpent budget = jdbcTemplate.queryForObject(
			"""
			update budget_cycles
			set spent_amount = greatest(spent_amount + ?, 0),
				updated_at = ?
			where id = ?
			returning monthly_budget_amount, spent_amount
			""",
			(rs, rowNumber) -> new BudgetSpent(
				rs.getInt("monthly_budget_amount"),
				rs.getInt("spent_amount")
			),
			delta,
			now,
			budgetCycleId
		);
		if (budget == null) {
			return new BudgetUpdate(0, new BudgetExhaustion(false, false));
		}
		int budgetBeforeAmount = budget.spentAmount() - delta;
		return new BudgetUpdate(
			budget.spentAmount(),
			budgetExhaustion(budget.monthlyBudgetAmount(), budgetBeforeAmount, budget.spentAmount())
		);
	}

	public static BudgetExhaustion budgetExhaustion(int monthlyBudgetAmount, int budgetBeforeAmount, int budgetAfterAmount) {
		boolean exhaustedBefore = budgetExhausted(monthlyBudgetAmount, budgetBeforeAmount);
		boolean exhaustedAfter = budgetExhausted(monthlyBudgetAmount, budgetAfterAmount);
		return new BudgetExhaustion(exhaustedAfter, !exhaustedBefore && exhaustedAfter);
	}

	private static boolean budgetExhausted(int monthlyBudgetAmount, int spentAmount) {
		return monthlyBudgetAmount > 0 && spentAmount >= monthlyBudgetAmount;
	}

	public record BudgetCycle(UUID id, String yearMonth, int spentAmount, int monthlyBudgetAmount) {
	}

	public record BudgetSpent(int monthlyBudgetAmount, int spentAmount) {
	}

	public record BudgetUpdate(int spentAmount, BudgetExhaustion exhaustion) {
	}

	public record BudgetExhaustion(boolean exhaustedAfter, boolean becameExhausted) {
	}
}
