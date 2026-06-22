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
				"app.shopping.import.browser-fetch.render-wait=PT5S"
			)
			.run(context -> {
				assertThat(context).hasSingleBean(PageFetcher.class);
				assertThat(context.getBean(PageFetcher.class)).isInstanceOf(FallbackPageFetcher.class);
				assertThat(context.getBean(ShoppingImportProperties.class)
					.getBrowserFetch()
					.getRenderWait()).isEqualTo(Duration.ofSeconds(5));
			});
	}
}
