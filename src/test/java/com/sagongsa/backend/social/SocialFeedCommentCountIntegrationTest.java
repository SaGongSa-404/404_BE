package com.sagongsa.backend.social;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SocialFeedCommentCountIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void feedCommentCountExcludesBlockedCommenters() throws Exception {
		UUID viewerId = insertUserWithProfile();
		UUID authorId = insertUserWithProfile();
		UUID visibleCommenterId = insertUserWithProfile();
		UUID blockedCommenterId = insertUserWithProfile();
		UUID postId = insertPost(authorId, "차단 댓글 집계 테스트");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		insertComment(postId, visibleCommenterId, "보이는 댓글", now.plusSeconds(1));
		insertComment(postId, blockedCommenterId, "차단된 댓글", now.plusSeconds(2));
		insertBlock(viewerId, blockedCommenterId);

		mockMvc.perform(get("/api/v1/social/posts")
				.header("X-User-Id", viewerId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts[0].id").value(postId.toString()))
			.andExpect(jsonPath("$.posts[0].commentCount").value(1));

		mockMvc.perform(get("/api/v1/social/posts/{postId}/comments", postId)
				.header("X-User-Id", viewerId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.comments[0].body").value("보이는 댓글"));
	}

	@Test
	void feedCommentCountIsZeroWhenOnlyBlockedCommentsRemain() throws Exception {
		UUID viewerId = insertUserWithProfile();
		UUID authorId = insertUserWithProfile();
		UUID blockedCommenterId = insertUserWithProfile();
		UUID postId = insertPost(authorId, "차단 댓글만 있는 게시글");
		insertComment(postId, blockedCommenterId, "차단된 댓글", OffsetDateTime.now(ZoneOffset.UTC));
		insertBlock(viewerId, blockedCommenterId);

		mockMvc.perform(get("/api/v1/social/posts")
				.header("X-User-Id", viewerId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts[0].id").value(postId.toString()))
			.andExpect(jsonPath("$.posts[0].commentCount").value(0));
	}

	private UUID insertUserWithProfile() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO users (id, status, onboarding_status, created_at, updated_at) VALUES (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId, now, now
		);
		jdbcTemplate.update(
			"INSERT INTO user_profiles (user_id, nickname, mascot_name, timezone, created_at, updated_at) VALUES (?, '너굴이', '너구리', 'Asia/Seoul', ?, ?)",
			userId, now, now
		);
		return userId;
	}

	private UUID insertPost(UUID userId, String title) {
		UUID postId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO feed_posts (id, user_id, title, body, go_count, stop_count, created_at, updated_at) VALUES (?, ?, ?, '본문', 0, 0, ?, ?)",
			postId, userId, title, now, now
		);
		return postId;
	}

	private void insertComment(UUID postId, UUID userId, String body, OffsetDateTime createdAt) {
		jdbcTemplate.update(
			"INSERT INTO post_comments (id, post_id, user_id, body, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
			UUID.randomUUID(), postId, userId, body, createdAt, createdAt
		);
	}

	private void insertBlock(UUID blockerId, UUID blockedId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO user_blocks (id, blocker_user_id, blocked_user_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
			UUID.randomUUID(), blockerId, blockedId, now, now
		);
	}
}
