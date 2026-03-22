package com.fourohfour.backend.modules.content.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourohfour.backend.modules.content.application.ScrapedContent;
import java.io.IOException;
import java.net.http.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlContentScraperTest {

    private final MockWebServer mockWebServer = new MockWebServer();

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void scrapeExtractsOpenGraphAndArticleText() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("""
                        <html>
                          <head>
                            <meta property="og:title" content="티스토리 글 제목" />
                            <meta property="og:description" content="글 요약입니다" />
                            <meta property="og:site_name" content="My Tistory" />
                          </head>
                          <body>
                            <article>
                              오늘 바로 적용할 수 있는 루틴 정리.
                              핵심은 작은 반복을 만드는 것이다.
                            </article>
                          </body>
                        </html>
                        """));

        HtmlContentScraper scraper = new HtmlContentScraper(
                HttpClient.newHttpClient(),
                new ScraperProperties(3000, "test-agent"),
                new ObjectMapper()
        );

        ScrapedContent scrapedContent = scraper.scrape(mockWebServer.url("/entry/test").toString());

        assertThat(scrapedContent.title()).isEqualTo("티스토리 글 제목");
        assertThat(scrapedContent.description()).isEqualTo("글 요약입니다");
        assertThat(scrapedContent.text()).contains("작은 반복");
        assertThat(scrapedContent.siteName()).isEqualTo("My Tistory");
        assertThat(scrapedContent.imageUrls()).isEmpty();
    }

    @Test
    void scrapeFallsBackGracefullyWhenPageFails() {
        HtmlContentScraper scraper = new HtmlContentScraper(
                HttpClient.newHttpClient(),
                new ScraperProperties(300, "test-agent"),
                new ObjectMapper()
        );

        ScrapedContent scrapedContent = scraper.scrape("http://127.0.0.1:9/unreachable");

        assertThat(scrapedContent.requestedUrl()).isEqualTo("http://127.0.0.1:9/unreachable");
        assertThat(scrapedContent.title()).isNull();
        assertThat(scrapedContent.sourceType()).isEqualTo("generic");
        assertThat(scrapedContent.imageUrls()).isEmpty();
    }

    @Test
    void extractsYoutubeTranscriptWhenCaptionTrackExists() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("""
                        <transcript>
                          <text>습관은 의지가 아니라 환경이 만든다.</text>
                          <text>오늘 바꾸고 싶은 습관 하나부터 시작하자.</text>
                        </transcript>
                        """));

        HtmlContentScraper scraper = new HtmlContentScraper(
                HttpClient.newHttpClient(),
                new ScraperProperties(3000, "test-agent"),
                new ObjectMapper()
        );

        String transcript = scraper.extractYoutubeTranscript("""
                <html>
                  <script>
                    var ytInitialPlayerResponse = {
                      "captions":{
                        "playerCaptionsTracklistRenderer":{
                          "captionTracks":[
                            {"baseUrl":"%sapi/timedtext?lang=ko\\u0026fmt=srv3"}
                          ]
                        }
                      }
                    };
                  </script>
                </html>
                """.formatted(mockWebServer.url("/").toString()));

        assertThat(transcript).contains("습관은 의지가 아니라 환경이 만든다");
        assertThat(transcript).contains("오늘 바꾸고 싶은 습관 하나부터 시작하자");
    }

    @Test
    void extractsYoutubeStructuredDescriptionAndKeywords() {
        HtmlContentScraper scraper = new HtmlContentScraper(
                HttpClient.newHttpClient(),
                new ScraperProperties(3000, "test-agent"),
                new ObjectMapper()
        );

        String structuredText = scraper.extractYoutubeStructuredText("""
                <html>
                  <script>
                    var ytInitialPlayerResponse = {
                      "videoDetails": {
                        "shortDescription": "상위 2%가 지키는 습관을 오늘 하나 적용해보자.\\n작은 반복이 인생을 바꾼다.",
                        "keywords": ["동기부여", "습관", "루틴", "정승제"]
                      }
                    };
                  </script>
                </html>
                """);

        assertThat(structuredText).contains("상위 2%가 지키는 습관");
        assertThat(structuredText).contains("작은 반복");
        assertThat(structuredText).contains("습관");
        assertThat(structuredText).contains("루틴");
    }

    @Test
    void scrapeExtractsInstagramCaptionAndAuthor() throws Exception {
        HtmlContentScraper scraper = new HtmlContentScraper(
                HttpClient.newHttpClient(),
                new ScraperProperties(3000, "test-agent"),
                new ObjectMapper()
        );

        String pageHtml = """
                <html>
                  <head>
                    <meta property="og:title" content="fit.jae on Instagram: &quot;오늘은 미루지 말고 10분만 뛰자&quot;" />
                    <meta property="og:description" content="1,240 likes, 12 comments - fit.jae on March 21, 2026: 오늘은 미루지 말고 10분만 뛰자. 작은 시작이 운동 습관을 만든다." />
                    <meta property="og:site_name" content="Instagram" />
                    <script type="application/ld+json">
                      {
                        "@context": "http://schema.org",
                        "@type": "SocialMediaPosting",
                        "author": {
                          "@type": "Person",
                          "alternateName": "fit.jae"
                        },
                        "caption": "오늘은 미루지 말고 10분만 뛰자. 작은 시작이 운동 습관을 만든다."
                      }
                    </script>
                  </head>
                  <body></body>
                </html>
                """;
        Document document = Jsoup.parse(pageHtml, "https://www.instagram.com/reel/example/");
        HtmlContentScraper.InstagramMetadata metadata = scraper.extractInstagramMetadata(document, pageHtml);

        assertThat(metadata.author()).isEqualTo("fit.jae");
        assertThat(metadata.title()).contains("fit.jae");
        assertThat(metadata.description()).contains("10분만 뛰자");
        assertThat(metadata.text()).contains("운동 습관");
        assertThat(metadata.siteName()).isEqualTo("Instagram");
    }
}
