package com.fourohfour.backend.modules.content.infrastructure;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
@EnableConfigurationProperties({ScraperProperties.class, GeminiProperties.class, OllamaProperties.class})
public class ContentInfrastructureConfig {

    @Bean
    HttpClient contentHttpClient(ScraperProperties scraperProperties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(scraperProperties.timeoutMillis()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    RestClient geminiRestClient(RestClient.Builder builder, GeminiProperties geminiProperties) {
        return builder
                .baseUrl(geminiProperties.baseUrl())
                .build();
    }

    @Bean
    ThreadPoolTaskExecutor ollamaEnhancementExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ollama-enhancer-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(32);
        executor.initialize();
        return executor;
    }
}
