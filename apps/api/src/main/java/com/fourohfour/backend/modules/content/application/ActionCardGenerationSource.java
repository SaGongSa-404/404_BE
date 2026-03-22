package com.fourohfour.backend.modules.content.application;

import java.util.List;

public record ActionCardGenerationSource(
        String url,
        String sourceDomain,
        String requestedTitle,
        String requestedNote,
        List<String> tags,
        String scrapedTitle,
        String scrapedDescription,
        String scrapedText,
        String sourceType,
        String author,
        String siteName,
        List<String> imageUrls
) {

    public String effectiveTitle() {
        if (scrapedTitle != null && !scrapedTitle.isBlank()) {
            return scrapedTitle;
        }
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            return requestedTitle;
        }
        return url;
    }

    public String effectiveDescription() {
        if (requestedNote != null && !requestedNote.isBlank()) {
            return requestedNote;
        }
        if (scrapedDescription != null && !scrapedDescription.isBlank()) {
            return scrapedDescription;
        }
        return "";
    }
}
