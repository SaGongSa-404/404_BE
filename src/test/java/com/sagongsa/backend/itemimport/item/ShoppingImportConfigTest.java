package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ShoppingImportConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(ShoppingImportConfig.class);

	@Test
	void usesFallbackPageFetcherByDefault() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(PageFetcher.class);
			assertThat(context.getBean(PageFetcher.class)).isInstanceOf(FallbackPageFetcher.class);
		});
	}

	@Test
	void usesJsoupPageFetcherWhenDisabled() {
		contextRunner
			.withPropertyValues("app.shopping.import.browser-fetch.enabled=false")
			.run(context -> {
				assertThat(context).hasSingleBean(PageFetcher.class);
				assertThat(context.getBean(PageFetcher.class)).isInstanceOf(JsoupPageFetcher.class);
			});
	}

	@Test
	void usesFallbackPageFetcherWhenEnabled() {
		contextRunner
			.withPropertyValues(
				"app.shopping.import.browser-fetch.enabled=true",
				"app.shopping.import.max-response-bytes=65536",
				"app.shopping.import.browser-fetch.render-wait=PT5S"
			)
			.run(context -> {
				assertThat(context).hasSingleBean(PageFetcher.class);
				assertThat(context.getBean(PageFetcher.class)).isInstanceOf(FallbackPageFetcher.class);
				assertThat(context.getBean(ShoppingImportProperties.class)
					.getMaxResponseBytes()).isEqualTo(65536);
				assertThat(context.getBean(ShoppingImportProperties.class)
					.getBrowserFetch()
					.getRenderWait()).isEqualTo(Duration.ofSeconds(5));
			});
	}

	@Test
	void bindsKreamProxySettings() {
		contextRunner
			.withPropertyValues(
				"app.shopping.import.kream-proxy.enabled=true",
				"app.shopping.import.kream-proxy.type=http",
				"app.shopping.import.kream-proxy.host=proxy.internal",
				"app.shopping.import.kream-proxy.port=3128"
			)
			.run(context -> {
				assertThat(context).hasNotFailed();
				ShoppingImportProperties.KreamProxy proxy = context.getBean(ShoppingImportProperties.class)
					.getKreamProxy();
				assertThat(proxy.isEnabled()).isTrue();
				assertThat(proxy.getType()).isEqualTo(ShoppingImportProperties.KreamProxy.Type.HTTP);
				assertThat(proxy.getHost()).isEqualTo("proxy.internal");
				assertThat(proxy.getPort()).isEqualTo(3128);
			});
	}

	@Test
	void bindsAblyApiSettingsWithoutExposingThemToOtherFetchers() {
		contextRunner
			.withPropertyValues(
				"app.shopping.import.ably-api.enabled=true",
				"app.shopping.import.ably-api.anonymous-token=test-anonymous-token",
				"app.shopping.import.ably-api.timeout=PT4S"
			)
			.run(context -> {
				assertThat(context).hasNotFailed();
				ShoppingImportProperties.AblyApi ablyApi = context.getBean(ShoppingImportProperties.class)
					.getAblyApi();
				assertThat(ablyApi.isEnabled()).isTrue();
				assertThat(ablyApi.getAnonymousToken()).isEqualTo("test-anonymous-token");
				assertThat(ablyApi.getTimeout()).isEqualTo(Duration.ofSeconds(4));
			});
	}

	@Test
	void bindsSharedCrawlFeatureFlagsAndTtls() {
		contextRunner
			.withPropertyValues(
				"app.shopping.import.shared-crawl.coalescing-enabled=false",
				"app.shopping.import.shared-crawl.cache-enabled=false",
				"app.shopping.import.shared-crawl.success-ttl=PT1M",
				"app.shopping.import.shared-crawl.failure-ttl=PT15S"
			)
			.run(context -> {
				ShoppingImportProperties.SharedCrawl sharedCrawl = context
					.getBean(ShoppingImportProperties.class)
					.getSharedCrawl();
				assertThat(sharedCrawl.isCoalescingEnabled()).isFalse();
				assertThat(sharedCrawl.isCacheEnabled()).isFalse();
				assertThat(sharedCrawl.getSuccessTtl()).isEqualTo(Duration.ofMinutes(1));
				assertThat(sharedCrawl.getFailureTtl()).isEqualTo(Duration.ofSeconds(15));
			});
	}

	@Test
	void rejectsEnabledKreamProxyWithoutHost() {
		contextRunner
			.withPropertyValues("app.shopping.import.kream-proxy.enabled=true")
			.run(context -> assertThat(context).hasFailed());
	}
}
