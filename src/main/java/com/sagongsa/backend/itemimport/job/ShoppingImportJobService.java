package com.sagongsa.backend.itemimport.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.itemimport.item.SavedItemDraft;
import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import com.sagongsa.backend.itemimport.item.WishlistSaveDraft;
import com.sagongsa.backend.itemimport.job.ShoppingImportSubmissionRepository.ReusableJob;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShoppingImportJobService {

	private final ShoppingImportSubmissionRepository repository;
	private final ShoppingImportJobQueries queries;
	private final TransactionTemplate transactionTemplate;
	private final ObjectMapper objectMapper;
	private final ShoppingImportProperties properties;
	private final ShoppingImportMetrics metrics;

	public ShoppingImportJobService(
		ShoppingImportSubmissionRepository repository,
		ShoppingImportJobQueries queries,
		TransactionTemplate transactionTemplate,
		ObjectMapper objectMapper,
		ShoppingImportProperties properties,
		ShoppingImportMetrics metrics
	) {
		this.repository = repository;
		this.queries = queries;
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
			List<ShoppingImportJobAcceptedResponse> existing = repository.lockAndFindExisting(userId, requestHash);
			if (!existing.isEmpty()) {
				return new Submission(existing.getFirst(), ReuseOutcome.EXISTING, identity.map(it -> it.site()).orElse("manual"));
			}

			if (identity.isPresent() && properties.getSharedCrawl().isCoalescingEnabled()) {
				ReusableJob active = repository.findActiveLeader(identity.get().key());
				if (active != null) {
					ensureUserCapacity(userId);
					repository.insertActiveFollower(jobId, userId, requestJson, requestHash, identity.get(), active, now);
					return new Submission(
						new ShoppingImportJobAcceptedResponse(jobId, active.status(), now),
						ReuseOutcome.COALESCED,
						identity.get().site()
					);
				}
			}

			if (identity.isPresent() && properties.getSharedCrawl().isCacheEnabled()) {
				ReusableJob cached = repository.findCachedLeader(identity.get().key(), now);
				if (cached != null) {
					repository.insertCachedFollower(jobId, userId, requestJson, requestHash, identity.get(), cached, now);
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
			repository.insertLeader(jobId, userId, requestJson, requestHash, identity.orElse(null), now);
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
		var job = queries.findOwned(userId, jobId);
		if (job == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping import job not found");
		}
		return new ShoppingImportJobResponse(job.id(), job.status(),
			readResult(job.resultJson(), job.requestJson()), readError(job.errorCode(), job.errorMessage()),
			job.attemptCount(), job.createdAt(), job.startedAt(), job.completedAt());
	}

	private void ensureQueueCapacity() {
		if (repository.activeLeaders() >= properties.getJobWorker().getMaxQueueSize()) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Shopping import queue is full");
		}
	}

	private void ensureUserCapacity(UUID userId) {
		if (repository.activeJobs(userId) >= properties.getJobWorker().getMaxActivePerUser()) {
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
}
