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
		kreamProxy.validate();
		JsoupPageFetcher jsoupPageFetcher = new JsoupPageFetcher(
			properties.getMaxResponseBytes(),
			kreamProxy,
			properties.getAblyApi()
		);
		PageFetcher delegate = jsoupPageFetcher;
		if (browserFetch.isEnabled()) {
			delegate = new FallbackPageFetcher(
				jsoupPageFetcher,
				new BrowserPageFetcher(browserFetch, properties.getMaxResponseBytes(), kreamProxy)
			);
		}
		ShoppingImportProperties.SiteThrottle.OliveYoung oliveYoung = properties
			.getSiteThrottle()
			.getOliveYoung();
		oliveYoung.validate();
		return new SiteIntervalPageFetcher(delegate, oliveYoung);
	}
}
