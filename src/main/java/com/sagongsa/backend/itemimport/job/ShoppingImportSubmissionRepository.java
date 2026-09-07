package com.sagongsa.backend.itemimport.job;

import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ShoppingImportSubmissionRepository {
	private final JdbcTemplate jdbcTemplate;
	private final ShoppingImportProperties properties;

	ShoppingImportSubmissionRepository(JdbcTemplate jdbcTemplate, ShoppingImportProperties properties) {
		this.jdbcTemplate = jdbcTemplate;
		this.properties = properties;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public List<ShoppingImportJobAcceptedResponse> lockAndFindExisting(UUID userId, String requestHash) {
		jdbcTemplate.query("select pg_advisory_xact_lock(?)", resultSet -> null, 4_044_083L);
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
		return existing;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public ReusableJob findActiveLeader(String crawlKey) {
		return jdbcTemplate.query(
			"""
			select id, status, result_json::text, error_code, error_message, created_at, started_at
			from shopping_import_jobs
			where crawl_key = ?
			  and leader_job_id is null
			  and status in ('PENDING', 'RUNNING')
			order by created_at asc, id asc
			limit 1
			for update
			""",
			this::mapReusableJob,
			crawlKey
		).stream().findFirst().orElse(null);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public ReusableJob findCachedLeader(String crawlKey, OffsetDateTime now) {
		OffsetDateTime successCutoff = now.minus(properties.getSharedCrawl().getSuccessTtl());
		OffsetDateTime failureCutoff = now.minus(properties.getSharedCrawl().getFailureTtl());
		return jdbcTemplate.query(
			"""
			select id, status, result_json::text, error_code, error_message, created_at, started_at
			from shopping_import_jobs
			where crawl_key = ?
			  and leader_job_id is null
			  and (
			      (status = 'SUCCEEDED' and completed_at >= ?)
			      or (status = 'FAILED' and completed_at >= ?)
			  )
			order by completed_at desc, id desc
			limit 1
			for share
			""",
			this::mapReusableJob,
			crawlKey,
			successCutoff,
			failureCutoff
		).stream().findFirst().orElse(null);
	}

	private ReusableJob mapReusableJob(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
		return new ReusableJob(
			rs.getObject("id", UUID.class),
			ShoppingImportJobStatus.valueOf(rs.getString("status")),
			rs.getString("result_json"),
			rs.getString("error_code"),
			rs.getString("error_message"),
			rs.getObject("created_at", OffsetDateTime.class),
			rs.getObject("started_at", OffsetDateTime.class)
		);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void insertLeader(
		UUID jobId,
		UUID userId,
		String requestJson,
		String requestHash,
		ShoppingImportCrawlIdentity.Identity identity,
		OffsetDateTime now
	) {
		jdbcTemplate.update(
			"""
			insert into shopping_import_jobs (
				id, user_id, status, request_json, request_hash, crawl_key, source_site,
				attempt_count, created_at, updated_at
			) values (?, ?, 'PENDING', cast(? as jsonb), ?, ?, ?, 0, ?, ?)
			""",
			jobId,
			userId,
			requestJson,
			requestHash,
			identity == null ? null : identity.key(),
			identity == null ? "manual" : identity.site(),
			now,
			now
		);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void insertActiveFollower(
		UUID jobId,
		UUID userId,
		String requestJson,
		String requestHash,
		ShoppingImportCrawlIdentity.Identity identity,
		ReusableJob leader,
		OffsetDateTime now
	) {
		jdbcTemplate.update(
			"""
			insert into shopping_import_jobs (
				id, user_id, status, request_json, request_hash, leader_job_id, crawl_key, source_site,
				attempt_count, created_at, started_at, updated_at
			) values (?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, 0, ?, ?, ?)
			""",
			jobId,
			userId,
			leader.status().name(),
			requestJson,
			requestHash,
			leader.id(),
			identity.key(),
			identity.site(),
			now,
			leader.startedAt(),
			now
		);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void insertCachedFollower(
		UUID jobId,
		UUID userId,
		String requestJson,
		String requestHash,
		ShoppingImportCrawlIdentity.Identity identity,
		ReusableJob cached,
		OffsetDateTime now
	) {
		jdbcTemplate.update(
			"""
			insert into shopping_import_jobs (
				id, user_id, status, request_json, request_hash, leader_job_id, crawl_key, source_site,
				result_json, error_code, error_message, attempt_count, created_at, completed_at, updated_at
			) values (?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, cast(? as jsonb), ?, ?, 0, ?, ?, ?)
			""",
			jobId,
			userId,
			cached.status().name(),
			requestJson,
			requestHash,
			cached.id(),
			identity.key(),
			identity.site(),
			cached.resultJson(),
			cached.errorCode(),
			cached.errorMessage(),
			now,
			now,
			now
		);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public int activeLeaders() {
		return jdbcTemplate.queryForObject("select count(*) from shopping_import_jobs where leader_job_id is null and status in ('PENDING', 'RUNNING')", Integer.class);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public int activeJobs(UUID userId) {
		return jdbcTemplate.queryForObject("select count(*) from shopping_import_jobs where user_id = ? and status in ('PENDING', 'RUNNING')", Integer.class, userId);
	}

	record ReusableJob(
		UUID id,
		ShoppingImportJobStatus status,
		String resultJson,
		String errorCode,
		String errorMessage,
		OffsetDateTime createdAt,
		OffsetDateTime startedAt
	) {
	}
}
