package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ShoppingImportPropertiesTest {

	@Test
	void limitsActiveJobsToOnePerUserByDefault() {
		ShoppingImportProperties properties = new ShoppingImportProperties();

		assertThat(properties.getJobWorker().getMaxActivePerUser()).isEqualTo(1);
	}

	@Test
	void fallsBackToOneWhenMaxActivePerUserIsNotPositive() {
		ShoppingImportProperties properties = new ShoppingImportProperties();

		properties.getJobWorker().setMaxActivePerUser(0);

		assertThat(properties.getJobWorker().getMaxActivePerUser()).isEqualTo(1);
	}

	@Test
	void enablesSharedCrawlWithShortCacheTtlsByDefault() {
		ShoppingImportProperties.SharedCrawl sharedCrawl = new ShoppingImportProperties().getSharedCrawl();

		assertThat(sharedCrawl.isCoalescingEnabled()).isTrue();
		assertThat(sharedCrawl.isCacheEnabled()).isTrue();
		assertThat(sharedCrawl.getSuccessTtl()).isEqualTo(Duration.ofMinutes(5));
		assertThat(sharedCrawl.getFailureTtl()).isEqualTo(Duration.ofSeconds(30));
	}

	@Test
	void rejectsNonPositiveSharedCrawlTtls() {
		ShoppingImportProperties.SharedCrawl sharedCrawl = new ShoppingImportProperties().getSharedCrawl();

		sharedCrawl.setSuccessTtl(Duration.ZERO);
		sharedCrawl.setFailureTtl(Duration.ofSeconds(-1));

		assertThat(sharedCrawl.getSuccessTtl()).isEqualTo(Duration.ofMinutes(5));
		assertThat(sharedCrawl.getFailureTtl()).isEqualTo(Duration.ofSeconds(30));
	}
}
