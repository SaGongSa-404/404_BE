package com.fourohfour.backend.modules.content.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        String baseUrl
) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}

