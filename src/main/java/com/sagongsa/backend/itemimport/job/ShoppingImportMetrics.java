package com.sagongsa.backend.itemimport.job;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ShoppingImportMetrics {

	private static final Pattern UPSTREAM_STATUS_PATTERN = Pattern.compile("Shopping page returned (403|429)");
	private final MeterRegistry meterRegistry;

	public ShoppingImportMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	void recordCacheLookup(String site, String outcome) {
		meterRegistry.counter("shopping.import.cache.lookups", "site", site, "outcome", outcome).increment();
	}

	void recordCoalesced(String site) {
		meterRegistry.counter("shopping.import.coalesced", "site", site).increment();
	}

	void recordActualCrawl(String site) {
		meterRegistry.counter("shopping.import.crawls", "site", site).increment();
	}

	void recordQueueWait(String site, Duration wait) {
		Duration safeWait = wait.isNegative() ? Duration.ZERO : wait;
		Timer.builder("shopping.import.queue.wait")
			.tag("site", site)
			.publishPercentileHistogram()
			.register(meterRegistry)
			.record(safeWait);
	}

	void recordUpstreamRejection(String site, Throwable throwable) {
		String status = upstreamStatus(throwable);
		if (status != null) {
			meterRegistry.counter("shopping.import.upstream.rejections", "site", site, "status", status)
				.increment();
		}
	}

	private String upstreamStatus(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof ResponseStatusException response && response.getStatusCode().value() == 429) {
				return "429";
			}
			String message = current.getMessage();
			if (message != null) {
				Matcher matcher = UPSTREAM_STATUS_PATTERN.matcher(message);
				if (matcher.find()) {
					return matcher.group(1);
				}
			}
			current = current.getCause();
		}
		return null;
	}
}
