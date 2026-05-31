package com.sagongsa.backend.social;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BlockApiIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── POST /api/v1/users/{targetUserId}/block ───────────────────────────────

	@Test
	void 차단_성공_204() throws Exception {
		UUID blocker = insertUser();
		UUID target = insertUser();

		mockMvc.perform(post("/api/v1/users/{targetUserId}/block", target)
				.header("X-User-Id", blocker))
			.andExpect(status().isNoContent());
	}

	@Test
	void 자기_자신_차단_403() throws Exception {
		UUID userId = insertUser();

		mockMvc.perform(post("/api/v1/users/{targetUserId}/block", userId)
				.header("X-User-Id", userId))
			.andExpect(status().isForbidden());
	}

	@Test
	void 이미_차단한_유저_재차단_403() throws Exception {
		UUID blocker = insertUser();
		UUID target = insertUser();

		mockMvc.perform(post("/api/v1/users/{targetUserId}/block", target)
				.header("X-User-Id", blocker))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/users/{targetUserId}/block", target)
				.header("X-User-Id", blocker))
			.andExpect(status().isForbidden());
	}

	@Test
	void 존재하지_않는_유저_차단_404() throws Exception {
		UUID blocker = insertUser();

		mockMvc.perform(post("/api/v1/users/{targetUserId}/block", UUID.randomUUID())
				.header("X-User-Id", blocker))
			.andExpect(status().isNotFound());
	}

	// ── DELETE /api/v1/users/{targetUserId}/block ─────────────────────────────

	@Test
	void 차단_해제_성공_204() throws Exception {
		UUID blocker = insertUser();
		UUID target = insertUser();
		mockMvc.perform(post("/api/v1/users/{targetUserId}/block", target)
				.header("X-User-Id", blocker))
			.andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/users/{targetUserId}/block", target)
				.header("X-User-Id", blocker))
			.andExpect(status().isNoContent());
	}

	@Test
	void 차단하지_않은_유저_해제_404() throws Exception {
		UUID blocker = insertUser();
		UUID target = insertUser();

		mockMvc.perform(delete("/api/v1/users/{targetUserId}/block", target)
				.header("X-User-Id", blocker))
			.andExpect(status().isNotFound());
	}

	@Test
	void 차단_해제_후_재차단_가능() throws Exception {
		UUID blocker = insertUser();
		UUID target = insertUser();

		mockMvc.perform(post("/api/v1/users/{targetUserId}/block", target)
				.header("X-User-Id", blocker))
			.andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/users/{targetUserId}/block", target)
				.header("X-User-Id", blocker))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/users/{targetUserId}/block", target)
				.header("X-User-Id", blocker))
			.andExpect(status().isNoContent());
	}

	// ── 헬퍼 ─────────────────────────────────────────────────────────────────

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
