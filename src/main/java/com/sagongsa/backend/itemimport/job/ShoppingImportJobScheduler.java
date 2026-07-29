package com.sagongsa.backend.itemimport.job;

import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
	name = "app.shopping.import.job-worker.enabled",
	havingValue = "true",
	matchIfMissing = true
)
class ShoppingImportJobScheduler {

	private final ShoppingImportJobWorker worker;
	private final ShoppingImportProperties properties;
	private final Executor workerExecutor;

	ShoppingImportJobScheduler(
		ShoppingImportJobWorker worker,
		ShoppingImportProperties properties,
		@Qualifier("shoppingImportWorkerExecutor") Executor workerExecutor
	) {
		this.worker = worker;
		this.properties = properties;
		this.workerExecutor = workerExecutor;
	}

	@Scheduled(
		fixedDelayString = "${app.shopping.import.job-worker.fixed-delay-ms:500}",
		scheduler = "shoppingImportTaskScheduler"
	)
	void processNextJob() {
		if (!worker.hasClaimableJob()) {
			return;
		}

		CompletableFuture<?>[] workers = IntStream.range(0, properties.getJobWorker().getConcurrency())
			.mapToObj(ignored -> CompletableFuture.runAsync(worker::processNextJob, workerExecutor))
			.toArray(CompletableFuture[]::new);
		CompletableFuture.allOf(workers).join();
	}

	@Scheduled(
		fixedDelayString = "${app.shopping.import.job-worker.recovery-delay-ms:60000}",
		scheduler = "shoppingImportTaskScheduler"
	)
	void recoverStaleJobs() {
		worker.recoverStaleJobs();
	}

	@Scheduled(
		fixedDelayString = "${app.shopping.import.job-worker.cleanup-delay-ms:3600000}",
		scheduler = "shoppingImportTaskScheduler"
	)
	void cleanupExpiredJobs() {
		worker.cleanupExpiredJobs();
	}
}
