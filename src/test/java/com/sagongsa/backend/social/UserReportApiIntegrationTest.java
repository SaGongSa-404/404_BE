package com.sagongsa.backend.social;

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
class UserReportApiIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── POST /api/v1/users/{targetUserId}/reports ─────────────────────────────

	@Test
	void 유저_신고_성공_204() throws Exception {
		UUID reporter = insertUser();
		UUID target = insertUser();

		mockMvc.perform(post("/api/v1/users/{targetUserId}/reports", target)
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"SPAM"}
					"""))
			.andExpect(status().isNoContent());
	}

	@Test
	void 자기_자신_신고_403() throws Exception {
		UUID userId = insertUser();

		mockMvc.perform(post("/api/v1/users/{targetUserId}/reports", userId)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"SPAM"}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void 같은_유저_두번_신고_403() throws Exception {
		UUID reporter = insertUser();
		UUID target = insertUser();

		mockMvc.perform(post("/api/v1/users/{targetUserId}/reports", target)
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"PROFANITY"}
					"""))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/users/{targetUserId}/reports", target)
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"SPAM"}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void 존재하지_않는_유저_신고_404() throws Exception {
		UUID reporter = insertUser();

		mockMvc.perform(post("/api/v1/users/{targetUserId}/reports", UUID.randomUUID())
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"SPAM"}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void category_누락_400() throws Exception {
		UUID reporter = insertUser();
		UUID target = insertUser();

		mockMvc.perform(post("/api/v1/users/{targetUserId}/reports", target)
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{}
					"""))
			.andExpect(status().isBadRequest());
	}

	private UUID insertUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO users (id, status, onboarding_status, created_at, updated_at) VALUES (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId, now, now);
		jdbcTemplate.update(
			"INSERT INTO user_profiles (user_id, nickname, mascot_name, timezone, created_at, updated_at) VALUES (?, '너굴이', '너구리', 'Asia/Seoul', ?, ?)",
			userId, now, now);
		return userId;
	}
}
