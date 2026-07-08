package com.sagongsa.backend.domain.budget;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetCycleRolloverService {

	private final JdbcTemplate jdbcTemplate;

	public BudgetCycleRolloverService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public void ensureCurrentMonthCycle(UUID userId, ZoneId zoneId) {
		ensureBudgetCycle(userId, YearMonth.now(zoneId).toString());
	}

	@Transactional
	public void ensureBudgetCycle(UUID userId, String yearMonth) {
		if (userId == null || yearMonth == null || yearMonth.isBlank()) {
			return;
		}
		String targetYearMonth = YearMonth.parse(yearMonth).toString();
		if (existsBudgetCycle(userId, targetYearMonth)) {
			return;
		}
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
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
			select
				?,
				bc.user_id,
				?,
				bc.monthly_budget_amount,
				0,
				bc.warning_threshold_rate,
				?,
				?
			from budget_cycles bc
			where bc.user_id = ?
			  and bc.year_month < ?
			order by bc.year_month desc
			limit 1
			on conflict (user_id, year_month) do nothing
			""",
			UUID.randomUUID(),
			targetYearMonth,
			now,
			now,
			userId,
			targetYearMonth
		);
	}

	private boolean existsBudgetCycle(UUID userId, String yearMonth) {
		Boolean exists = jdbcTemplate.queryForObject(
			"select exists (select 1 from budget_cycles where user_id = ? and year_month = ?)",
			Boolean.class,
			userId,
			yearMonth
		);
		return Boolean.TRUE.equals(exists);
	}
}
