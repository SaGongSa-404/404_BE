package com.sagongsa.backend.itemimport.item;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class SiteIntervalPageFetcher implements PageFetcher {

	private final PageFetcher delegate;
	private final boolean oliveYoungEnabled;
	private final long minIntervalNanos;
	private final long maxIntervalNanos;
	private final LongSupplier nanoTime;
	private final Sleeper sleeper;
	private final LongSupplier intervalNanos;
	private long nextOliveYoungStartNanos;

	SiteIntervalPageFetcher(
		PageFetcher delegate,
		ShoppingImportProperties.SiteThrottle.OliveYoung properties
	) {
		this(
			delegate,
			properties,
			System::nanoTime,
			TimeUnit.NANOSECONDS::sleep,
			() -> randomInterval(properties.getMinStartInterval(), properties.getMaxStartInterval())
		);
	}

	SiteIntervalPageFetcher(
		PageFetcher delegate,
		ShoppingImportProperties.SiteThrottle.OliveYoung properties,
		LongSupplier nanoTime,
		Sleeper sleeper,
		LongSupplier intervalNanos
	) {
		this.delegate = delegate;
		this.oliveYoungEnabled = properties.isEnabled();
		this.minIntervalNanos = properties.getMinStartInterval().toNanos();
		this.maxIntervalNanos = properties.getMaxStartInterval().toNanos();
		this.nanoTime = nanoTime;
		this.sleeper = sleeper;
		this.intervalNanos = intervalNanos;
	}

	@Override
	public FetchedPage fetch(URI uri) {
		if (oliveYoungEnabled && isOliveYoung(uri)) {
			waitForStartInterval();
		}
		return delegate.fetch(uri);
	}

	private synchronized void waitForStartInterval() {
		long now = nanoTime.getAsLong();
		long delayNanos = Math.max(0, nextOliveYoungStartNanos - now);
		try {
			if (delayNanos > 0) {
				sleeper.sleep(delayNanos);
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Shopping page request interval wait was interrupted",
				exception
			);
		}
		long selectedInterval = Math.max(minIntervalNanos, Math.min(maxIntervalNanos, intervalNanos.getAsLong()));
		nextOliveYoungStartNanos = nanoTime.getAsLong() + selectedInterval;
	}

	private boolean isOliveYoung(URI uri) {
		String host = uri == null || uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		return host.equals("oliveyoung.co.kr") || host.endsWith(".oliveyoung.co.kr");
	}

	private static long randomInterval(Duration min, Duration max) {
		long minNanos = min.toNanos();
		long maxNanos = max.toNanos();
		if (minNanos == maxNanos) {
			return minNanos;
		}
		return ThreadLocalRandom.current().nextLong(minNanos, maxNanos + 1);
	}

	@FunctionalInterface
	interface Sleeper {
		void sleep(long nanos) throws InterruptedException;
	}
}
