package com.fourohfour.backend.modules.content.application;

import java.util.List;

public record ScrapedContent(
        String requestedUrl,
        String resolvedUrl,
        String sourceDomain,
        String sourceType,
        String title,
        String description,
        String text,
        String author,
        String siteName,
        List<String> imageUrls
) {

    public String effectiveUrl() {
        if (resolvedUrl != null && !resolvedUrl.isBlank()) {
            return resolvedUrl;
        }
        return requestedUrl;
    }
}
