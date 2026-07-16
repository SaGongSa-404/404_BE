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
	void allowsTwelveConcurrentWorkers() {
		ShoppingImportProperties properties = new ShoppingImportProperties();

		properties.getJobWorker().setConcurrency(12);

		assertThat(properties.getJobWorker().getConcurrency()).isEqualTo(12);
	}

	@Test
	void limitsConcurrentWorkersToSixteen() {
		ShoppingImportProperties properties = new ShoppingImportProperties();

		properties.getJobWorker().setConcurrency(17);

		assertThat(properties.getJobWorker().getConcurrency()).isEqualTo(16);
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

	@Test
	void enablesSyncBridgeWithBoundedWaitByDefault() {
		ShoppingImportProperties.SyncBridge syncBridge = new ShoppingImportProperties().getSyncBridge();

		assertThat(syncBridge.isEnabled()).isTrue();
		assertThat(syncBridge.getWaitTimeout()).isEqualTo(Duration.ofSeconds(25));
		assertThat(syncBridge.getPollInterval()).isEqualTo(Duration.ofMillis(500));
	}

	@Test
	void rejectsNonPositiveSyncBridgeDurations() {
		ShoppingImportProperties.SyncBridge syncBridge = new ShoppingImportProperties().getSyncBridge();

		syncBridge.setWaitTimeout(Duration.ZERO);
		syncBridge.setPollInterval(Duration.ofMillis(-1));

		assertThat(syncBridge.getWaitTimeout()).isEqualTo(Duration.ofSeconds(25));
		assertThat(syncBridge.getPollInterval()).isEqualTo(Duration.ofMillis(500));
	}
}
