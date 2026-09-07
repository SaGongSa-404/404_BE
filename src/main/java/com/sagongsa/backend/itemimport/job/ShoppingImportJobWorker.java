package com.sagongsa.backend.itemimport.job;

import com.sagongsa.backend.itemimport.job.ShoppingImportJobRepository.ClaimedJob;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobRepository.JobFailure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShoppingImportJobWorker {

	private static final Logger log = LoggerFactory.getLogger(ShoppingImportJobWorker.class);

	private final ShoppingImportJobRepository repository;
	private final ObjectMapper objectMapper;
	private final ShoppingLinkImportService shoppingLinkImportService;
	private final ShoppingImportMetrics metrics;

	public ShoppingImportJobWorker(
		ShoppingImportJobRepository repository,
		ObjectMapper objectMapper,
		ShoppingLinkImportService shoppingLinkImportService,
		ShoppingImportMetrics metrics
	) {
		this.repository = repository;
		this.objectMapper = objectMapper;
		this.shoppingLinkImportService = shoppingLinkImportService;
		this.metrics = metrics;
	}

	public boolean processNextJob() {
		ClaimedJob job = repository.claim();
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
			if (!repository.markSucceeded(job, objectMapper.writeValueAsString(result))) {
				return true;
			}
			log.info(
				"shopping import job succeeded jobId={} attempt={} durationMs={}",
				job.id(),
				job.attemptCount(),
				elapsedMillis(startedAtNanos)
			);
		} catch (Exception exception) {
			metrics.recordUpstreamRejection(job.sourceSite(), exception);
			if (!repository.markFailed(job, failureFor(exception))) {
				return true;
			}
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

	public boolean hasClaimableJob() { return repository.hasClaimableJob(); }
	public int cleanupExpiredJobs() { return repository.cleanupExpiredJobs(); }
	public int recoverStaleJobs() { return repository.recoverStaleJobs(); }

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

}
