package com.sagongsa.backend.social;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FeedPostProductSectionIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void postWithItemReturnsProductSection() throws Exception {
		String userId = createDevUser();
		String itemId = createTestItem(userId, "에어팟 프로", 350000, "https://shop.example.com/airpods");

		mockMvc.perform(post("/api/v1/social/posts")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"구매 후기","itemId":"%s"}
					""".formatted(itemId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.product.name").value("에어팟 프로"))
			.andExpect(jsonPath("$.product.price").value(350000))
			.andExpect(jsonPath("$.product.link").value("https://shop.example.com/airpods"))
			.andExpect(jsonPath("$.linkAvailable").value(true));
	}

	@Test
	void postWithoutItemHasNullProductAndLinkUnavailable() throws Exception {
		String userId = createDevUser();

		mockMvc.perform(post("/api/v1/social/posts")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"일반 게시글"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.product").doesNotExist())
			.andExpect(jsonPath("$.linkAvailable").value(false));
	}

	@Test
	void postWithItemWithoutLinkHasLinkUnavailable() throws Exception {
		String userId = createDevUser();
		String itemId = createTestItem(userId, "가격만 있는 상품", 10000, null);

		mockMvc.perform(post("/api/v1/social/posts")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"링크 없는 상품 게시글","itemId":"%s"}
					""".formatted(itemId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.product.name").value("가격만 있는 상품"))
			.andExpect(jsonPath("$.product.price").value(10000))
			.andExpect(jsonPath("$.product.link").isEmpty())
			.andExpect(jsonPath("$.linkAvailable").value(false));
	}

	@Test
	void feedListIncludesProductSection() throws Exception {
		String userId = createDevUser();
		String itemId = createTestItem(userId, "피드 상품", 99000, "https://shop.example.com/item");

		mockMvc.perform(post("/api/v1/social/posts")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"피드 조회 테스트","itemId":"%s"}
					""".formatted(itemId)));

		mockMvc.perform(get("/api/v1/social/posts")
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts[0].product.name").value("피드 상품"))
			.andExpect(jsonPath("$.posts[0].product.price").value(99000))
			.andExpect(jsonPath("$.posts[0].linkAvailable").value(true));
	}

	@Test
	void otherUserItemIdIsIgnored() throws Exception {
		String owner = createDevUser();
		String otherUser = createDevUser();
		String itemId = createTestItem(owner, "타인 상품", 50000, "https://example.com");

		// otherUser가 owner의 itemId를 요청 — 무시되어 product=null
		mockMvc.perform(post("/api/v1/social/posts")
				.header("X-User-Id", otherUser)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"타인 상품 게시글","itemId":"%s"}
					""".formatted(itemId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.product").doesNotExist())
			.andExpect(jsonPath("$.linkAvailable").value(false));
	}

	private String createDevUser() throws Exception {
		String body = mockMvc.perform(post("/api/dev/users/test"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("userId").asText();
	}

	private String createTestItem(String userId, String name, Integer price, String link) throws Exception {
		String requestBody = link != null
			? """
			  {"name":"%s","price":%d,"link":"%s"}
			  """.formatted(name, price, link)
			: """
			  {"name":"%s","price":%d}
			  """.formatted(name, price);

		String body = mockMvc.perform(post("/api/dev/items/test")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("itemId").asText();
	}
}
