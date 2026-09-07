package com.sagongsa.backend.home;

import java.time.YearMonth;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class HomeBubbleCommands {
	private final JdbcTemplate jdbcTemplate;
	HomeBubbleCommands(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

	public void markBubbleSeen(UUID userId) {
		markBudgetExhaustionBubbleSeen(userId);
	}

	public void markBubbleSeen(UUID userId, String type) {
		if (type == null || type.isBlank()) {
			return;
		}
		switch (type.toUpperCase(java.util.Locale.ROOT)) {
			case "WELCOME" -> markWelcomeBubbleSeen(userId);
			case "BUDGET_NEGATIVE", "BUDGET_ZERO" -> markBudgetExhaustionBubbleSeen(userId);
			case "DECISION_REACTION" -> clearDecisionReactionBubble(userId);
			default -> {
			}
		}
	}

	private void markBudgetExhaustionBubbleSeen(UUID userId) {
		String timezone = jdbcTemplate.query(
				"select timezone from user_profiles where user_id = ?",
				(rs, rowNum) -> rs.getString("timezone"),
				userId
			)
			.stream().findFirst().orElse(null);
		String currentYearMonth = YearMonth.now(resolveZoneId(timezone)).toString();
		jdbcTemplate.update(
			"""
			update budget_cycles
			   set budget_exhaustion_bubble_seen = true,
			       updated_at = now()
			 where user_id = ?
			   and year_month = ?
			   and monthly_budget_amount > 0
			   and spent_amount >= monthly_budget_amount
			""",
			userId,
			currentYearMonth
		);
	}

	private void markWelcomeBubbleSeen(UUID userId) {
		jdbcTemplate.update(
			"""
			update mascot_profiles
			   set welcome_bubble_seen = true,
			       updated_at = now()
			 where user_id = ?
			""",
			userId
		);
	}

	private void clearDecisionReactionBubble(UUID userId) {
		jdbcTemplate.update(
			"""
			update mascot_profiles
			   set last_reaction_message = null,
			       reaction_expires_at = null,
			       updated_at = now()
			 where user_id = ?
			""",
			userId
		);
	}

	private static java.time.ZoneId resolveZoneId(String timezone) {
		try {
			return java.time.ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Seoul" : timezone);
		} catch (java.time.DateTimeException ignored) {
			return java.time.ZoneId.of("Asia/Seoul");
		}
	}
}
