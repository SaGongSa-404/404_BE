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
		ShoppingImportProperties.KreamProxy kreamProxy = properties.getKreamProxy();
		ShoppingImportProperties.NaverProxy naverProxy = properties.getNaverProxy();
		kreamProxy.validate();
		naverProxy.validate();
		JsoupPageFetcher jsoupPageFetcher = new JsoupPageFetcher(
			properties.getMaxResponseBytes(),
			kreamProxy,
			naverProxy,
			properties.getAblyApi()
		);
		if (browserFetch.isEnabled()) {
			return new FallbackPageFetcher(
				jsoupPageFetcher,
				new BrowserPageFetcher(browserFetch, properties.getMaxResponseBytes(), kreamProxy, naverProxy)
			);
		}
		return jsoupPageFetcher;
	}
}
