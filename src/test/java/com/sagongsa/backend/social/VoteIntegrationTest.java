package com.sagongsa.backend.social;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class VoteIntegrationTest extends PostgreSqlContainerTest {

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
	void goVoteThenCancelDoesNotGoNegative() throws Exception {
		UUID userId = insertUser();
		UUID authorId = insertUser();
		UUID postId = insertPost(authorId);

		// GO 투표
		mockMvc.perform(post("/api/v1/social/posts/{postId}/votes", postId)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"voteType":"GO"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.goCount").value(1))
			.andExpect(jsonPath("$.stopCount").value(0));

		// 같은 GO 투표 → 취소
		mockMvc.perform(post("/api/v1/social/posts/{postId}/votes", postId)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"voteType":"GO"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myVote").doesNotExist())
			.andExpect(jsonPath("$.goCount").value(0))
			.andExpect(jsonPath("$.stopCount").value(0));
	}

	@Test
	void goToStopSwitchUpdatesCountsCorrectly() throws Exception {
		UUID userId = insertUser();
		UUID authorId = insertUser();
		UUID postId = insertPost(authorId);

		// GO 투표
		mockMvc.perform(post("/api/v1/social/posts/{postId}/votes", postId)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"voteType":"GO"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.goCount").value(1));

		// GO → STOP 전환
		mockMvc.perform(post("/api/v1/social/posts/{postId}/votes", postId)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"voteType":"STOP"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myVote").value("STOP"))
			.andExpect(jsonPath("$.goCount").value(0))
			.andExpect(jsonPath("$.stopCount").value(1));
	}

	@Test
	void cancelledVoteCanBeReactivated() throws Exception {
		UUID userId = insertUser();
		UUID authorId = insertUser();
		UUID postId = insertPost(authorId);

		String goBody = """
			{"voteType":"GO"}
			""";

		// GO 투표 → 취소 → 다시 GO 투표
		mockMvc.perform(post("/api/v1/social/posts/{postId}/votes", postId)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(goBody))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/social/posts/{postId}/votes", postId)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(goBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.goCount").value(0));

		mockMvc.perform(post("/api/v1/social/posts/{postId}/votes", postId)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(goBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myVote").value("GO"))
			.andExpect(jsonPath("$.goCount").value(1));
	}

	@Test
	void authorCannotVoteOwnPost() throws Exception {
		UUID authorId = insertUser();
		UUID postId = insertPost(authorId);

		mockMvc.perform(post("/api/v1/social/posts/{postId}/votes", postId)
				.header("X-User-Id", authorId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"voteType":"GO"}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void firstVoteCreatesSingleNotificationForPostAuthor() throws Exception {
		UUID firstVoterId = insertUser();
		UUID secondVoterId = insertUser();
		UUID authorId = insertUser();
		UUID postId = insertPost(authorId);

		mockMvc.perform(post("/api/v1/social/posts/{postId}/votes", postId)
				.header("X-User-Id", firstVoterId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"voteType":"GO"}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/social/posts/{postId}/votes", postId)
				.header("X-User-Id", secondVoterId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"voteType":"STOP"}
					"""))
			.andExpect(status().isOk());

		Integer count = jdbcTemplate.queryForObject(
			"""
			select count(*)
			from notifications
			where user_id = ?
			  and notification_type = 'SOCIAL_FIRST_VOTE'
			  and dedupe_key = ?
			""",
			Integer.class,
			authorId,
			"post:" + postId
		);
		String channelId = jdbcTemplate.queryForObject(
			"select channel_id from notifications where notification_type = 'SOCIAL_FIRST_VOTE'",
			String.class
		);

		org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
		org.assertj.core.api.Assertions.assertThat(channelId).isEqualTo("social_activity");
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

	private UUID insertPost(UUID userId) {
		UUID postId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO feed_posts (id, user_id, title, body, go_count, stop_count, created_at, updated_at) VALUES (?, ?, '테스트 게시글', '내용', 0, 0, ?, ?)",
			postId, userId, now, now);
		return postId;
	}
}
