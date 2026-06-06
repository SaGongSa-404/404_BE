package com.sagongsa.backend.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DevQaControllerIntegrationTest extends PostgreSqlContainerTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

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
	void basicScenarioCreatesHomeNotificationWishlistAndDeliberationState() throws Exception {
		String body = mockMvc.perform(post("/api/dev/qa/scenarios/basic"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value(startsWith("QA너굴")))
			.andExpect(jsonPath("$.itemIds.length()").value(2))
			.andExpect(jsonPath("$.decisionIds.length()").value(1))
			.andExpect(jsonPath("$.notificationIds.length()").value(1))
			.andExpect(jsonPath("$.paths.deliberation").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();

		UUID userId = UUID.fromString(objectMapper.readTree(body).get("userId").asText());
		UUID deliberationItemId = UUID.fromString(objectMapper.readTree(body).get("deliberationItemId").asText());

		mockMvc.perform(get("/api/v1/home/summary")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget.monthlyBudgetAmount").value(100000))
			.andExpect(jsonPath("$.budget.spentAmount").value(120000))
			.andExpect(jsonPath("$.budget.exhausted").value(true))
			.andExpect(jsonPath("$.notifications.unreadCount").value(1));

		mockMvc.perform(get("/api/v1/notifications")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].type").value("BUDGET_WARNING"))
			.andExpect(jsonPath("$[0].read").value(false));

		mockMvc.perform(get("/api/v1/wishlist/items")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(deliberationItemId.toString()))
			.andExpect(jsonPath("$.items[0].status").value("SAVED"));

		mockMvc.perform(get("/api/v1/deliberations/items/{itemId}", deliberationItemId)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.item.id").value(deliberationItemId.toString()))
			.andExpect(jsonPath("$.budget.monthlyBudgetAmount").value(100000))
			.andExpect(jsonPath("$.budget.spentAmount").value(120000))
			.andExpect(jsonPath("$.similarCategorySpendAmount").value(120000));

		mockMvc.perform(delete("/api/dev/qa/users/{userId}", userId))
			.andExpect(status().isNoContent());

		assertThat(queryInteger("select count(*) from users where id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from saved_items where user_id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from purchase_decisions where user_id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from notifications where user_id = ?", userId)).isZero();
	}

	@Test
	void createsQaUserWithCurrentBudgetAndScenarioPaths() throws Exception {
		String body = mockMvc.perform(post("/api/dev/qa/users"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value(startsWith("QA너굴")))
			.andExpect(jsonPath("$.yearMonth").value(YearMonth.now(SEOUL_ZONE).toString()))
			.andExpect(jsonPath("$.paths.home").value("/api/v1/home/summary"))
			.andExpect(jsonPath("$.paths.mypage").value("/api/v1/users/me"))
			.andExpect(jsonPath("$.paths.cleanup").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();

		UUID userId = UUID.fromString(objectMapper.readTree(body).get("userId").asText());

		assertThat(queryInteger("select count(*) from users where id = ? and status = 'ACTIVE'", userId)).isOne();
		assertThat(queryInteger(
			"select count(*) from social_accounts where user_id = ? and provider_user_id = ?",
			userId,
			"DEV_QA:" + userId
		)).isOne();
		assertThat(queryInteger("select count(*) from user_profiles where user_id = ?", userId)).isOne();
		assertThat(queryInteger("select count(*) from user_notification_settings where user_id = ?", userId)).isOne();
		assertThat(queryInteger("select count(*) from mascot_profiles where user_id = ?", userId)).isOne();
		assertThat(queryInteger(
			"select count(*) from budget_cycles where user_id = ? and year_month = ?",
			userId,
			YearMonth.now(SEOUL_ZONE).toString()
		)).isOne();

		mockMvc.perform(get("/api/v1/home/summary")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userProfile.nickname").value(startsWith("QA너굴")))
			.andExpect(jsonPath("$.budget.yearMonth").value(YearMonth.now(SEOUL_ZONE).toString()));
	}

	@Test
	void deletesOnlyQaScenarioUser() throws Exception {
		String body = mockMvc.perform(post("/api/dev/qa/users"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID userId = UUID.fromString(objectMapper.readTree(body).get("userId").asText());

		mockMvc.perform(delete("/api/dev/qa/users/{userId}", userId))
			.andExpect(status().isNoContent());

		assertThat(queryInteger("select count(*) from users where id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from social_accounts where user_id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from user_profiles where user_id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from budget_cycles where user_id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from user_notification_settings where user_id = ?", userId)).isZero();
		assertThat(queryInteger("select count(*) from mascot_profiles where user_id = ?", userId)).isZero();
	}

	@Test
	void rejectsDeletingNonQaUser() throws Exception {
		UUID userId = insertNonQaUserWithQaNickname();

		mockMvc.perform(delete("/api/dev/qa/users/{userId}", userId))
			.andExpect(status().isBadRequest());

		assertThat(queryInteger("select count(*) from users where id = ?", userId)).isOne();
		assertThat(queryInteger("select count(*) from user_profiles where user_id = ?", userId)).isOne();
	}

	private UUID insertNonQaUserWithQaNickname() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId,
			now,
			now
		);
		jdbcTemplate.update(
			"""
			insert into user_profiles (
				user_id, nickname, mascot_name, timezone, profile_image_url, notification_enabled, created_at, updated_at
			)
			values (?, 'QA너굴99999', '너구리', 'Asia/Seoul', null, true, ?, ?)
			""",
			userId,
			now,
			now
		);
		return userId;
	}

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}
}
