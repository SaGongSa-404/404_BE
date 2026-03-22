package com.fourohfour.backend.modules.content.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourohfour.backend.modules.content.infrastructure.GeminiProperties;
import com.fourohfour.backend.modules.content.infrastructure.RemoteMediaFetcher;
import com.fourohfour.backend.modules.content.infrastructure.ScraperProperties;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiActionCardGeneratorTest {

    private final MockWebServer mockWebServer = new MockWebServer();

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void generateParsesGeminiJsonResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {
                                    "text": "{\\"category\\":\\"ROUTINE\\",\\"actionTitle\\":\\"오늘 5분 루틴 시작\\",\\"actionDetail\\":\\"오늘 같은 시간에 5분만 반복해보세요.\\",\\"detailTitle\\":\\"루틴을 더 잘 시작하는 법\\",\\"detailBody\\":\\"루틴은 시간과 장소를 함께 정하면 더 쉽게 붙습니다.\\",\\"ideaOptions\\":[\\"실행 시간 고정하기\\",\\"신호 행동 정하기\\"],\\"encouragementMessage\\":\\"작게 시작하면 이어가기 쉬워요.\\",\\"rationale\\":\\"콘텐츠의 핵심을 오늘의 루틴으로 압축했어요.\\",\\"estimatedMinutes\\":5,\\"energyLevel\\":\\"LOW\\"}"
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """));

        GeminiActionCardGenerator generator = new GeminiActionCardGenerator(
                RestClient.builder().baseUrl(mockWebServer.url("/").toString()).build(),
                new GeminiProperties("test-key", "gemini-2.5-flash-lite", mockWebServer.url("/").toString()),
                new ObjectMapper(),
                new RemoteMediaFetcher(HttpClient.newHttpClient(), new ScraperProperties(3000, "test-agent"))
        );

        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://example.com/routine",
                        "example.com",
                        "아침 루틴 만들기",
                        "작은 습관 시작하기",
                        List.of("루틴"),
                        "아침 루틴 만들기",
                        "반복의 힘",
                        "작게 시작하고 반복하라",
                        "generic",
                        "writer",
                        "Example",
                        List.of()
                ),
                LocalDate.of(2026, 3, 21)
        );

        assertThat(card.category().name()).isEqualTo("ROUTINE");
        assertThat(card.actionTitle()).isEqualTo("오늘 5분 루틴 시작");
        assertThat(card.detailTitle()).isEqualTo("루틴을 더 잘 시작하는 법");
        assertThat(card.ideaOptions()).contains("실행 시간 고정하기");
        assertThat(card.estimatedMinutes()).isEqualTo(5);
        assertThat(card.energyLevel().name()).isEqualTo("LOW");
    }

    @Test
    void includesYoutubeFileDataWhenSourceIsYoutube() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {
                                    "text": "{\\"category\\":\\"FITNESS\\",\\"actionTitle\\":\\"오늘 10분 몸 움직이기\\",\\"actionDetail\\":\\"운동화를 신고 바로 10분 걸어보세요.\\",\\"detailTitle\\":\\"운동 시작 장벽 낮추기\\",\\"detailBody\\":\\"옷 갈아입기와 나가기처럼 첫 행동을 단순화하세요.\\",\\"ideaOptions\\":[\\"운동복 갈아입기\\",\\"5분만 나가기\\"],\\"encouragementMessage\\":\\"한 번 움직이면 흐름이 붙어요.\\",\\"rationale\\":\\"운동 자극 콘텐츠를 오늘 행동으로 바꿨어요.\\",\\"estimatedMinutes\\":10,\\"energyLevel\\":\\"MEDIUM\\"}"
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """));

        GeminiActionCardGenerator generator = new GeminiActionCardGenerator(
                RestClient.builder().baseUrl(mockWebServer.url("/").toString()).build(),
                new GeminiProperties("test-key", "gemini-2.5-flash-lite", mockWebServer.url("/").toString()),
                new ObjectMapper(),
                new RemoteMediaFetcher(HttpClient.newHttpClient(), new ScraperProperties(3000, "test-agent"))
        );

        generator.generate(
                new ActionCardGenerationSource(
                        "https://www.youtube.com/watch?v=abc123",
                        "youtube.com",
                        "운동자극 영상",
                        null,
                        List.of(),
                        "운동자극 영상",
                        "지금 움직이라는 내용",
                        "운동을 미루지 말라는 내용",
                        "youtube",
                        null,
                        "YouTube",
                        List.of()
                ),
                LocalDate.of(2026, 3, 21)
        );

        String requestBody = mockWebServer.takeRequest().getBody().readUtf8();
        assertThat(requestBody).contains("\"file_uri\":\"https://www.youtube.com/watch?v=abc123\"");
        assertThat(requestBody).contains("\"text\":\"You are an action-card generator");
    }
}
