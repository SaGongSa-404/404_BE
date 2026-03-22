package com.fourohfour.backend.modules.content.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.scraping")
public record ScraperProperties(
        int timeoutMillis,
        String userAgent
) {
}

