package com.sagongsa.backend.mypage;

import com.sagongsa.backend.domain.enums.PurchaseDecisionResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ConsumptionService {

	private final JdbcTemplate jdbcTemplate;

	ConsumptionService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	ConsumptionListResponse getMonthlyConsumption(UUID userId, String month) {
		validateMonth(month);
		List<ConsumptionRecord> records = jdbcTemplate.query(
			"""
			SELECT pd.id, si.title AS item_title, pd.final_price AS price,
			       pd.result, pd.decided_at, pd.is_changed, pd.change_count
			FROM purchase_decisions pd
			JOIN saved_items si ON si.id = pd.item_id
			JOIN budget_cycles bc ON bc.id = pd.budget_cycle_id
			WHERE pd.user_id = ?
			  AND bc.year_month = ?
			ORDER BY pd.decided_at DESC
			""",
			this::mapConsumptionRecord,
			userId,
			month
		);
		return new ConsumptionListResponse(month, records);
	}

	@Transactional
	ConsumptionRecord changeDecisionResult(UUID userId, UUID decisionId, String newResultStr) {
		PurchaseDecisionResult newResult = parseResult(newResultStr);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		DecisionForChange decision = lockDecision(userId, decisionId);
		PurchaseDecisionResult previousResult = PurchaseDecisionResult.valueOf(decision.result());

		int previousGoAmount = previousResult == PurchaseDecisionResult.GO
			? Objects.requireNonNullElse(decision.finalPrice(), 0) : 0;
		int newGoAmount = newResult == PurchaseDecisionResult.GO
			? Objects.requireNonNullElse(decision.finalPrice(), 0) : 0;
		int delta = newGoAmount - previousGoAmount;

		jdbcTemplate.update(
			"""
			UPDATE purchase_decisions
			SET result = ?, is_changed = true, change_count = change_count + 1,
			    changed_at = ?, updated_at = ?
			WHERE id = ?
			""",
			newResult.name(), now, now, decisionId
		);

		jdbcTemplate.update(
			"UPDATE saved_items SET status = ?, updated_at = ? WHERE id = ?",
			newResult.name(), now, decision.itemId()
		);

		if (decision.budgetCycleId() != null && delta != 0) {
			jdbcTemplate.update(
				"""
				UPDATE budget_cycles
				SET spent_amount = GREATEST(spent_amount + ?, 0), updated_at = ?
				WHERE id = ?
				""",
				delta, now, decision.budgetCycleId()
			);
		}

		return new ConsumptionRecord(
			decisionId,
			decision.itemTitle(),
			decision.finalPrice(),
			newResult.name(),
			decision.decidedAt(),
			true,
			decision.changeCount() + 1
		);
	}

	private DecisionForChange lockDecision(UUID userId, UUID decisionId) {
		try {
			return jdbcTemplate.queryForObject(
				"""
				SELECT pd.id, pd.item_id, pd.budget_cycle_id, pd.result,
				       pd.final_price, pd.change_count, pd.decided_at,
				       si.title AS item_title
				FROM purchase_decisions pd
				JOIN saved_items si ON si.id = pd.item_id
				WHERE pd.user_id = ?
				  AND pd.id = ?
				FOR UPDATE OF pd
				""",
				this::mapDecisionForChange,
				userId,
				decisionId
			);
		} catch (EmptyResultDataAccessException e) {
			throw new ConsumptionNotFoundException("소비 기록을 찾을 수 없습니다.");
		}
	}

	private PurchaseDecisionResult parseResult(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ConsumptionBadRequestException("result는 필수입니다.");
		}
		try {
			return PurchaseDecisionResult.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new ConsumptionBadRequestException("result는 GO 또는 STOP이어야 합니다.");
		}
	}

	private void validateMonth(String month) {
		if (month == null || month.isBlank()) {
			throw new ConsumptionBadRequestException("month는 필수입니다.");
		}
		try {
			YearMonth.parse(month);
		} catch (Exception e) {
			throw new ConsumptionBadRequestException("month는 YYYY-MM 형식이어야 합니다.");
		}
	}

	private ConsumptionRecord mapConsumptionRecord(ResultSet rs, int rowNum) throws SQLException {
		return new ConsumptionRecord(
			rs.getObject("id", UUID.class),
			rs.getString("item_title"),
			nullableInt(rs, "price"),
			rs.getString("result"),
			toInstant(rs, "decided_at"),
			rs.getBoolean("is_changed"),
			rs.getInt("change_count")
		);
	}

	private DecisionForChange mapDecisionForChange(ResultSet rs, int rowNum) throws SQLException {
		return new DecisionForChange(
			rs.getObject("id", UUID.class),
			rs.getObject("item_id", UUID.class),
			rs.getObject("budget_cycle_id", UUID.class),
			rs.getString("result"),
			nullableInt(rs, "final_price"),
			rs.getInt("change_count"),
			toInstant(rs, "decided_at"),
			rs.getString("item_title")
		);
	}

	private Integer nullableInt(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp ts = rs.getTimestamp(column);
		return ts == null ? null : ts.toInstant();
	}

	private record DecisionForChange(
		UUID id,
		UUID itemId,
		UUID budgetCycleId,
		String result,
		Integer finalPrice,
		int changeCount,
		Instant decidedAt,
		String itemTitle
	) {}
}
