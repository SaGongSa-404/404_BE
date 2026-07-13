package com.sagongsa.backend.itemimport.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

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
}
