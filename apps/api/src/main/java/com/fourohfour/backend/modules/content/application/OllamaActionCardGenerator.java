package com.fourohfour.backend.modules.content.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourohfour.backend.modules.content.domain.ActionCardCategory;
import com.fourohfour.backend.modules.content.infrastructure.OllamaCliRunner;
import com.fourohfour.backend.modules.practice.domain.EnergyLevel;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OllamaActionCardGenerator {

    private final OllamaCliRunner ollamaCliRunner;
    private final ObjectMapper objectMapper;
    private final HeuristicActionCardGenerator heuristicActionCardGenerator;

    public OllamaActionCardGenerator(
            OllamaCliRunner ollamaCliRunner,
            ObjectMapper objectMapper,
            HeuristicActionCardGenerator heuristicActionCardGenerator
    ) {
        this.ollamaCliRunner = ollamaCliRunner;
        this.objectMapper = objectMapper;
        this.heuristicActionCardGenerator = heuristicActionCardGenerator;
    }

    public GeneratedPracticeCard generate(ActionCardGenerationSource source, LocalDate today) {
        GeneratedPracticeCard fallbackCard = heuristicActionCardGenerator.generate(source, today);
        String rawOutput = ollamaCliRunner.runPrompt(buildPrompt(source));
        String jsonPayload = extractJsonPayload(rawOutput);

        try {
            JsonNode json = objectMapper.readTree(jsonPayload);
            return new GeneratedPracticeCard(
                    parseCategory(json.path("category").asText(), fallbackCard.category()),
                    readShortText(json, "actionTitle", fallbackCard.actionTitle(), 35),
                    readShortText(json, "actionDetail", fallbackCard.actionDetail(), 140),
                    fallbackCard.detailTitle(),
                    fallbackCard.detailBody(),
                    fallbackCard.detailSections(),
                    fallbackCard.ideaOptions(),
                    readShortText(json, "encouragementMessage", fallbackCard.encouragementMessage(), 45),
                    readShortText(json, "rationale", fallbackCard.rationale(), 60),
                    clampMinutes(json.path("estimatedMinutes").asInt(fallbackCard.estimatedMinutes())),
                    parseEnergy(json.path("energyLevel").asText(), fallbackCard.energyLevel()),
                    today
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Ollama response could not be parsed as action card JSON.", exception);
        }
    }

    private String buildPrompt(ActionCardGenerationSource source) {
        String tags = source.tags() == null || source.tags().isEmpty()
                ? "(none)"
                : String.join(", ", source.tags());

        return """
                You generate one Korean action card from saved content.
                Respond with minified compact JSON on one line only.
                Prefer requested title, note, and tags over noisy scraped text.
                Keep the whole response concise and practical.

                Required JSON fields:
                category, actionTitle, actionDetail, encouragementMessage, rationale, estimatedMinutes, energyLevel

                Rules:
                - category must be one of: GRATITUDE, ROUTINE, FITNESS, RELATIONSHIP, COOKING, CLEANING, EVENT, PRODUCTIVITY, MINDFULNESS, LEARNING, FINANCE, GENERAL
                - energyLevel must be one of: LOW, MEDIUM, HIGH
                - actionTitle under 35 Korean characters
                - actionDetail under 140 Korean characters
                - encouragementMessage under 45 Korean characters
                - rationale under 60 Korean characters
                - Keep every string short and concrete
                - No markdown
                - Do not add any explanation outside the JSON object

                Source URL: %s
                Source domain: %s
                Source type: %s
                Requested title: %s
                Requested note: %s
                Tags: %s
                Scraped title: %s
                Scraped description: %s
                Scraped text: %s
                Author: %s
                Site name: %s
                Image URLs: %s
                """.formatted(
                safe(source.url(), 300),
                safe(source.sourceDomain(), 120),
                safe(source.sourceType(), 120),
                safe(source.requestedTitle(), 220),
                safe(source.requestedNote(), 220),
                safe(tags, 160),
                safe(source.scrapedTitle(), 160),
                safe(source.scrapedDescription(), 220),
                safe(source.scrapedText(), 220),
                safe(source.author(), 120),
                safe(source.siteName(), 120),
                safe(String.join(", ", source.imageUrls() == null ? List.of() : source.imageUrls()), 120)
        );
    }

    private String extractJsonPayload(String rawOutput) {
        String trimmed = rawOutput == null ? "" : rawOutput.trim();
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        throw new IllegalStateException("Ollama output did not contain JSON.");
    }

    private ActionCardCategory parseCategory(String raw, ActionCardCategory fallback) {
        try {
            return ActionCardCategory.valueOf(raw.trim().toUpperCase());
        } catch (Exception exception) {
            return fallback;
        }
    }

    private EnergyLevel parseEnergy(String raw, EnergyLevel fallback) {
        try {
            return EnergyLevel.valueOf(raw.trim().toUpperCase());
        } catch (Exception exception) {
            return fallback;
        }
    }

    private int clampMinutes(int minutes) {
        if (minutes < 3) {
            return 3;
        }
        if (minutes > 60) {
            return 60;
        }
        return minutes;
    }

    private String readShortText(JsonNode json, String fieldName, String fallback, int maxLength) {
        String value = json.path(fieldName).asText(null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private String safe(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "(none)";
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength);
        }
        return normalized;
    }
}
