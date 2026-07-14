package com.sagongsa.backend.itemimport.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShoppingImportJobService {

	private static final long QUEUE_CAPACITY_LOCK_ID = 4_044_083L;

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;
	private final ObjectMapper objectMapper;
	private final ShoppingImportProperties properties;

	public ShoppingImportJobService(
		JdbcTemplate jdbcTemplate,
		TransactionTemplate transactionTemplate,
		ObjectMapper objectMapper,
		ShoppingImportProperties properties
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	public ShoppingImportJobAcceptedResponse submit(UUID userId, ShoppingLinkImportRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		String requestJson = writeJson(request);
		String requestHash = sha256(requestJson);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UUID jobId = UUID.randomUUID();

		ShoppingImportJobAcceptedResponse accepted = transactionTemplate.execute(status -> {
			jdbcTemplate.query("select pg_advisory_xact_lock(?)", resultSet -> null, QUEUE_CAPACITY_LOCK_ID);
			List<ShoppingImportJobAcceptedResponse> existing = jdbcTemplate.query(
				"""
				select id, status, created_at
				from shopping_import_jobs
				where user_id = ?
				  and request_hash = ?
				  and status in ('PENDING', 'RUNNING')
				order by created_at asc
				limit 1
				""",
				(rs, rowNumber) -> new ShoppingImportJobAcceptedResponse(
					rs.getObject("id", UUID.class),
					ShoppingImportJobStatus.valueOf(rs.getString("status")),
					rs.getObject("created_at", OffsetDateTime.class)
				),
				userId,
				requestHash
			);
			if (!existing.isEmpty()) {
				return existing.getFirst();
			}

			Integer activeJobs = jdbcTemplate.queryForObject(
				"select count(*) from shopping_import_jobs where status in ('PENDING', 'RUNNING')",
				Integer.class
			);
			if (activeJobs != null && activeJobs >= properties.getJobWorker().getMaxQueueSize()) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Shopping import queue is full");
			}
			Integer userActiveJobs = jdbcTemplate.queryForObject(
				"""
				select count(*) from shopping_import_jobs
				where user_id = ? and status in ('PENDING', 'RUNNING')
				""",
				Integer.class,
				userId
			);
			if (userActiveJobs != null && userActiveJobs >= properties.getJobWorker().getMaxActivePerUser()) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many active shopping import jobs");
			}
			jdbcTemplate.update(
				"""
				insert into shopping_import_jobs (
					id, user_id, status, request_json, request_hash, attempt_count, created_at, updated_at
				) values (?, ?, 'PENDING', cast(? as jsonb), ?, 0, ?, ?)
				""",
				jobId,
				userId,
				requestJson,
				requestHash,
				now,
				now
			);
			return new ShoppingImportJobAcceptedResponse(jobId, ShoppingImportJobStatus.PENDING, now);
		});

		if (accepted == null) {
			throw new IllegalStateException("Shopping import job transaction returned no result");
		}
		return accepted;
	}

	public ShoppingImportJobResponse get(UUID userId, UUID jobId) {
		return jdbcTemplate.query(
			"""
			select id, status, result_json, error_code, error_message, attempt_count,
			       created_at, started_at, completed_at
			from shopping_import_jobs
			where id = ? and user_id = ?
			""",
			(rs, rowNumber) -> new ShoppingImportJobResponse(
				rs.getObject("id", UUID.class),
				ShoppingImportJobStatus.valueOf(rs.getString("status")),
				readResult(rs.getString("result_json")),
				readError(rs.getString("error_code"), rs.getString("error_message")),
				rs.getInt("attempt_count"),
				rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("started_at", OffsetDateTime.class),
				rs.getObject("completed_at", OffsetDateTime.class)
			),
			jobId,
			userId
		).stream().findFirst().orElseThrow(() ->
			new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping import job not found")
		);
	}

	private ShoppingLinkImportResponse readResult(String resultJson) {
		if (resultJson == null) {
			return null;
		}
		try {
			return objectMapper.readValue(resultJson, ShoppingLinkImportResponse.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to deserialize shopping import result", exception);
		}
	}

	private ShoppingImportJobError readError(String code, String message) {
		return code == null ? null : new ShoppingImportJobError(code, message);
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shopping import request", exception);
		}
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
