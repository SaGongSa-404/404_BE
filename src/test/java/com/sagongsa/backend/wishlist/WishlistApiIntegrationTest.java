package com.sagongsa.backend.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
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
	void rejectsZeroListedPriceOnCreate() throws Exception {
		UUID userId = createUser();

		mockMvc.perform(post(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"inputSource": "SHARE",
					"originalUrl": "https://shop.example.com/product?id=100",
					"normalizedUrl": "https://shop.example.com/product?id=100",
					"title": "Zero price item",
					"listedPrice": 0,
					"category": "DIGITAL"
				}
				"""))
			.andExpect(status().isBadRequest());
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
	void serializesDuplicateSavedItemResponseWhenExistingItemIsNull() throws Exception {
		DuplicateSavedItemResponse response = new DuplicateSavedItemResponse(
			"DUPLICATE_SAVED_ITEM",
			"Saved wishlist item already exists for the normalized URL.",
			null
		);

		JsonNode body = objectMapper.readTree(objectMapper.writeValueAsString(response));

		assertThat(body.get("code").asText()).isEqualTo("DUPLICATE_SAVED_ITEM");
		assertThat(body.get("message").asText()).isEqualTo("Saved wishlist item already exists for the normalized URL.");
		assertThat(body.has("existingItem")).isTrue();
		assertThat(body.get("existingItem").isNull()).isTrue();
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
	void createsDirectInputItemWithoutUrl() throws Exception {
		UUID userId = createUser();

		mockMvc.perform(post(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"inputSource": "DIRECT_INPUT",
					"title": "Manual no url item",
					"category": "ETC"
				}
				"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.originalUrl").value(nullValue()))
			.andExpect(jsonPath("$.normalizedUrl").value(nullValue()))
			.andExpect(jsonPath("$.title").value("Manual no url item"));
	}

	@Test
	void rejectsNullBodyAsBadRequest() throws Exception {
		UUID userId = createUser();

		mockMvc.perform(post(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("null"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void rejectsUrlWithUserInfo() throws Exception {
		UUID userId = createUser();

		mockMvc.perform(post(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"inputSource": "SHARE",
					"originalUrl": "https://user:password@shop.example.com/product",
					"title": "Credential url",
					"category": "ETC"
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
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
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].id").value(newerSavedId.toString()))
			.andExpect(jsonPath("$.items[1].id").value(olderSavedId.toString()))
			.andExpect(jsonPath("$.hasMore").value(false))
			.andExpect(jsonPath("$.nextCursor").value(nullValue()));

		mockMvc.perform(get(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.queryParam("category", "FASHION"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].id").value(olderSavedId.toString()))
			.andExpect(jsonPath("$.hasMore").value(false))
			.andExpect(jsonPath("$.nextCursor").value(nullValue()));

		assertThat(droppedId).isNotNull();
	}

	@Test
	void listsWithLimitCursorAndWithoutRawMetadata() throws Exception {
		UUID userId = createUser();
		OffsetDateTime oldest = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3);
		OffsetDateTime middle = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2);
		OffsetDateTime newest = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
		insertItem(userId, "Oldest", "FASHION", "SAVED", oldest);
		UUID middleId = insertItem(userId, "Middle", "FASHION", "SAVED", middle);
		UUID newestId = insertItem(userId, "Newest", "FASHION", "SAVED", newest);

		String firstPage = mockMvc.perform(get(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.queryParam("limit", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].id").value(newestId.toString()))
			.andExpect(jsonPath("$.items[0].rawPayloadJson").doesNotExist())
			.andExpect(jsonPath("$.items[0].sourceDomain").doesNotExist())
			.andExpect(jsonPath("$.hasMore").value(true))
			.andExpect(jsonPath("$.nextCursor").isNotEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String nextCursor = objectMapper.readTree(firstPage).path("nextCursor").asText();

		mockMvc.perform(get(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.queryParam("limit", "1")
				.queryParam("cursor", nextCursor))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].id").value(middleId.toString()))
			.andExpect(jsonPath("$.hasMore").value(true))
			.andExpect(jsonPath("$.nextCursor").isNotEmpty());
	}

	@Test
	void treatsBlankCursorAsFirstPage() throws Exception {
		UUID userId = createUser();
		UUID itemId = insertItem(userId, "Blank cursor target", "FASHION", "SAVED", OffsetDateTime.now(ZoneOffset.UTC));

		mockMvc.perform(get(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.queryParam("cursor", "   "))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].id").value(itemId.toString()))
			.andExpect(jsonPath("$.hasMore").value(false))
			.andExpect(jsonPath("$.nextCursor").value(nullValue()));
	}

	@Test
	void rejectsMalformedCursorQueryParameter() throws Exception {
		UUID userId = createUser();

		mockMvc.perform(get(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.queryParam("cursor", "not-a-cursor"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void listsWithStableCursorWhenCreatedAtTies() throws Exception {
		UUID userId = createUser();
		OffsetDateTime sameCreatedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
		insertItem(userId, "First same time", "FASHION", "SAVED", sameCreatedAt);
		insertItem(userId, "Second same time", "FASHION", "SAVED", sameCreatedAt);

		String firstPage = mockMvc.perform(get(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.queryParam("limit", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.hasMore").value(true))
			.andExpect(jsonPath("$.nextCursor").isNotEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode firstJson = objectMapper.readTree(firstPage);
		String firstId = firstJson.path("items").get(0).path("id").asText();
		String nextCursor = firstJson.path("nextCursor").asText();

		String secondPage = mockMvc.perform(get(WISHLIST_ITEMS_PATH)
				.header(USER_ID_HEADER, userId)
				.queryParam("limit", "1")
				.queryParam("cursor", nextCursor))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.hasMore").value(false))
			.andExpect(jsonPath("$.nextCursor").value(nullValue()))
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode secondJson = objectMapper.readTree(secondPage);
		String secondId = secondJson.path("items").get(0).path("id").asText();
		assertThat(secondId).isNotEqualTo(firstId);
	}

	@Test
	void getsSavedItemDetail() throws Exception {
		UUID userId = createUser();
		UUID itemId = insertItem(userId, "Detail target", "BEAUTY", "SAVED", OffsetDateTime.now(ZoneOffset.UTC));

		mockMvc.perform(get(WISHLIST_ITEMS_PATH + "/{itemId}", itemId)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(itemId.toString()))
			.andExpect(jsonPath("$.title").value("Detail target"))
			.andExpect(jsonPath("$.category").value("BEAUTY"))
			.andExpect(jsonPath("$.status").value("SAVED"));
	}

	@Test
	void hidesDroppedItemDetail() throws Exception {
		UUID userId = createUser();
		UUID itemId = insertItem(userId, "Dropped detail", "BEAUTY", "DROPPED", OffsetDateTime.now(ZoneOffset.UTC));

		mockMvc.perform(get(WISHLIST_ITEMS_PATH + "/{itemId}", itemId)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
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
	void updatesDirectInputItemFieldsIncludingUrl() throws Exception {
		UUID userId = createUser();
		UUID itemId = insertItem(userId, "Manual update target", "ETC", "SAVED", OffsetDateTime.now(ZoneOffset.UTC));

		mockMvc.perform(patch(WISHLIST_ITEMS_PATH + "/{itemId}", itemId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"originalUrl": "https://shop.example.com/product?id=100&utm_source=social",
					"title": "Updated manual item",
					"listedPrice": 99000,
					"category": "DIGITAL"
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inputSource").value("DIRECT_INPUT"))
			.andExpect(jsonPath("$.originalUrl").value("https://shop.example.com/product?id=100&utm_source=social"))
			.andExpect(jsonPath("$.normalizedUrl").value("https://shop.example.com/product?id=100"))
			.andExpect(jsonPath("$.title").value("Updated manual item"))
			.andExpect(jsonPath("$.listedPrice").value(99000))
			.andExpect(jsonPath("$.category").value("DIGITAL"))
			.andExpect(jsonPath("$.categoryLockedByUser").value(true));
	}

	@Test
	void updatesShareItemFieldsWithoutChangingUrl() throws Exception {
		UUID userId = createUser();
		UUID itemId = insertShareItem(
			userId,
			"Share update target",
			"https://shop.example.com/product?id=100",
			"DIGITAL",
			"SAVED",
			OffsetDateTime.now(ZoneOffset.UTC)
		);

		mockMvc.perform(patch(WISHLIST_ITEMS_PATH + "/{itemId}", itemId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"title": "Updated share item",
					"listedPrice": 88000,
					"category": "LIVING"
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inputSource").value("SHARE"))
			.andExpect(jsonPath("$.originalUrl").value("https://shop.example.com/product?id=100"))
			.andExpect(jsonPath("$.normalizedUrl").value("https://shop.example.com/product?id=100"))
			.andExpect(jsonPath("$.title").value("Updated share item"))
			.andExpect(jsonPath("$.listedPrice").value(88000))
			.andExpect(jsonPath("$.category").value("LIVING"))
			.andExpect(jsonPath("$.categoryLockedByUser").value(true));
	}

	@Test
	void rejectsShareItemUrlUpdate() throws Exception {
		UUID userId = createUser();
		UUID itemId = insertShareItem(
			userId,
			"Share update target",
			"https://shop.example.com/product?id=100",
			"DIGITAL",
			"SAVED",
			OffsetDateTime.now(ZoneOffset.UTC)
		);

		mockMvc.perform(patch(WISHLIST_ITEMS_PATH + "/{itemId}", itemId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"originalUrl": "https://shop.example.com/changed",
					"title": "Updated share item",
					"listedPrice": 88000,
					"category": "LIVING"
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void rejectsNullBodyForItemUpdate() throws Exception {
		UUID userId = createUser();
		UUID itemId = insertItem(userId, "Manual update target", "ETC", "SAVED", OffsetDateTime.now(ZoneOffset.UTC));

		mockMvc.perform(patch(WISHLIST_ITEMS_PATH + "/{itemId}", itemId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("null"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void rejectsNullBodyForCategoryUpdate() throws Exception {
		UUID userId = createUser();
		UUID itemId = insertItem(userId, "Category update target", "ETC", "SAVED", OffsetDateTime.now(ZoneOffset.UTC));

		mockMvc.perform(patch(WISHLIST_ITEMS_PATH + "/{itemId}/category", itemId)
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("null"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
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
			.andExpect(jsonPath("$.items", hasSize(0)))
			.andExpect(jsonPath("$.hasMore").value(false))
			.andExpect(jsonPath("$.nextCursor").value(nullValue()));
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

	private UUID insertShareItem(UUID userId, String title, String url, String category, String status, OffsetDateTime createdAt) {
		UUID itemId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into saved_items (
				id, user_id, input_source, original_url, normalized_url, title, category,
				category_locked_by_user, status, created_at, updated_at
			)
			values (?, ?, 'SHARE', ?, ?, ?, ?, false, ?, ?, ?)
			""",
			itemId,
			userId,
			url,
			url,
			title,
			category,
			status,
			createdAt,
			createdAt
		);
		return itemId;
	}
}
