package com.sagongsa.backend.itemimport.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ShoppingImportMetricsTest {

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final ShoppingImportMetrics metrics = new ShoppingImportMetrics(registry);

	@Test
	void recordsSiteBoundedCacheCrawlAndQueueMetrics() {
		metrics.recordCacheLookup("oliveyoung", "success_hit");
		metrics.recordCoalesced("oliveyoung");
		metrics.recordActualCrawl("oliveyoung");
		metrics.recordQueueWait("oliveyoung", Duration.ofSeconds(2));

		assertThat(registry.get("shopping.import.cache.lookups").counter().count()).isEqualTo(1);
		assertThat(registry.get("shopping.import.coalesced").counter().count()).isEqualTo(1);
		assertThat(registry.get("shopping.import.crawls").counter().count()).isEqualTo(1);
		assertThat(registry.get("shopping.import.queue.wait").timer().count()).isEqualTo(1);
	}

	@Test
	void recordsOnlyConfirmedUpstream403And429Signals() {
		metrics.recordUpstreamRejection(
			"oliveyoung",
			new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Shopping page returned 403")
		);
		metrics.recordUpstreamRejection(
			"oliveyoung",
			new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate limited")
		);
		metrics.recordUpstreamRejection(
			"oliveyoung",
			new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch shopping page")
		);

		assertThat(registry.get("shopping.import.upstream.rejections").tag("status", "403").counter().count())
			.isEqualTo(1);
		assertThat(registry.get("shopping.import.upstream.rejections").tag("status", "429").counter().count())
			.isEqualTo(1);
	}
}
