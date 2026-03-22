package com.fourohfour.backend.modules.content.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OllamaCliRunner {

    private final OllamaProperties ollamaProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaCliRunner(OllamaProperties ollamaProperties, ObjectMapper objectMapper) {
        this.ollamaProperties = ollamaProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public Optional<String> resolveExecutablePath() {
        if (ollamaProperties.executablePath() != null && !ollamaProperties.executablePath().isBlank()) {
            return Optional.of(ollamaProperties.executablePath().trim());
        }

        Optional<String> discovered = firstLine(new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "(Get-Command ollama -ErrorAction SilentlyContinue).Source"
        ));
        if (discovered.isPresent() && !discovered.get().isBlank()) {
            return Optional.of(discovered.get().trim());
        }
        return Optional.empty();
    }

    public String runPrompt(String prompt) {
        if (!ollamaProperties.enabled()) {
            throw new IllegalStateException("Ollama is disabled.");
        }

        try {
            HttpResponse<String> response = sendGenerateRequest(
                    buildRequestBody(prompt),
                    Duration.ofSeconds(ollamaProperties.timeoutSeconds())
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Ollama generation failed: HTTP " + response.statusCode() + " " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            if ("length".equalsIgnoreCase(json.path("done_reason").asText())) {
                throw new IllegalStateException(
                        "Ollama generation hit max output tokens (" + ollamaProperties.maxOutputTokens() + ").");
            }
            String output = firstNonBlank(
                    json.path("response").asText(null),
                    json.path("thinking").asText(null)
            );
            if (output == null || output.isBlank()) {
                throw new IllegalStateException("Ollama generation failed: empty response");
            }
            return output.trim();
        } catch (Exception exception) {
            String message = exception.getMessage();
            if (message == null || message.isBlank()) {
                throw new IllegalStateException("Ollama invocation failed.", exception);
            }
            throw new IllegalStateException("Ollama invocation failed: " + message, exception);
        }
    }

    public boolean canReachServer(Duration timeout) {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(normalizeBaseUrl(ollamaProperties.baseUrl()) + "/api/tags"))
                            .timeout(timeout)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception exception) {
            return false;
        }
    }

    public void warmUpModel() {
        if (!ollamaProperties.enabled()) {
            return;
        }

        try {
            HttpResponse<String> response = sendGenerateRequest(
                    "{\"model\":\"" + escapeJson(ollamaProperties.model()) + "\","
                            + "\"format\":\"json\","
                            + "\"prompt\":\"Return exactly {\\\"ok\\\":true}.\","
                            + "\"options\":{\"temperature\":0,\"num_predict\":32},"
                            + "\"stream\":false}",
                    Duration.ofSeconds(Math.min(30, Math.max(10, ollamaProperties.timeoutSeconds())))
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String output = firstNonBlank(
                    json.path("response").asText(null),
                    json.path("thinking").asText(null)
            );
            if (output.isBlank()) {
                throw new IllegalStateException("empty response");
            }
        } catch (Exception exception) {
            String message = exception.getMessage();
            throw new IllegalStateException(
                    "Ollama warm-up failed: " + (message == null || message.isBlank() ? "unknown reason" : message),
                    exception
            );
        }
    }

    private HttpResponse<String> sendGenerateRequest(String requestBody, Duration timeout) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create(normalizeBaseUrl(ollamaProperties.baseUrl()) + "/api/generate"))
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private String buildRequestBody(String prompt) {
        return "{\"model\":\"" + escapeJson(ollamaProperties.model()) + "\","
                + "\"format\":\"json\","
                + "\"prompt\":\"" + escapeJson(prompt) + "\","
                + "\"options\":{\"temperature\":0.2,\"num_predict\":" + ollamaProperties.maxOutputTokens() + "},"
                + "\"stream\":false}";
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:11434";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private Optional<String> firstLine(ProcessBuilder processBuilder) {
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return Optional.ofNullable(reader.readLine());
            }
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
