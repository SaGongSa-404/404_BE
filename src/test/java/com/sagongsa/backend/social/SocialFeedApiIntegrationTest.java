package com.sagongsa.backend.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class SocialFeedApiIntegrationTest extends PostgreSqlContainerTest {

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

	// ── POST /api/v1/social/posts ────────────────────────────────────────────

	@Test
	void 게시글_생성_201_Location_헤더_포함() throws Exception {
		UUID userId = insertUser();

		mockMvc.perform(post("/api/v1/social/posts")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"제목","body":"내용"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/social/posts/")))
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.title").value("제목"));
	}

	@Test
	void 게시글_생성_본문_500자_초과_400() throws Exception {
		UUID userId = insertUser();
		String longBody = "a".repeat(501);

		mockMvc.perform(post("/api/v1/social/posts")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"제목","body":"%s"}
					""".formatted(longBody)))
			.andExpect(status().isBadRequest());
	}

	// ── GET /api/v1/social/posts ─────────────────────────────────────────────

	@Test
	void 피드_목록_조회_200_hasMore_cursor_포함() throws Exception {
		UUID userId = insertUser();
		for (int i = 0; i < 3; i++) {
			insertPost(userId);
		}

		mockMvc.perform(get("/api/v1/social/posts")
				.header("X-User-Id", userId)
				.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts").isArray())
			.andExpect(jsonPath("$.hasMore").value(true))
			.andExpect(jsonPath("$.nextCursor").isNotEmpty());
	}

	@Test
	void 피드_목록_게시글_없으면_빈_배열_hasMore_false() throws Exception {
		UUID userId = insertUser();

		mockMvc.perform(get("/api/v1/social/posts")
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts").isEmpty())
			.andExpect(jsonPath("$.hasMore").value(false));
	}

	@Test
	void 정지된_유저는_피드_API_접근_403() throws Exception {
		UUID userId = insertUser();
		jdbcTemplate.update(
			"UPDATE users SET status = 'SUSPENDED', suspended_until = ?, updated_at = ? WHERE id = ?",
			OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
			OffsetDateTime.now(ZoneOffset.UTC),
			userId
		);

		mockMvc.perform(get("/api/v1/social/posts")
				.header("X-User-Id", userId))
			.andExpect(status().isForbidden());
	}

	@Test
	void 정지기간_만료_유저는_피드_API_접근_가능하고_ACTIVE로_복구() throws Exception {
		UUID userId = insertUser();
		jdbcTemplate.update(
			"UPDATE users SET status = 'SUSPENDED', suspended_until = ?, updated_at = ? WHERE id = ?",
			OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
			OffsetDateTime.now(ZoneOffset.UTC),
			userId
		);

		mockMvc.perform(get("/api/v1/social/posts")
				.header("X-User-Id", userId))
			.andExpect(status().isOk());

		assertThat(queryString("select status from users where id = ?", userId)).isEqualTo("ACTIVE");
	}

	// ── GET /api/v1/social/posts/{postId} ────────────────────────────────────

	@Test
	void 게시글_단건_조회_200() throws Exception {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);

		mockMvc.perform(get("/api/v1/social/posts/{postId}", postId)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(postId.toString()));
	}

	@Test
	void 삭제된_게시글_조회_404() throws Exception {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);
		jdbcTemplate.update("UPDATE feed_posts SET deleted_at = NOW() WHERE id = ?", postId);

		mockMvc.perform(get("/api/v1/social/posts/{postId}", postId)
				.header("X-User-Id", userId))
			.andExpect(status().isNotFound());
	}

	@Test
	void 존재하지_않는_게시글_조회_404() throws Exception {
		UUID userId = insertUser();

		mockMvc.perform(get("/api/v1/social/posts/{postId}", UUID.randomUUID())
				.header("X-User-Id", userId))
			.andExpect(status().isNotFound());
	}

	// ── PATCH /api/v1/social/posts/{postId} ──────────────────────────────────

	@Test
	void 본인_게시글_수정_200() throws Exception {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);

		mockMvc.perform(patch("/api/v1/social/posts/{postId}", postId)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"body":"수정된 내용"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body").value("수정된 내용"));
	}

	@Test
	void 타인_게시글_수정_403() throws Exception {
		UUID owner = insertUser();
		UUID other = insertUser();
		UUID postId = insertPost(owner);

		mockMvc.perform(patch("/api/v1/social/posts/{postId}", postId)
				.header("X-User-Id", other)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"body":"수정 시도"}
					"""))
			.andExpect(status().isForbidden());
	}

	// ── DELETE /api/v1/social/posts/{postId} ─────────────────────────────────

	@Test
	void 본인_게시글_삭제_204() throws Exception {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);

		mockMvc.perform(delete("/api/v1/social/posts/{postId}", postId)
				.header("X-User-Id", userId))
			.andExpect(status().isNoContent());
	}

	@Test
	void 타인_게시글_삭제_403() throws Exception {
		UUID owner = insertUser();
		UUID other = insertUser();
		UUID postId = insertPost(owner);

		mockMvc.perform(delete("/api/v1/social/posts/{postId}", postId)
				.header("X-User-Id", other))
			.andExpect(status().isForbidden());
	}

	// ── POST /api/v1/social/posts/{postId}/comments ──────────────────────────

	@Test
	void 댓글_생성_201_Location_헤더_포함() throws Exception {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);

		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments", postId)
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"body":"댓글 내용"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location",
				org.hamcrest.Matchers.containsString("/api/v1/social/posts/" + postId + "/comments/")))
			.andExpect(jsonPath("$.body").value("댓글 내용"));
	}

	@Test
	void 존재하지_않는_게시글에_댓글_생성_404() throws Exception {
		UUID userId = insertUser();

		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments", UUID.randomUUID())
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"body":"댓글"}
					"""))
			.andExpect(status().isNotFound());
	}

	// ── GET /api/v1/social/posts/{postId}/comments ───────────────────────────

	@Test
	void 댓글_목록_조회_200() throws Exception {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);
		insertComment(userId, postId);

		mockMvc.perform(get("/api/v1/social/posts/{postId}/comments", postId)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comments").isArray())
			.andExpect(jsonPath("$.comments[0].body").value("테스트 댓글"));
	}

	// ── DELETE /api/v1/social/posts/{postId}/comments/{commentId} ────────────

	@Test
	void 본인_댓글_삭제_204() throws Exception {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);
		UUID commentId = insertComment(userId, postId);

		mockMvc.perform(delete("/api/v1/social/posts/{postId}/comments/{commentId}", postId, commentId)
				.header("X-User-Id", userId))
			.andExpect(status().isNoContent());
	}

	@Test
	void 타인_댓글_삭제_403() throws Exception {
		UUID owner = insertUser();
		UUID other = insertUser();
		UUID postId = insertPost(owner);
		UUID commentId = insertComment(owner, postId);

		mockMvc.perform(delete("/api/v1/social/posts/{postId}/comments/{commentId}", postId, commentId)
				.header("X-User-Id", other))
			.andExpect(status().isForbidden());
	}

	// ── POST /api/v1/social/posts/{postId}/reports ───────────────────────────

	@Test
	void 게시글_신고_204() throws Exception {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);

		mockMvc.perform(post("/api/v1/social/posts/{postId}/reports", postId)
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"SPAM","reason":"스팸입니다"}
					"""))
			.andExpect(status().isNoContent());
	}

	@Test
	void 게시글_기타_신고_상세사유_없으면_400() throws Exception {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);

		mockMvc.perform(post("/api/v1/social/posts/{postId}/reports", postId)
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"OTHER","reason":" "}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void 같은_게시글_두번_신고_403() throws Exception {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);
		String body = """
			{"category":"SPAM"}
			""";

		mockMvc.perform(post("/api/v1/social/posts/{postId}/reports", postId)
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/social/posts/{postId}/reports", postId)
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());
	}

	@Test
	void 존재하지_않는_게시글_신고_404() throws Exception {
		UUID reporter = insertUser();

		mockMvc.perform(post("/api/v1/social/posts/{postId}/reports", UUID.randomUUID())
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"SPAM"}
					"""))
			.andExpect(status().isNotFound());
	}

	// ── POST /api/v1/social/posts/{postId}/comments/{commentId}/reports ───────

	@Test
	void 댓글_신고_204() throws Exception {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);
		UUID commentId = insertComment(author, postId);

		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments/{commentId}/reports", postId, commentId)
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"OBSCENE","reason":"부적절한 내용"}
					"""))
			.andExpect(status().isNoContent());
	}

	@Test
	void 댓글_기타_신고_상세사유_없으면_400() throws Exception {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);
		UUID commentId = insertComment(author, postId);

		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments/{commentId}/reports", postId, commentId)
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"OTHER"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void 같은_댓글_두번_신고_403() throws Exception {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);
		UUID commentId = insertComment(author, postId);
		String body = """
			{"category":"PROFANITY"}
			""";

		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments/{commentId}/reports", postId, commentId)
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments/{commentId}/reports", postId, commentId)
				.header("X-User-Id", reporter)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());
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

	private UUID insertPost(UUID userId) {
		UUID postId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO feed_posts (id, user_id, title, body, go_count, stop_count, created_at, updated_at) VALUES (?, ?, '테스트 게시글', '내용', 0, 0, ?, ?)",
			postId, userId, now, now);
		return postId;
	}

	private UUID insertComment(UUID userId, UUID postId) {
		UUID commentId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO post_comments (id, post_id, user_id, body, created_at, updated_at) VALUES (?, ?, ?, '테스트 댓글', ?, ?)",
			commentId, postId, userId, now, now);
		return commentId;
	}

	private String queryString(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}
}
