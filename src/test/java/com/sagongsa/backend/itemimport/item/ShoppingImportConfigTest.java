package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ShoppingImportConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(ShoppingImportConfig.class);

	@Test
	void usesJsoupPageFetcherByDefault() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(PageFetcher.class);
			assertThat(context.getBean(PageFetcher.class)).isInstanceOf(JsoupPageFetcher.class);
		});
	}

	@Test
	void usesBrowserPageFetcherWhenEnabled() {
		contextRunner
			.withPropertyValues(
				"app.shopping.import.browser-fetch.enabled=true",
				"app.shopping.import.browser-fetch.render-wait=PT5S"
			)
			.run(context -> {
				assertThat(context).hasSingleBean(PageFetcher.class);
				assertThat(context.getBean(PageFetcher.class)).isInstanceOf(BrowserPageFetcher.class);
				assertThat(context.getBean(ShoppingImportProperties.class)
					.getBrowserFetch()
					.getRenderWait()).isEqualTo(Duration.ofSeconds(5));
			});
	}
}
