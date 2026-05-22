package com.sagongsa.backend.mypage;

import com.sagongsa.backend.decision.DecisionResultResponse;
import com.sagongsa.backend.decision.DecisionResultUpdateRequest;
import com.sagongsa.backend.decision.DecisionService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ConsumptionService {

	private final JdbcTemplate jdbcTemplate;
	private final DecisionService decisionService;

	ConsumptionService(JdbcTemplate jdbcTemplate, DecisionService decisionService) {
		this.jdbcTemplate = jdbcTemplate;
		this.decisionService = decisionService;
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
		DecisionResultResponse response = decisionService.updateResult(
			userId,
			decisionId,
			new DecisionResultUpdateRequest(newResultStr, null, null, null)
		);

		int changeCount = jdbcTemplate.queryForObject(
			"SELECT change_count FROM purchase_decisions WHERE id = ?",
			Integer.class,
			decisionId
		);

		return new ConsumptionRecord(
			decisionId,
			response.itemTitle(),
			response.finalPrice(),
			response.result(),
			response.decidedAt(),
			true,
			changeCount
		);
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

	private Integer nullableInt(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp ts = rs.getTimestamp(column);
		return ts == null ? null : ts.toInstant();
	}
}
