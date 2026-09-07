package com.sagongsa.backend.itemimport.job;

import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@org.springframework.stereotype.Repository
class ShoppingImportJobRepository {
	private static final Logger log = LoggerFactory.getLogger(ShoppingImportJobRepository.class);
	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;
	private final ShoppingImportProperties properties;

	ShoppingImportJobRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, ShoppingImportProperties properties) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
		this.properties = properties;
	}

	ClaimedJob claim() { return transactionTemplate.execute(status -> claimNextJob()); }

	public boolean hasClaimableJob() {
		Boolean claimable = jdbcTemplate.queryForObject(
			"""
			select exists (
				select 1
				from shopping_import_jobs
				where leader_job_id is null
				  and status = 'PENDING'
				  and attempt_count < ?
			)
			""",
			Boolean.class,
			properties.getJobWorker().getMaxAttempts()
		);
		return Boolean.TRUE.equals(claimable);
	}

	public int cleanupExpiredJobs() {
		OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(properties.getJobWorker().getRetention());
		int deleted = jdbcTemplate.update(
			"""
			delete from shopping_import_jobs
			where id in (
				select id
				from shopping_import_jobs
				where status in ('SUCCEEDED', 'FAILED')
				  and completed_at < ?
				order by completed_at asc
				limit 1000
			)
			""",
			cutoff
		);
		if (deleted > 0) {
			log.info("cleaned up expired shopping import jobs count={}", deleted);
		}
		return deleted;
	}

	ClaimedJob claimNextJob() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		ClaimedJob job = jdbcTemplate.query(
			"""
			with candidate as (
				select id
				from shopping_import_jobs
				where leader_job_id is null
				  and status = 'PENDING'
				  and attempt_count < ?
				order by created_at asc, id asc
				limit 1
				for update skip locked
			)
			update shopping_import_jobs job
			set status = 'RUNNING',
				started_at = ?,
				updated_at = ?,
				attempt_count = attempt_count + 1
			from candidate
			where job.id = candidate.id
			returning job.id, job.request_json::text, job.attempt_count, job.created_at,
			          job.started_at, job.source_site, job.crawl_key
			""",
			(rs, rowNumber) -> new ClaimedJob(
				rs.getObject("id", UUID.class),
				rs.getString("request_json"),
				rs.getInt("attempt_count"),
				rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("started_at", OffsetDateTime.class),
				rs.getString("source_site"),
				rs.getString("crawl_key")
			),
			properties.getJobWorker().getMaxAttempts(),
			now,
			now
		).stream().findFirst().orElse(null);

		if (job != null) {
			jdbcTemplate.update(
				"""
				update shopping_import_jobs
				set status = 'RUNNING', started_at = ?, updated_at = ?
				where leader_job_id = ? and status = 'PENDING'
				""",
				now,
				now,
				job.id()
			);
		}
		return job;
	}

	public int recoverStaleJobs() {
		Integer recovered = transactionTemplate.execute(status -> recoverStaleJobsInTransaction());
		if (recovered != null && recovered > 0) {
			log.warn("recovered stale shopping import jobs count={}", recovered);
		}
		return recovered == null ? 0 : recovered;
	}

	int recoverStaleJobsInTransaction() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime cutoff = now.minus(properties.getJobWorker().getStaleTimeout());
		List<RecoveredJob> recoveredJobs = jdbcTemplate.query(
			"""
			update shopping_import_jobs
			set status = case when attempt_count < ? then 'PENDING' else 'FAILED' end,
				started_at = null,
				completed_at = case when attempt_count < ? then null else ? end,
				error_code = case when attempt_count < ? then null else 'WORKER_INTERRUPTED' end,
				error_message = case when attempt_count < ? then null else '쇼핑 링크 처리 작업이 중단되었습니다.' end,
				updated_at = ?
			where leader_job_id is null
			  and status = 'RUNNING'
			  and started_at < ?
			returning id, status, error_code, error_message, completed_at
			""",
			(rs, rowNumber) -> new RecoveredJob(
				rs.getObject("id", UUID.class),
				ShoppingImportJobStatus.valueOf(rs.getString("status")),
				rs.getString("error_code"),
				rs.getString("error_message"),
				rs.getObject("completed_at", OffsetDateTime.class)
			),
			properties.getJobWorker().getMaxAttempts(),
			properties.getJobWorker().getMaxAttempts(),
			now,
			properties.getJobWorker().getMaxAttempts(),
			properties.getJobWorker().getMaxAttempts(),
			now,
			cutoff
		);

		for (RecoveredJob recovered : recoveredJobs) {
			if (recovered.status() == ShoppingImportJobStatus.PENDING) {
				jdbcTemplate.update(
					"""
					update shopping_import_jobs
					set status = 'PENDING', started_at = null, updated_at = ?
					where leader_job_id = ? and status = 'RUNNING'
					""",
					now,
					recovered.id()
				);
			} else {
				jdbcTemplate.update(
					"""
					update shopping_import_jobs
					set status = 'FAILED', result_json = null, error_code = ?, error_message = ?,
					    completed_at = ?, updated_at = ?
					where leader_job_id = ? and status in ('PENDING', 'RUNNING')
					""",
					recovered.errorCode(),
					recovered.errorMessage(),
					recovered.completedAt(),
					now,
					recovered.id()
				);
			}
		}
		return recoveredJobs.size();
	}

	boolean markSucceeded(ClaimedJob job, String resultJson) {
		UUID jobId = job.id();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
			int updated = jdbcTemplate.update(
				"""
				update shopping_import_jobs
				set status = 'SUCCEEDED', result_json = cast(? as jsonb), completed_at = ?, updated_at = ?
				where id = ? and status = 'RUNNING' and attempt_count = ?
				""",
				resultJson,
				now,
				now,
				jobId,
				job.attemptCount()
			);
			if (updated == 0) {
				log.info("ignored stale shopping import completion jobId={} attempt={}", jobId, job.attemptCount());
				return false;
			}
			jdbcTemplate.update(
				"""
				update shopping_import_jobs
				set status = 'SUCCEEDED', result_json = cast(? as jsonb), error_code = null, error_message = null,
				    completed_at = ?, updated_at = ?
				where leader_job_id = ? and status in ('PENDING', 'RUNNING')
				""",
				resultJson,
				now,
				now,
				jobId
			);
			return true;
		}));
	}

	boolean markFailed(ClaimedJob job, JobFailure failure) {
		UUID jobId = job.id();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
			int updated = jdbcTemplate.update(
				"""
				update shopping_import_jobs
				set status = 'FAILED', result_json = null, error_code = ?, error_message = ?,
				    completed_at = ?, updated_at = ?
				where id = ? and status = 'RUNNING' and attempt_count = ?
				""",
				failure.code(),
				failure.message(),
				now,
				now,
				jobId,
				job.attemptCount()
			);
			if (updated == 0) {
				log.info("ignored stale shopping import completion jobId={} attempt={}", jobId, job.attemptCount());
				return false;
			}
			jdbcTemplate.update(
				"""
				update shopping_import_jobs
				set status = 'FAILED', result_json = null, error_code = ?, error_message = ?,
				    completed_at = ?, updated_at = ?
				where leader_job_id = ? and status in ('PENDING', 'RUNNING')
				""",
				failure.code(),
				failure.message(),
				now,
				now,
				jobId
			);
			return true;
		}));
	}

	record ClaimedJob(
		UUID id,
		String requestJson,
		int attemptCount,
		OffsetDateTime createdAt,
		OffsetDateTime startedAt,
		String sourceSite,
		String crawlKey
	) {
	}

	record RecoveredJob(
		UUID id,
		ShoppingImportJobStatus status,
		String errorCode,
		String errorMessage,
		OffsetDateTime completedAt
	) {
	}

	record JobFailure(String code, String message) {
	}
}
