package com.sagongsa.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "app.auth.trusted-user-id-header.enabled=false")
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class AppReviewerAuthIntegrationTest extends PostgreSqlContainerTest {

	private static final UUID REVIEWER_USER_ID = AppReviewerAccount.USER_ID;
	private static final UUID REVIEWER_SOCIAL_ACCOUNT_ID = UUID.fromString("40400000-0000-0000-0000-000000055001");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void ensureReviewerAccount() {
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, created_at, updated_at, withdrawn_at)
			values (?, 'ACTIVE', 'COMPLETED', now(), now(), null)
			on conflict (id) do update
			set status = 'ACTIVE',
			    onboarding_status = 'COMPLETED',
			    updated_at = now(),
			    withdrawn_at = null
			""",
			REVIEWER_USER_ID
		);
		jdbcTemplate.update(
			"""
			insert into social_accounts (id, user_id, provider, provider_user_id, email, profile_image_url, created_at, updated_at)
			values (?, ?, ?, ?, ?, null, now(), now())
			on conflict on constraint uk_social_accounts_provider_user do update
			set user_id = excluded.user_id,
			    email = excluded.email,
			    profile_image_url = excluded.profile_image_url,
			    updated_at = now()
			""",
			REVIEWER_SOCIAL_ACCOUNT_ID,
			REVIEWER_USER_ID,
			AppReviewerAccount.PROVIDER_DB_VALUE,
			AppReviewerAccount.PROVIDER_USER_ID,
			AppReviewerAccount.EMAIL
		);
		jdbcTemplate.update(
			"""
			insert into user_profiles (user_id, nickname, mascot_name, timezone, profile_image_url, notification_enabled, created_at, updated_at)
			values (?, '심사너굴', '너구리', 'Asia/Seoul', null, true, now(), now())
			on conflict (user_id) do update
			set nickname = excluded.nickname,
			    mascot_name = excluded.mascot_name,
			    timezone = excluded.timezone,
			    profile_image_url = excluded.profile_image_url,
			    notification_enabled = excluded.notification_enabled,
			    updated_at = now()
			""",
			REVIEWER_USER_ID
		);
	}

	@Test
	void issuesReviewerTokenInProdProfileAndAuthenticatesAsReviewer() throws Exception {
		MvcResult tokenResult = mockMvc.perform(post("/api/auth/reviewer-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.accessToken").isString())
			.andExpect(jsonPath("$.refreshToken").isString())
			.andReturn();

		JsonNode tokenJson = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
		String accessToken = tokenJson.get("accessToken").asText();
		String refreshToken = tokenJson.get("refreshToken").asText();

		mockMvc.perform(get("/api/auth/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(true))
			.andExpect(jsonPath("$.userId").value(REVIEWER_USER_ID.toString()))
			.andExpect(jsonPath("$.provider").value(AppReviewerAccount.PROVIDER))
			.andExpect(jsonPath("$.providerUserId").value(AppReviewerAccount.PROVIDER_USER_ID));

		mockMvc.perform(get("/api/v1/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(REVIEWER_USER_ID.toString()))
			.andExpect(jsonPath("$.nickname").value("심사너굴"));

		assertThat(countStoredRefreshTokens(refreshToken)).isEqualTo(1);
	}

	@Test
	void keepsDevUserIdHeaderClosedInProdProfile() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")
			.header("X-User-Id", REVIEWER_USER_ID.toString()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void reviewerSeedMigrationMatchesApplicationConstants() throws Exception {
		try (var stream = getClass().getResourceAsStream("/db/migration/V13__seed_app_reviewer_user.sql")) {
			assertThat(stream).isNotNull();
			String migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

			assertThat(migration)
				.contains(REVIEWER_USER_ID.toString())
				.contains(REVIEWER_SOCIAL_ACCOUNT_ID.toString())
				.contains(AppReviewerAccount.PROVIDER_DB_VALUE)
				.contains(AppReviewerAccount.PROVIDER_USER_ID)
				.contains(AppReviewerAccount.EMAIL);
		}
	}

	private int countStoredRefreshTokens(String refreshToken) {
		String tokenHash = JwtTokenHash.sha256(refreshToken);
		Integer count = jdbcTemplate.queryForObject(
			"select count(*) from refresh_tokens where user_id = ? and token_hash = ? and revoked_at is null",
			Integer.class,
			REVIEWER_USER_ID,
			tokenHash
		);
		return count == null ? 0 : count;
	}
}
