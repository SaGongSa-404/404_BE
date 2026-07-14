package com.sagongsa.backend.itemimport.job;

import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnProperty(
	name = "app.shopping.import.job-worker.enabled",
	havingValue = "true",
	matchIfMissing = true
)
class ShoppingImportJobSchedulerConfig {

	@Bean(name = "shoppingImportTaskScheduler")
	ThreadPoolTaskScheduler shoppingImportTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("shopping-import-worker-");
		scheduler.setWaitForTasksToCompleteOnShutdown(false);
		return scheduler;
	}

	@Bean(name = "shoppingImportWorkerExecutor")
	ThreadPoolTaskExecutor shoppingImportWorkerExecutor(ShoppingImportProperties properties) {
		int concurrency = properties.getJobWorker().getConcurrency();
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(concurrency);
		executor.setMaxPoolSize(concurrency);
		executor.setQueueCapacity(0);
		executor.setThreadNamePrefix("shopping-import-executor-");
		executor.setWaitForTasksToCompleteOnShutdown(false);
		return executor;
	}
}
