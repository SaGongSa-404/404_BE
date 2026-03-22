package com.fourohfour.backend.api;

import com.fourohfour.backend.api.bootstrap.ApiApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ApiApplication.class,
        properties = {
                "app.ai.gemini.api-key=",
                "app.ai.ollama.background-enhancement-enabled=false"
        }
)
@AutoConfigureMockMvc
class ActionDeckApiIntegrationTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from practice_card_events");
        jdbcTemplate.update("delete from practice_cards");
        jdbcTemplate.update("delete from saved_contents");
        jdbcTemplate.update("delete from users");
    }

    @Test
    void saveContentGeneratesActionCard() throws Exception {
        mockMvc.perform(post("/api/v1/content-links")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "https://example.com/gratitude-video",
                                  "title": "감사하는 마음 영상",
                                  "note": "하루를 감사로 마무리하는 방법",
                                  "tags": ["마음", "감사"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedContent.category").value("GRATITUDE"))
                .andExpect(jsonPath("$.savedContent.categoryLabel").value("감사"))
                .andExpect(jsonPath("$.practiceCard.actionTitle").value("오늘 감사한 일 3가지 떠올려보기"))
                .andExpect(jsonPath("$.practiceCard.status").value("OPEN"));
    }

    @Test
    void servesLabFrontend() throws Exception {
        String html = mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Action Deck Lab");
        assertThat(html).contains("링크 하나로 카드 만들기");
    }

    @Test
    void homeFacadeReturnsTodaysDeck() throws Exception {
        mockMvc.perform(post("/api/v1/content-links")
                .header("X-User-Id", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "url": "https://example.com/focus",
                          "title": "집중 루틴 만드는 법"
                        }
                        """)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/facades/home")
                        .header("X-User-Id", USER_ID)
                        .param("date", "2026-03-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openCardCount").value(1))
                .andExpect(jsonPath("$.completedTodayCount").value(0))
                .andExpect(jsonPath("$.recommendedCard.actionTitle").isNotEmpty())
                .andExpect(jsonPath("$.cards[0].sourceTitle").value("집중 루틴 만드는 법"));
    }

    @Test
    void canFetchCardDetail() throws Exception {
        String response = mockMvc.perform(post("/api/v1/content-links")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "https://example.com/routine",
                                  "title": "아침 루틴 만들기"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode payload = objectMapper.readTree(response);
        UUID cardId = UUID.fromString(payload.path("practiceCard").path("id").asText());

        mockMvc.perform(get("/api/v1/practice-cards/{cardId}", cardId)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailTitle").isNotEmpty())
                .andExpect(jsonPath("$.detailBody").isNotEmpty())
                .andExpect(jsonPath("$.ideaOptions[0]").isNotEmpty());
    }

    @Test
    void weeklyReportTracksSavedAndCompletedActions() throws Exception {
        mockMvc.perform(post("/api/v1/content-links")
                .header("X-User-Id", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "url": "https://example.com/routine",
                          "title": "아침 루틴 만들기"
                        }
                        """)).andExpect(status().isOk());

        String secondResponse = mockMvc.perform(post("/api/v1/content-links")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "https://example.com/thanks",
                                  "title": "감사하는 마음 영상"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode savedPayload = objectMapper.readTree(secondResponse);
        UUID gratitudeCardId = UUID.fromString(savedPayload.path("practiceCard").path("id").asText());

        mockMvc.perform(post("/api/v1/practice-cards/{cardId}/complete", gratitudeCardId)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "completionNote": "오늘 감사한 일 세 가지를 적었다"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        String report = mockMvc.perform(get("/api/v1/facades/reports/weekly")
                        .header("X-User-Id", USER_ID)
                        .param("date", "2026-03-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedCount").value(2))
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.completionRate").value(50))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode reportJson = objectMapper.readTree(report);
        assertThat(reportJson.path("insightMessage").asText()).contains("실천했어요");
        assertThat(reportJson.path("recentCompletions").get(0).path("actionTitle").asText())
                .isEqualTo("오늘 감사한 일 3가지 떠올려보기");
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
