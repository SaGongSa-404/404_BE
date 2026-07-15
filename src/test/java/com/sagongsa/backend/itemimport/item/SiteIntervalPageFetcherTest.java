package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SiteIntervalPageFetcherTest {

	@Test
	void spacesOnlyOliveYoungRequestStartsByReservedInterval() {
		AtomicLong nowNanos = new AtomicLong();
		List<Long> sleeps = new ArrayList<>();
		AtomicInteger fetches = new AtomicInteger();
		SiteIntervalPageFetcher fetcher = new SiteIntervalPageFetcher(
			uri -> {
				fetches.incrementAndGet();
				return page(uri);
			},
			properties(true),
			nowNanos::get,
			nanos -> {
				sleeps.add(nanos);
				nowNanos.addAndGet(nanos);
			},
			() -> Duration.ofMillis(2_500).toNanos()
		);

		fetcher.fetch(URI.create("https://www.oliveyoung.co.kr/store/goods/1"));
		fetcher.fetch(URI.create("https://www.musinsa.com/products/1"));
		fetcher.fetch(URI.create("https://m.oliveyoung.co.kr/store/goods/2"));
		fetcher.fetch(URI.create("https://oliveyoung.co.kr/store/goods/3"));

		assertThat(fetches).hasValue(4);
		assertThat(sleeps).containsExactly(
			Duration.ofMillis(2_500).toNanos(),
			Duration.ofMillis(2_500).toNanos()
		);
	}

	@Test
	void doesNotWaitWhenOliveYoungIntervalIsDisabled() {
		AtomicInteger sleeps = new AtomicInteger();
		SiteIntervalPageFetcher fetcher = new SiteIntervalPageFetcher(
			SiteIntervalPageFetcherTest::page,
			properties(false),
			System::nanoTime,
			nanos -> sleeps.incrementAndGet(),
			() -> Duration.ofSeconds(2).toNanos()
		);

		fetcher.fetch(URI.create("https://www.oliveyoung.co.kr/store/goods/1"));
		fetcher.fetch(URI.create("https://www.oliveyoung.co.kr/store/goods/2"));

		assertThat(sleeps).hasValue(0);
	}

	private static ShoppingImportProperties.SiteThrottle.OliveYoung properties(boolean enabled) {
		ShoppingImportProperties.SiteThrottle.OliveYoung properties = new ShoppingImportProperties.SiteThrottle.OliveYoung();
		properties.setEnabled(enabled);
		properties.setMinStartInterval(Duration.ofSeconds(2));
		properties.setMaxStartInterval(Duration.ofSeconds(3));
		return properties;
	}

	private static FetchedPage page(URI uri) {
		return new FetchedPage(uri, uri, 200, "text/html", "<html></html>");
	}
}
