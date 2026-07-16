package com.sagongsa.backend.itemimport;

import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportService;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobError;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobResponse;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobService;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShoppingImportSyncService {

	private final ShoppingLinkImportService shoppingLinkImportService;
	private final ShoppingImportJobService shoppingImportJobService;
	private final ShoppingImportProperties properties;

	public ShoppingImportSyncService(
		ShoppingLinkImportService shoppingLinkImportService,
		ShoppingImportJobService shoppingImportJobService,
		ShoppingImportProperties properties
	) {
		this.shoppingLinkImportService = shoppingLinkImportService;
		this.shoppingImportJobService = shoppingImportJobService;
		this.properties = properties;
	}

	public ShoppingLinkImportResponse importLink(UUID userId, ShoppingLinkImportRequest request) {
		ShoppingImportProperties.SyncBridge bridge = properties.getSyncBridge();
		if (!bridge.isEnabled()) {
			return shoppingLinkImportService.importLink(request);
		}

		UUID jobId = shoppingImportJobService.submit(userId, request).jobId();
		long deadline = deadlineAfter(bridge.getWaitTimeout());
		while (true) {
			ShoppingImportJobResponse job = shoppingImportJobService.get(userId, jobId);
			switch (job.status()) {
				case SUCCEEDED -> {
					if (job.result() == null) {
						throw new IllegalStateException("Succeeded shopping import job has no result");
					}
					return job.result();
				}
				case FAILED -> throw failedJob(job.error());
				case PENDING, RUNNING -> waitForNextPoll(deadline, bridge.getPollInterval());
			}
		}
	}

	private long deadlineAfter(Duration timeout) {
		long timeoutNanos = timeout.toNanos();
		long now = System.nanoTime();
		return timeoutNanos >= Long.MAX_VALUE - now ? Long.MAX_VALUE : now + timeoutNanos;
	}

	private void waitForNextPoll(long deadline, Duration pollInterval) {
		long remainingNanos = deadline - System.nanoTime();
		if (remainingNanos <= 0) {
			throw new ResponseStatusException(
				HttpStatus.GATEWAY_TIMEOUT,
				"Shopping import is still processing. Retry the same request."
			);
		}

		long sleepNanos = Math.min(remainingNanos, pollInterval.toNanos());
		try {
			long millis = sleepNanos / 1_000_000;
			int nanos = (int) (sleepNanos % 1_000_000);
			Thread.sleep(millis, nanos);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Shopping import wait was interrupted.",
				exception
			);
		}
	}

	private ResponseStatusException failedJob(ShoppingImportJobError error) {
		String code = error == null ? "IMPORT_FAILED" : error.code();
		String message = error == null ? "쇼핑 링크 정보를 가져오지 못했습니다." : error.message();
		HttpStatus status = switch (code) {
			case "INVALID_JOB_PAYLOAD", "INVALID_OR_UNSUPPORTED_ITEM" -> HttpStatus.BAD_REQUEST;
			case "WORKER_INTERRUPTED" -> HttpStatus.SERVICE_UNAVAILABLE;
			default -> HttpStatus.BAD_GATEWAY;
		};
		return new ResponseStatusException(status, message);
	}
}
