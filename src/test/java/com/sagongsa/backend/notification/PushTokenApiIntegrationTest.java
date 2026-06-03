package com.sagongsa.backend.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PushTokenApiIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void registersAndroidPushToken() throws Exception {
		UUID userId = insertUser();

		mockMvc.perform(post("/api/v1/push-tokens")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "token": "fcm-token-1",
					  "platform": "ANDROID",
					  "deviceId": "android-device-1"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.platform").value("ANDROID"))
			.andExpect(jsonPath("$.deviceId").value("android-device-1"))
			.andExpect(jsonPath("$.active").value(true));

		assertTokenActive("fcm-token-1", true);
	}

	@Test
	void deactivatesPushToken() throws Exception {
		UUID userId = insertUser();
		insertPushToken(userId, "fcm-token-logout");

		mockMvc.perform(delete("/api/v1/push-tokens")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"token": "fcm-token-logout"}
					"""))
			.andExpect(status().isNoContent());

		assertTokenActive("fcm-token-logout", false);
	}

	private UUID insertUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, created_at, updated_at)
			values (?, 'ACTIVE', 'COMPLETED', ?, ?)
			""",
			userId,
			now,
			now
		);
		return userId;
	}

	private void insertPushToken(UUID userId, String token) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into device_push_tokens (
				id, user_id, platform, push_token, is_active, created_at, updated_at
			)
			values (?, ?, 'ANDROID', ?, true, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			token,
			now,
			now
		);
	}

	private void assertTokenActive(String token, boolean active) {
		Boolean actual = jdbcTemplate.queryForObject(
			"select is_active from device_push_tokens where push_token = ?",
			Boolean.class,
			token
		);
		org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(active);
	}
}
