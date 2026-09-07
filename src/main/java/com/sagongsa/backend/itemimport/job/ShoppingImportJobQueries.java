package com.sagongsa.backend.itemimport.job;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ShoppingImportJobQueries {
	private final JdbcTemplate jdbcTemplate;
	ShoppingImportJobQueries(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

	public JobSnapshot findOwned(UUID userId, UUID jobId) {
		return jdbcTemplate.query(
			"""
			select id, status, request_json::text, result_json::text, error_code, error_message, attempt_count,
			       created_at, started_at, completed_at
			from shopping_import_jobs
			where id = ? and user_id = ?
			""",
			(rs, rowNumber) -> new JobSnapshot(
				rs.getObject("id", UUID.class),
				ShoppingImportJobStatus.valueOf(rs.getString("status")),
				rs.getString("result_json"), rs.getString("request_json"),
				rs.getString("error_code"), rs.getString("error_message"),
				rs.getInt("attempt_count"),
				rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("started_at", OffsetDateTime.class),
				rs.getObject("completed_at", OffsetDateTime.class)
			),
			jobId,
			userId
		).stream().findFirst().orElse(null);
	}


	record JobSnapshot(UUID id, ShoppingImportJobStatus status, String resultJson, String requestJson,
		String errorCode, String errorMessage, int attemptCount, OffsetDateTime createdAt,
		OffsetDateTime startedAt, OffsetDateTime completedAt) {}

}
