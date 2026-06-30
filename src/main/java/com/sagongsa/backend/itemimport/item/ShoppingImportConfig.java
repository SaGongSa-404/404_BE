package com.sagongsa.backend.itemimport.item;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ShoppingImportProperties.class)
public class ShoppingImportConfig {

	@Bean
	public PageFetcher pageFetcher(ShoppingImportProperties properties) {
		ShoppingImportProperties.BrowserFetch browserFetch = properties.getBrowserFetch();
		JsoupPageFetcher jsoupPageFetcher = new JsoupPageFetcher(properties.getMaxResponseBytes());
		if (browserFetch.isEnabled()) {
			return new FallbackPageFetcher(jsoupPageFetcher, new BrowserPageFetcher(browserFetch, properties.getMaxResponseBytes()));
		}
		return jsoupPageFetcher;
	}
}
