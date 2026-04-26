package com.sagongsa.backend.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WishlistApiIntegrationTest extends PostgreSqlContainerTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String WISHLIST_ITEMS_PATH = "/api/v1/wishlist/items";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("delete from item_source_metadata");
		jdbcTemplate.update("delete from saved_items");
		jdbcTemplate.update("delete from users");
	}

	@Test
	void createsSavedItemWithSourceMetadata() throws Exception {
		UUID userId = createUser();

		String response = mockMvc.perform(post(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"inputSource": "SHARE",
					"originalUrl": "https://shop.example.com/product?id=100&utm_source=social",
					"normalizedUrl": "https://shop.example.com/product?id=100",
					"title": "Noise Canceling Headphones",
					"imageUrl": "https://cdn.example.com/item.png",
					"listedPrice": 129000,
					"currencyCode": "krw",
					"category": "DIGITAL",
					"categoryConfidence": 92.50,
					"categoryLockedByUser": false,
					"sourceDomain": "shop.example.com",
					"rawTitle": "Headphones / Sale",
					"rawDescription": "Preview description",
					"rawPriceText": "129,000원",
					"rawPayloadJson": "{\\"source\\":\\"preview\\",\\"rank\\":1}"
				}
				"""))
			.andExpect(status().isCreated())
			.andExpect(header().exists(HttpHeaders.LOCATION))
			.andExpect(jsonPath("$.status").value("SAVED"))
			.andExpect(jsonPath("$.inputSource").value("SHARE"))
			.andExpect(jsonPath("$.currencyCode").value("KRW"))
			.andExpect(jsonPath("$.sourceDomain").value("shop.example.com"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(response).contains("\"id\"");
		Integer savedCount = jdbcTemplate.queryForObject(
			"select count(*) from saved_items where user_id = ? and status = 'SAVED'",
			Integer.class,
			userId
		);
		Integer metadataCount = jdbcTemplate.queryForObject(
			"select count(*) from item_source_metadata where source_domain = ? and raw_payload_json ->> 'source' = ?",
			Integer.class,
			"shop.example.com",
			"preview"
		);

		assertThat(savedCount).isEqualTo(1);
		assertThat(metadataCount).isEqualTo(1);
	}

	@Test
	void blocksDuplicateSavedNormalizedUrlForSameUser() throws Exception {
		UUID userId = createUser();
		String request = """
			{
				"inputSource": "DIRECT_INPUT",
				"normalizedUrl": "https://shop.example.com/products/duplicated",
				"title": "Duplicated item",
				"category": "ETC"
			}
			""";

		mockMvc.perform(post(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isCreated());

		mockMvc.perform(post(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_SAVED_ITEM"))
			.andExpect(jsonPath("$.existingItem.title").value("Duplicated item"));
	}

	@Test
	void createsItemByNormalizingOriginalUrlWhenNormalizedUrlIsMissing() throws Exception {
		UUID userId = createUser();

		mockMvc.perform(post(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"inputSource": "SHARE",
					"originalUrl": "https://shop.example.com/product?id=100&utm_source=social",
					"title": "Original only item",
					"category": "ETC"
				}
				"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.normalizedUrl").value("https://shop.example.com/product?id=100"));
	}

	@Test
	void listsOnlySavedItemsNewestFirst() throws Exception {
		UUID userId = createUser();
		UUID olderSavedId = insertItem(userId, "Older saved", "FASHION", "SAVED", OffsetDateTime.now(ZoneOffset.UTC).minusDays(2));
		UUID droppedId = insertItem(userId, "Dropped", "FASHION", "DROPPED", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
		UUID newerSavedId = insertItem(userId, "Newer saved", "DIGITAL", "SAVED", OffsetDateTime.now(ZoneOffset.UTC));

		mockMvc.perform(get(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].id").value(newerSavedId.toString()))
			.andExpect(jsonPath("$[1].id").value(olderSavedId.toString()));

		mockMvc.perform(get(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.queryParam("category", "FASHION"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(olderSavedId.toString()));

		assertThat(droppedId).isNotNull();
	}

	@Test
	void updatesCategoryAndLocksItByUser() throws Exception {
		UUID userId = createUser();
		UUID itemId = insertItem(userId, "Category update target", "ETC", "SAVED", OffsetDateTime.now(ZoneOffset.UTC));

		mockMvc.perform(patch(WISHLIST_ITEMS_PATH + "/{itemId}/category", itemId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"category": "LIVING"
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.category").value("LIVING"))
			.andExpect(jsonPath("$.categoryLockedByUser").value(true));

		Boolean locked = jdbcTemplate.queryForObject(
			"select category_locked_by_user from saved_items where id = ?",
			Boolean.class,
			itemId
		);
		String category = jdbcTemplate.queryForObject("select category from saved_items where id = ?", String.class, itemId);

		assertThat(locked).isTrue();
		assertThat(category).isEqualTo("LIVING");
	}

	@Test
	void deletesByMarkingItemDropped() throws Exception {
		UUID userId = createUser();
		UUID itemId = insertItem(userId, "Delete target", "FOOD", "SAVED", OffsetDateTime.now(ZoneOffset.UTC));

		mockMvc.perform(delete(WISHLIST_ITEMS_PATH + "/{itemId}", itemId)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isNoContent());

		String status = jdbcTemplate.queryForObject("select status from saved_items where id = ?", String.class, itemId);
		assertThat(status).isEqualTo("DROPPED");

		mockMvc.perform(get(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void blocksWishlistBeforeOnboardingCompletion() throws Exception {
		UUID userId = createUser("ACTIVE", "NOT_STARTED");

		mockMvc.perform(get(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	private UUID createUser() {
		return createUser("ACTIVE", "COMPLETED");
	}

	private UUID createUser(String status, String onboardingStatus) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, created_at, updated_at)
			values (?, ?, ?, ?, ?)
			""",
			userId,
			status,
			onboardingStatus,
			now,
			now
		);
		return userId;
	}

	private UUID insertItem(UUID userId, String title, String category, String status, OffsetDateTime createdAt) {
		UUID itemId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into saved_items (
				id, user_id, input_source, title, category, category_locked_by_user,
				status, created_at, updated_at
			)
			values (?, ?, 'DIRECT_INPUT', ?, ?, false, ?, ?, ?)
			""",
			itemId,
			userId,
			title,
			category,
			status,
			createdAt,
			createdAt
		);
		return itemId;
	}
}
