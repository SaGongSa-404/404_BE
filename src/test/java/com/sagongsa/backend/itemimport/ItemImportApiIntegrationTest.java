package com.sagongsa.backend.itemimport;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sagongsa.backend.itemimport.item.FetchedPage;
import com.sagongsa.backend.itemimport.item.PageFetcher;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.notification.reminder-worker.enabled=false")
@AutoConfigureMockMvc
class ItemImportApiIntegrationTest extends PostgreSqlContainerTest {

	private static final String IMPORT_LINK_PATH = "/api/v1/items/import-link";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FakePageFetcher pageFetcher;

	@BeforeEach
	void setUp() {
		pageFetcher.reset();
	}

	@Test
	void importsSharedLinkThroughApiAndReturnsWishlistSaveDraft() throws Exception {
		pageFetcher.stub(
			"https://shop.example.com/products/100",
			"""
			<html>
			<head>
			  <meta property="og:title" content="Noise Canceling Headphones" />
			  <meta property="og:description" content="가벼운 무선 헤드폰" />
			  <meta property="og:image" content="https://cdn.example.com/headphones.jpg" />
			  <meta property="product:price:amount" content="129000" />
			</head>
			<body></body>
			</html>
			"""
		);

		mockMvc.perform(post(IMPORT_LINK_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"inputSource": "SHARE",
					"url": "https://shop.example.com/products/100?utm_source=kakao"
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.retrievalStatus").value("SUCCESS"))
			.andExpect(jsonPath("$.item.inputSource").value("SHARE"))
			.andExpect(jsonPath("$.item.originalUrl").value("https://shop.example.com/products/100?utm_source=kakao"))
			.andExpect(jsonPath("$.item.normalizedUrl").value("https://shop.example.com/products/100"))
			.andExpect(jsonPath("$.item.title").value("Noise Canceling Headphones"))
			.andExpect(jsonPath("$.item.listedPrice").value(129000))
			.andExpect(jsonPath("$.item.currencyCode").value("KRW"))
			.andExpect(jsonPath("$.item.category").value("DIGITAL"))
			.andExpect(jsonPath("$.sourceMetadata.sourceDomain").value("shop.example.com"))
			.andExpect(jsonPath("$.sourceMetadata.extractionMethod").value("HTML_META"))
			.andExpect(jsonPath("$.saveRequest.normalizedUrl").value("https://shop.example.com/products/100"))
			.andExpect(jsonPath("$.saveRequest.sourceDomain").value("shop.example.com"))
			.andExpect(jsonPath("$.warnings", hasItem("추적성 query parameter를 제거했습니다.")));
	}

	@Test
	void importsDirectInputWithoutUrlThroughApi() throws Exception {
		mockMvc.perform(post(IMPORT_LINK_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"inputSource": "DIRECT_INPUT",
					"title": "수동 입력 머그컵",
					"brandName": "Daily Cup",
					"price": 18000,
					"imageUrl": "https://cdn.example.com/cup.jpg"
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.retrievalStatus").value("SUCCESS"))
			.andExpect(jsonPath("$.item.inputSource").value("DIRECT_INPUT"))
			.andExpect(jsonPath("$.item.originalUrl").value(nullValue()))
			.andExpect(jsonPath("$.item.normalizedUrl").value(nullValue()))
			.andExpect(jsonPath("$.item.title").value("수동 입력 머그컵"))
			.andExpect(jsonPath("$.item.brandName").value("Daily Cup"))
			.andExpect(jsonPath("$.item.listedPrice").value(18000))
			.andExpect(jsonPath("$.item.currencyCode").value("KRW"))
			.andExpect(jsonPath("$.item.category").value("LIVING"))
			.andExpect(jsonPath("$.sourceMetadata.extractionMethod").value("MANUAL"))
			.andExpect(jsonPath("$.saveRequest.originalUrl").value(nullValue()))
			.andExpect(jsonPath("$.saveRequest.normalizedUrl").value(nullValue()));
	}

	@Test
	void rejectsShareUrlWithUserInfoThroughApi() throws Exception {
		mockMvc.perform(post(IMPORT_LINK_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"inputSource": "SHARE",
					"url": "https://user:password@shop.example.com/products/100"
				}
				"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsFailedShoppingPageThroughApi() throws Exception {
		pageFetcher.stub("https://shop.example.com/products/missing", 404, "<html></html>");

		mockMvc.perform(post(IMPORT_LINK_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"inputSource": "SHARE",
					"url": "https://shop.example.com/products/missing"
				}
				"""))
			.andExpect(status().isBadGateway());
	}

	@TestConfiguration
	static class TestPageFetcherConfig {

		@Bean
		@Primary
		FakePageFetcher fakePageFetcher() {
			return new FakePageFetcher();
		}
	}

	static final class FakePageFetcher implements PageFetcher {

		private final Map<String, FetchedPage> pages = new HashMap<>();

		void reset() {
			pages.clear();
		}

		void stub(String url, String body) {
			stub(url, 200, body);
		}

		void stub(String url, int statusCode, String body) {
			URI uri = URI.create(url);
			pages.put(url, new FetchedPage(uri, uri, statusCode, "text/html", body));
		}

		@Override
		public FetchedPage fetch(URI uri) {
			FetchedPage page = pages.get(uri.toString());
			if (page == null) {
				throw new AssertionError("No stubbed page for " + uri);
			}
			return page;
		}
	}
}
