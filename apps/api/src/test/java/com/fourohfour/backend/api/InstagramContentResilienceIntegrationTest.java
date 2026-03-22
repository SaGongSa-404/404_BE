package com.fourohfour.backend.api;

import com.fourohfour.backend.api.bootstrap.ApiApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourohfour.backend.modules.content.application.ContentScraper;
import com.fourohfour.backend.modules.content.application.ScrapedContent;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ApiApplication.class,
        properties = {
                "app.ai.gemini.api-key=",
                "app.ai.ollama.background-enhancement-enabled=false"
        }
)
@AutoConfigureMockMvc
class InstagramContentResilienceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContentScraper contentScraper;

    @Test
    void savesContentEvenWhenScrapedInstagramMetadataIsVeryLong() throws Exception {
        String longTitle = "인스타그램 릴스 ".repeat(40);
        String longDescription = "이 설명은 매우 길어서 저장 컬럼 길이를 쉽게 넘길 수 있는 인스타 메타 설명이다. ".repeat(40);

        given(contentScraper.scrape("https://www.instagram.com/reel/example/"))
                .willReturn(new ScrapedContent(
                        "https://www.instagram.com/reel/example/",
                        "https://www.instagram.com/reel/example/",
                        "instagram.com",
                        "instagram",
                        longTitle,
                        longDescription,
                        longDescription,
                        "author",
                        "Instagram",
                        java.util.List.of()
                ));

        String response = mockMvc.perform(post("/api/v1/content-links")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "https://www.instagram.com/reel/example/"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode jsonNode = objectMapper.readTree(response);
        assertThat(jsonNode.path("savedContent").path("title").asText()).hasSizeLessThanOrEqualTo(255);
        assertThat(jsonNode.path("savedContent").path("note").asText()).hasSizeLessThanOrEqualTo(1000);
        assertThat(jsonNode.path("practiceCard").path("actionTitle").asText()).isNotBlank();
    }
}
