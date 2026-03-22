package com.fourohfour.backend.modules.content.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourohfour.backend.modules.content.domain.ActionCardCategory;
import com.fourohfour.backend.modules.content.infrastructure.GeminiProperties;
import com.fourohfour.backend.modules.content.infrastructure.RemoteMediaFetcher;
import com.fourohfour.backend.modules.practice.domain.EnergyLevel;
import java.util.Base64;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GeminiActionCardGenerator {

    private final RestClient restClient;
    private final GeminiProperties geminiProperties;
    private final ObjectMapper objectMapper;
    private final RemoteMediaFetcher remoteMediaFetcher;

    public GeminiActionCardGenerator(
            RestClient geminiRestClient,
            GeminiProperties geminiProperties,
            ObjectMapper objectMapper,
            RemoteMediaFetcher remoteMediaFetcher
    ) {
        this.restClient = geminiRestClient;
        this.geminiProperties = geminiProperties;
        this.objectMapper = objectMapper;
        this.remoteMediaFetcher = remoteMediaFetcher;
    }

    public GeneratedPracticeCard generate(ActionCardGenerationSource source, LocalDate today) {
        if (!geminiProperties.enabled()) {
            throw new IllegalStateException("Gemini API key is not configured.");
        }

        GeminiResponse response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:generateContent")
                        .queryParam("key", geminiProperties.apiKey())
                        .build(geminiProperties.model()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildRequest(source))
                .retrieve()
                .body(GeminiResponse.class);

        String rawJson = extractTextResponse(response);
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalStateException("Gemini returned an empty response.");
        }

        try {
            JsonNode json = objectMapper.readTree(rawJson);
            return new GeneratedPracticeCard(
                    parseCategory(json.path("category").asText()),
                    json.path("actionTitle").asText("오늘 바로 할 수 있는 1가지 정하기"),
                    json.path("actionDetail").asText("저장한 콘텐츠를 보고 가장 작은 행동 하나를 지금 시작해보세요."),
                    json.path("detailTitle").asText("이 카드를 더 자세히 보는 방법"),
                    json.path("detailBody").asText("콘텐츠의 맥락을 더 자세히 읽고, 오늘 실행 가능한 방식으로 작게 잘라보세요."),
                    parseDetailSections(json.path("detailSections")),
                    parseIdeaOptions(json.path("ideaOptions")),
                    json.path("encouragementMessage").asText("지금 시작하면 저장이 행동으로 바뀌어요."),
                    json.path("rationale").asText("저장한 콘텐츠의 핵심을 오늘의 행동으로 압축했어요."),
                    clampMinutes(json.path("estimatedMinutes").asInt(10)),
                    parseEnergy(json.path("energyLevel").asText()),
                    today
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Gemini response could not be parsed as action card JSON.", exception);
        }
    }

    private Object buildRequest(ActionCardGenerationSource source) {
        String prompt = """
                You are an action-card generator for a Korean product.
                Your task is to turn saved content into one concrete action the user can do today.

                Rules:
                - Respond in JSON only.
                - Output fields: category, actionTitle, actionDetail, detailTitle, detailBody, detailSections, ideaOptions, encouragementMessage, rationale, estimatedMinutes, energyLevel
                - category must be one of: GRATITUDE, ROUTINE, FITNESS, RELATIONSHIP, COOKING, CLEANING, PRODUCTIVITY, MINDFULNESS, LEARNING, FINANCE, GENERAL
                - energyLevel must be one of: LOW, MEDIUM, HIGH
                - actionTitle must be Korean and under 35 characters
                - actionDetail must be Korean and practical
                - detailTitle must be Korean and under 40 characters
                - detailBody must be Korean and explain the action in more depth
                - detailSections must be a JSON array with 0 to 3 objects. Each object has title, body, items
                - ideaOptions must be a JSON array with 0 to 4 Korean strings for extra ideas or variations
                - Make the action something the user can do today without extra prep
                - Avoid summaries, advice lists, or vague inspiration
                - Base the card on a specific concept from the source, not a generic self-help pattern
                - Reuse an actual source concept in the actionTitle or actionDetail
                - Do not output generic goal-setting or priority-setting cards unless the source explicitly talks about goals or priorities
                - If the source is about exercise motivation, the card should involve moving the body today
                - If the source is about habits or routines, the card should involve one repeatable habit action today
                - If a YouTube video is attached, rely on the video's spoken content and not only the title
                - If the source contains multiple useful ideas, keep one primary action in the card and preserve the other useful ideas inside ideaOptions
                - For cooking or cleaning content, preserve order and accuracy of important steps in detailBody and ideaOptions
                - For tool recommendation content, choose one primary tool as the action and preserve the other recommended tools in ideaOptions

                Source URL: %s
                Source domain: %s
                Source type: %s
                Requested title: %s
                Requested note: %s
                Scraped title: %s
                Scraped description: %s
                Scraped author: %s
                Scraped site name: %s
                Tags: %s
                Scraped text:
                %s
                """.formatted(
                safe(source.url()),
                safe(source.sourceDomain()),
                safe(source.sourceType()),
                safe(source.requestedTitle()),
                safe(source.requestedNote()),
                safe(source.scrapedTitle()),
                safe(source.scrapedDescription()),
                safe(source.author()),
                safe(source.siteName()),
                String.join(", ", source.tags() == null ? List.of() : source.tags()),
                safe(source.scrapedText())
        );

        List<GeminiPart> parts = new java.util.ArrayList<>();
        if ("youtube".equalsIgnoreCase(source.sourceType())) {
            parts.add(GeminiPart.forFileUri(source.url()));
        }
        if (source.imageUrls() != null && !source.imageUrls().isEmpty()) {
            for (String imageUrl : source.imageUrls().stream().limit(2).toList()) {
                RemoteMediaFetcher.MediaAsset mediaAsset = remoteMediaFetcher.fetch(imageUrl);
                if (mediaAsset != null) {
                    parts.add(GeminiPart.forInlineData(
                            Base64.getEncoder().encodeToString(mediaAsset.bytes()),
                            mediaAsset.mimeType()
                    ));
                }
            }
        }
        parts.add(GeminiPart.forText(prompt));

        return new GeminiRequest(
                List.of(new GeminiContent(parts)),
                new GeminiGenerationConfig("application/json", 0.3)
        );
    }

    private String extractTextResponse(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }
        GeminiCandidate candidate = response.candidates().getFirst();
        if (candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
            return null;
        }
        return candidate.content().parts().getFirst().text();
    }

    private ActionCardCategory parseCategory(String raw) {
        try {
            return ActionCardCategory.valueOf(raw.trim().toUpperCase());
        } catch (Exception exception) {
            return ActionCardCategory.GENERAL;
        }
    }

    private EnergyLevel parseEnergy(String raw) {
        try {
            return EnergyLevel.valueOf(raw.trim().toUpperCase());
        } catch (Exception exception) {
            return EnergyLevel.LOW;
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

    private List<String> parseIdeaOptions(JsonNode jsonNode) {
        if (jsonNode == null || !jsonNode.isArray()) {
            return List.of();
        }
        java.util.ArrayList<String> items = new java.util.ArrayList<>();
        for (JsonNode item : jsonNode) {
            String value = item.asText(null);
            if (value == null || value.isBlank()) {
                continue;
            }
            items.add(value.trim());
            if (items.size() >= 4) {
                break;
            }
        }
        return List.copyOf(items);
    }

    private List<PracticeCardSection> parseDetailSections(JsonNode jsonNode) {
        if (jsonNode == null || !jsonNode.isArray()) {
            return List.of();
        }
        java.util.ArrayList<PracticeCardSection> sections = new java.util.ArrayList<>();
        for (JsonNode item : jsonNode) {
            String title = item.path("title").asText(null);
            String body = item.path("body").asText(null);
            List<String> items = parseIdeaOptions(item.path("items"));
            if ((title == null || title.isBlank()) && (body == null || body.isBlank()) && items.isEmpty()) {
                continue;
            }
            sections.add(new PracticeCardSection(title, body, items));
            if (sections.size() >= 3) {
                break;
            }
        }
        return List.copyOf(sections);
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "(none)";
        }
        if (value.length() > 4000) {
            return value.substring(0, 4000);
        }
        return value;
    }

    private record GeminiRequest(
            List<GeminiContent> contents,
            GeminiGenerationConfig generationConfig
    ) {
    }

    private record GeminiContent(List<GeminiPart> parts) {
    }

    private record GeminiPart(String text, GeminiFileData file_data, GeminiInlineData inline_data) {

        private static GeminiPart forText(String text) {
            return new GeminiPart(text, null, null);
        }

        private static GeminiPart forFileUri(String fileUri) {
            return new GeminiPart(null, new GeminiFileData(fileUri), null);
        }

        private static GeminiPart forInlineData(String data, String mimeType) {
            return new GeminiPart(null, null, new GeminiInlineData(data, mimeType));
        }
    }

    private record GeminiFileData(String file_uri) {
    }

    private record GeminiInlineData(String data, String mime_type) {
    }

    private record GeminiGenerationConfig(
            String responseMimeType,
            double temperature
    ) {
    }

    private record GeminiResponse(List<GeminiCandidate> candidates) {
    }

    private record GeminiCandidate(GeminiCandidateContent content) {
    }

    private record GeminiCandidateContent(List<GeminiResponsePart> parts) {
    }

    private record GeminiResponsePart(String text) {
    }
}
