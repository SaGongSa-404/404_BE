package com.sagongsa.backend.itemimport.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

	ShoppingImportJobScheduler(ShoppingImportJobWorker worker) {
		this.worker = worker;
	}

	@Scheduled(
		fixedDelayString = "${app.shopping.import.job-worker.fixed-delay-ms:500}",
		scheduler = "shoppingImportTaskScheduler"
	)
	void processNextJob() {
		worker.processNextJob();
	}

	@Scheduled(
		fixedDelayString = "${app.shopping.import.job-worker.cleanup-delay-ms:3600000}",
		scheduler = "shoppingImportTaskScheduler"
	)
	void cleanupExpiredJobs() {
		worker.cleanupExpiredJobs();
	}
}
