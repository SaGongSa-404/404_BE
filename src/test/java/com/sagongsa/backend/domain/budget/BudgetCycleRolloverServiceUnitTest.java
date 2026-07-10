package com.sagongsa.backend.domain.budget;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BudgetCycleRolloverServiceUnitTest {

	private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

	private JdbcTemplate jdbcTemplate;
	private BudgetCycleRolloverService service;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(JdbcTemplate.class);
		service = new BudgetCycleRolloverService(jdbcTemplate);
	}

	@Test
	void skipsInsertSelectWhenTargetMonthAlreadyExists() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(USER_ID), eq("2026-07")))
			.thenReturn(true);

		service.ensureBudgetCycle(USER_ID, "2026-07");

		verify(jdbcTemplate, never()).update(
			contains("insert into budget_cycles"),
			any(),
			any(),
			any(),
			any(),
			any(),
			any()
		);
	}

	@Test
	void runsInsertSelectWhenTargetMonthIsMissing() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(USER_ID), eq("2026-07")))
			.thenReturn(false);

		service.ensureBudgetCycle(USER_ID, "2026-07");

		verify(jdbcTemplate).update(
			contains("insert into budget_cycles"),
			any(UUID.class),
			eq("2026-07"),
			any(OffsetDateTime.class),
			any(OffsetDateTime.class),
			eq(USER_ID),
			eq("2026-07")
		);
	}
}
