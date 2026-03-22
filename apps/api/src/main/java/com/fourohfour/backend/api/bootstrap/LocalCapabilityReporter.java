package com.fourohfour.backend.api.bootstrap;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class LocalCapabilityReporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalCapabilityReporter.class);
    private final com.fourohfour.backend.modules.content.infrastructure.OllamaProperties ollamaProperties;

    public LocalCapabilityReporter(com.fourohfour.backend.modules.content.infrastructure.OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Local capability check: GPU={}, OCR(tesseract)={}, OllamaCLI={}, OllamaHTTP={}",
                commandAvailable(List.of("nvidia-smi")) ? "available" : "unavailable",
                commandAvailable(List.of("tesseract")) ? "available" : "unavailable",
                detectOllamaCli().orElse("unavailable"),
                detectOllamaHttp().orElse("unreachable"));
    }

    private boolean commandAvailable(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception exception) {
            return false;
        }
    }

    private Optional<String> detectOllamaCli() {
        Optional<String> fromLinux = firstLine(List.of("bash", "-lc", "command -v ollama || true"));
        if (fromLinux.isPresent() && !fromLinux.get().isBlank()) {
            return Optional.of("linux-cli:" + fromLinux.get().trim());
        }

        Optional<String> fromWindows = firstLine(List.of(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "(Get-Command ollama -ErrorAction SilentlyContinue).Source"
        ));
        return fromWindows.filter(value -> !value.isBlank()).map(value -> "windows-cli:" + value.trim());
    }

    private Optional<String> detectOllamaHttp() {
        List<String> candidates = new ArrayList<>();
        candidates.add(normalizeBaseUrl(ollamaProperties.baseUrl()));
        candidates.add("http://localhost:11434");
        candidates.add("http://" + readWslHostIp() + ":11434");

        for (String baseUrl : candidates) {
            if (baseUrl.contains("null")) {
                continue;
            }
            try {
                HttpClient httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build();
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                                .timeout(Duration.ofSeconds(2))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                if (response.statusCode() == 200) {
                    return Optional.of(baseUrl);
                }
            } catch (Exception ignored) {
            }
        }
        return Optional.empty();
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

    private String readWslHostIp() {
        try {
            Process process = new ProcessBuilder("bash", "-lc", "awk '/nameserver/ {print $2; exit}' /etc/resolv.conf")
                    .redirectErrorStream(true)
                    .start();
            process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.readLine();
            }
        } catch (Exception exception) {
            return null;
        }
    }

    private Optional<String> firstLine(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return Optional.ofNullable(reader.readLine());
            }
        } catch (Exception exception) {
            return Optional.empty();
        }
    }
}
