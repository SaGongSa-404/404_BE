package com.fourohfour.backend.modules.content.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.ollama")
public record OllamaProperties(
        boolean enabled,
        boolean backgroundEnhancementEnabled,
        String model,
        String baseUrl,
        String executablePath,
        int timeoutSeconds,
        int maxOutputTokens
) {
}
