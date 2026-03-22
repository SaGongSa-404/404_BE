package com.fourohfour.backend.modules.content.infrastructure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RemoteMediaFetcher {

    private static final int MAX_BYTES = 2_500_000;

    private final HttpClient httpClient;
    private final ScraperProperties scraperProperties;

    public RemoteMediaFetcher(HttpClient httpClient, ScraperProperties scraperProperties) {
        this.httpClient = httpClient;
        this.scraperProperties = scraperProperties;
    }

    public MediaAsset fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(scraperProperties.timeoutMillis()))
                    .header("User-Agent", scraperProperties.userAgent())
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("Content-Type").orElse("image/jpeg");
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                return null;
            }
            if (body.length > MAX_BYTES) {
                return new MediaAsset(slice(body, MAX_BYTES), contentType);
            }
            return new MediaAsset(body, contentType);
        } catch (Exception exception) {
            return null;
        }
    }

    private byte[] slice(byte[] source, int maxBytes) {
        byte[] bytes = new byte[maxBytes];
        System.arraycopy(source, 0, bytes, 0, maxBytes);
        return bytes;
    }

    public record MediaAsset(byte[] bytes, String mimeType) {
    }
}
