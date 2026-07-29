package com.sagongsa.backend.itemimport.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShoppingImportJobWorker {

	private static final Logger log = LoggerFactory.getLogger(ShoppingImportJobWorker.class);

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;
	private final ObjectMapper objectMapper;
	private final ShoppingLinkImportService shoppingLinkImportService;
	private final ShoppingImportProperties properties;
	private final ShoppingImportMetrics metrics;

	public ShoppingImportJobWorker(
		JdbcTemplate jdbcTemplate,
		TransactionTemplate transactionTemplate,
		ObjectMapper objectMapper,
		ShoppingLinkImportService shoppingLinkImportService,
		ShoppingImportProperties properties,
		ShoppingImportMetrics metrics
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
		this.objectMapper = objectMapper;
		this.shoppingLinkImportService = shoppingLinkImportService;
		this.properties = properties;
		this.metrics = metrics;
	}

	public boolean processNextJob() {
		ClaimedJob job = transactionTemplate.execute(status -> claimNextJob());
		if (job == null) {
			return false;
		}

		metrics.recordQueueWait(job.sourceSite(), Duration.between(job.createdAt(), job.startedAt()));
		if (job.crawlKey() != null) {
			metrics.recordActualCrawl(job.sourceSite());
		}

		long startedAtNanos = System.nanoTime();
		try {
			ShoppingLinkImportRequest request = objectMapper.readValue(job.requestJson(), ShoppingLinkImportRequest.class);
			ShoppingLinkImportResponse result = shoppingLinkImportService.importLink(request);
			markSucceeded(job.id(), objectMapper.writeValueAsString(result));
			log.info(
				"shopping import job succeeded jobId={} attempt={} durationMs={}",
				job.id(),
				job.attemptCount(),
				elapsedMillis(startedAtNanos)
			);
		} catch (Exception exception) {
			metrics.recordUpstreamRejection(job.sourceSite(), exception);
			markFailed(job.id(), failureFor(exception));
			log.warn(
				"shopping import job failed jobId={} attempt={} durationMs={}",
				job.id(),
				job.attemptCount(),
				elapsedMillis(startedAtNanos),
				exception
			);
		}
		return true;
	}

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

	private ClaimedJob claimNextJob() {
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

	private int recoverStaleJobsInTransaction() {
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

	private void markSucceeded(UUID jobId, String resultJson) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		transactionTemplate.executeWithoutResult(status -> {
			jdbcTemplate.update(
				"""
				update shopping_import_jobs
				set status = 'SUCCEEDED', result_json = cast(? as jsonb), completed_at = ?, updated_at = ?
				where id = ? and status = 'RUNNING'
				""",
				resultJson,
				now,
				now,
				jobId
			);
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
		});
	}

	private void markFailed(UUID jobId, JobFailure failure) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		transactionTemplate.executeWithoutResult(status -> {
			jdbcTemplate.update(
				"""
				update shopping_import_jobs
				set status = 'FAILED', result_json = null, error_code = ?, error_message = ?,
				    completed_at = ?, updated_at = ?
				where id = ? and status = 'RUNNING'
				""",
				failure.code(),
				failure.message(),
				now,
				now,
				jobId
			);
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
		});
	}

	private JobFailure failureFor(Exception exception) {
		if (exception instanceof ResponseStatusException responseStatusException) {
			HttpStatusCode status = responseStatusException.getStatusCode();
			if (status.is4xxClientError() && status.value() != 429) {
				return new JobFailure("INVALID_OR_UNSUPPORTED_ITEM", "상품 정보를 확인할 수 없습니다.");
			}
			if (status.is5xxServerError() || status.value() == 429) {
				return new JobFailure("SHOPPING_PAGE_UNAVAILABLE", "쇼핑 페이지에 일시적으로 접근할 수 없습니다.");
			}
		}
		if (exception instanceof JsonProcessingException) {
			return new JobFailure("INVALID_JOB_PAYLOAD", "쇼핑 링크 요청 형식이 올바르지 않습니다.");
		}
		return new JobFailure("IMPORT_FAILED", "쇼핑 링크 정보를 가져오지 못했습니다.");
	}

	private long elapsedMillis(long startedAtNanos) {
		return (System.nanoTime() - startedAtNanos) / 1_000_000;
	}

	private record ClaimedJob(
		UUID id,
		String requestJson,
		int attemptCount,
		OffsetDateTime createdAt,
		OffsetDateTime startedAt,
		String sourceSite,
		String crawlKey
	) {
	}

	private record RecoveredJob(
		UUID id,
		ShoppingImportJobStatus status,
		String errorCode,
		String errorMessage,
		OffsetDateTime completedAt
	) {
	}

	private record JobFailure(String code, String message) {
	}
}
