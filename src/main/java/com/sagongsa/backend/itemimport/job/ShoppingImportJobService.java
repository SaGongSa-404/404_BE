package com.sagongsa.backend.itemimport.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.itemimport.item.SavedItemDraft;
import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import com.sagongsa.backend.itemimport.item.WishlistSaveDraft;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
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
	private final ShoppingImportMetrics metrics;

	public ShoppingImportJobService(
		JdbcTemplate jdbcTemplate,
		TransactionTemplate transactionTemplate,
		ObjectMapper objectMapper,
		ShoppingImportProperties properties,
		ShoppingImportMetrics metrics
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.metrics = metrics;
	}

	public ShoppingImportJobAcceptedResponse submit(UUID userId, ShoppingLinkImportRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		String requestJson = writeJson(request);
		String requestHash = sha256(requestJson);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UUID jobId = UUID.randomUUID();
		Optional<ShoppingImportCrawlIdentity.Identity> identity = ShoppingImportCrawlIdentity.from(request);

		Submission submission = transactionTemplate.execute(status -> {
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
				return new Submission(existing.getFirst(), ReuseOutcome.EXISTING, identity.map(it -> it.site()).orElse("manual"));
			}

			if (identity.isPresent() && properties.getSharedCrawl().isCoalescingEnabled()) {
				ReusableJob active = findActiveLeader(identity.get().key());
				if (active != null) {
					ensureUserCapacity(userId);
					insertActiveFollower(jobId, userId, requestJson, requestHash, identity.get(), active, now);
					return new Submission(
						new ShoppingImportJobAcceptedResponse(jobId, active.status(), now),
						ReuseOutcome.COALESCED,
						identity.get().site()
					);
				}
			}

			if (identity.isPresent() && properties.getSharedCrawl().isCacheEnabled()) {
				ReusableJob cached = findCachedLeader(identity.get().key(), now);
				if (cached != null) {
					insertCachedFollower(jobId, userId, requestJson, requestHash, identity.get(), cached, now);
					ReuseOutcome outcome = cached.status() == ShoppingImportJobStatus.SUCCEEDED
						? ReuseOutcome.SUCCESS_CACHE_HIT
						: ReuseOutcome.FAILURE_CACHE_HIT;
					return new Submission(
						new ShoppingImportJobAcceptedResponse(jobId, cached.status(), now),
						outcome,
						identity.get().site()
					);
				}
			}

			ensureQueueCapacity();
			ensureUserCapacity(userId);
			insertLeader(jobId, userId, requestJson, requestHash, identity.orElse(null), now);
			ReuseOutcome outcome = identity.isPresent() && properties.getSharedCrawl().isCacheEnabled()
				? ReuseOutcome.CACHE_MISS
				: ReuseOutcome.NEW;
			return new Submission(
				new ShoppingImportJobAcceptedResponse(jobId, ShoppingImportJobStatus.PENDING, now),
				outcome,
				identity.map(it -> it.site()).orElse("manual")
			);
		});

		if (submission == null) {
			throw new IllegalStateException("Shopping import job transaction returned no result");
		}
		recordReuseMetric(submission);
		return submission.accepted();
	}

	public ShoppingImportJobResponse get(UUID userId, UUID jobId) {
		return jdbcTemplate.query(
			"""
			select id, status, request_json::text, result_json::text, error_code, error_message, attempt_count,
			       created_at, started_at, completed_at
			from shopping_import_jobs
			where id = ? and user_id = ?
			""",
			(rs, rowNumber) -> new ShoppingImportJobResponse(
				rs.getObject("id", UUID.class),
				ShoppingImportJobStatus.valueOf(rs.getString("status")),
				readResult(rs.getString("result_json"), rs.getString("request_json")),
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

	private ReusableJob findActiveLeader(String crawlKey) {
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

	private ReusableJob findCachedLeader(String crawlKey, OffsetDateTime now) {
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

	private void insertLeader(
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

	private void insertActiveFollower(
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

	private void insertCachedFollower(
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

	private void ensureQueueCapacity() {
		Integer activeJobs = jdbcTemplate.queryForObject(
			"""
			select count(*) from shopping_import_jobs
			where leader_job_id is null and status in ('PENDING', 'RUNNING')
			""",
			Integer.class
		);
		if (activeJobs != null && activeJobs >= properties.getJobWorker().getMaxQueueSize()) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Shopping import queue is full");
		}
	}

	private void ensureUserCapacity(UUID userId) {
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
	}

	private void recordReuseMetric(Submission submission) {
		switch (submission.outcome()) {
			case COALESCED -> metrics.recordCoalesced(submission.site());
			case SUCCESS_CACHE_HIT -> metrics.recordCacheLookup(submission.site(), "success_hit");
			case FAILURE_CACHE_HIT -> metrics.recordCacheLookup(submission.site(), "failure_hit");
			case CACHE_MISS -> metrics.recordCacheLookup(submission.site(), "miss");
			case EXISTING, NEW -> {
			}
		}
	}

	private ShoppingLinkImportResponse readResult(String resultJson, String requestJson) {
		if (resultJson == null) {
			return null;
		}
		try {
			ShoppingLinkImportResponse result = objectMapper.readValue(resultJson, ShoppingLinkImportResponse.class);
			ShoppingLinkImportRequest request = objectMapper.readValue(requestJson, ShoppingLinkImportRequest.class);
			return personalize(result, request);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to deserialize shopping import result", exception);
		}
	}

	private ShoppingLinkImportResponse personalize(
		ShoppingLinkImportResponse result,
		ShoppingLinkImportRequest request
	) {
		if (request.inputSource() != ItemInputSource.SHARE || request.url() == null) {
			return result;
		}
		SavedItemDraft item = result.item();
		SavedItemDraft personalizedItem = item == null ? null : new SavedItemDraft(
			item.inputSource(),
			request.url(),
			item.normalizedUrl(),
			item.title(),
			request.brandName() == null || request.brandName().isBlank() ? item.brandName() : request.brandName(),
			item.summary(),
			item.imageUrl(),
			item.listedPrice(),
			item.currencyCode(),
			item.category(),
			item.categoryConfidence(),
			item.categoryLockedByUser(),
			item.status()
		);
		WishlistSaveDraft saveRequest = result.saveRequest();
		WishlistSaveDraft personalizedSaveRequest = saveRequest == null ? null : new WishlistSaveDraft(
			saveRequest.inputSource(),
			request.url(),
			saveRequest.normalizedUrl(),
			saveRequest.title(),
			saveRequest.imageUrl(),
			saveRequest.listedPrice(),
			saveRequest.currencyCode(),
			saveRequest.category(),
			saveRequest.categoryConfidence(),
			saveRequest.categoryLockedByUser(),
			saveRequest.sourceDomain(),
			saveRequest.rawTitle(),
			saveRequest.rawDescription(),
			saveRequest.rawPriceText(),
			saveRequest.rawPayloadJson()
		);
		return new ShoppingLinkImportResponse(
			result.retrievalStatus(),
			personalizedItem,
			result.sourceMetadata(),
			personalizedSaveRequest,
			result.warnings()
		);
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

	private enum ReuseOutcome {
		EXISTING,
		COALESCED,
		SUCCESS_CACHE_HIT,
		FAILURE_CACHE_HIT,
		CACHE_MISS,
		NEW
	}

	private record Submission(
		ShoppingImportJobAcceptedResponse accepted,
		ReuseOutcome outcome,
		String site
	) {
	}

	private record ReusableJob(
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
