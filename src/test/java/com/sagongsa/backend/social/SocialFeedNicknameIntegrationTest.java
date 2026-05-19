package com.sagongsa.backend.social;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SocialFeedNicknameIntegrationTest extends PostgreSqlContainerTest {

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
	void postResponseContainsAuthorNickname() throws Exception {
		UUID userId = insertUserWithProfile();

		mockMvc.perform(post("/api/v1/social/posts")
				.header("X-User-Id", userId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"테스트 제목","body":"내용"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.authorNickname").value("너굴이"));
	}

	@Test
	void feedListReturnsAuthorNickname() throws Exception {
		UUID userId = insertUserWithProfile();
		insertPost(userId, "게시글 제목", "본문");

		mockMvc.perform(get("/api/v1/social/posts")
				.header("X-User-Id", userId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts[0].authorNickname").value("너굴이"));
	}

	@Test
	void postAuthorShowsUnknownWhenProfileDeleted() throws Exception {
		UUID userId = insertUserWithProfile();
		insertPost(userId, "탈퇴 회원 게시글", "내용");

		// 프로필 삭제 (탈퇴 처리 시뮬레이션)
		jdbcTemplate.update("DELETE FROM user_profiles WHERE user_id = ?", userId);

		mockMvc.perform(get("/api/v1/social/posts")
				.header("X-User-Id", userId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts[0].authorNickname").value("(알 수 없음)"));
	}

	@Test
	void firstCommentNicknameIsNeogul1() throws Exception {
		UUID userId = insertUserWithProfile();
		UUID postId = insertPost(userId, "댓글 테스트 게시글", "본문");

		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments", postId)
				.header("X-User-Id", userId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"body":"첫 댓글"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.authorNickname").value("너굴1"));
	}

	@Test
	void secondUniqueCommenterGetsNeogul2() throws Exception {
		UUID postAuthorId = insertUserWithProfile();
		UUID commenter1Id = insertUserWithProfile();
		UUID commenter2Id = insertUserWithProfile();
		UUID postId = insertPost(postAuthorId, "댓글 순서 테스트", "본문");

		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments", postId)
				.header("X-User-Id", commenter1Id.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"body":"첫 댓글"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.authorNickname").value("너굴1"));

		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments", postId)
				.header("X-User-Id", commenter2Id.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"body":"두 번째 댓글"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.authorNickname").value("너굴2"));

		// 기존 댓글러가 다시 댓글 달아도 번호 동일
		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments", postId)
				.header("X-User-Id", commenter1Id.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"body":"추가 댓글"}
					"""))
			.andExpect(status().isCreated())
				.andExpect(jsonPath("$.authorNickname").value("너굴1"));
	}

	@Test
	void commentNicknameStableAfterFirstCommenterDeletes() throws Exception {
		UUID postAuthorId = insertUserWithProfile();
		UUID commenter1Id = insertUserWithProfile();
		UUID commenter2Id = insertUserWithProfile();
		UUID postId = insertPost(postAuthorId, "닉네임 안정성 테스트", "본문");

		// commenter1이 첫 댓글 → 너굴1
		String c1Body = mockMvc.perform(post("/api/v1/social/posts/{postId}/comments", postId)
				.header("X-User-Id", commenter1Id.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"body":"첫 댓글"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.authorNickname").value("너굴1"))
			.andReturn().getResponse().getContentAsString();

		// commenter2가 두 번째 댓글 → 너굴2
		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments", postId)
				.header("X-User-Id", commenter2Id.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"body":"두 번째 댓글"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.authorNickname").value("너굴2"));

		// commenter1의 댓글 삭제
		UUID comment1Id = UUID.fromString(objectMapper.readTree(c1Body).get("id").asText());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/v1/social/posts/{postId}/comments/{commentId}", postId, comment1Id)
				.header("X-User-Id", commenter1Id.toString()))
			.andExpect(status().isNoContent());

		// 댓글 목록 조회 시 commenter2는 여전히 너굴2 (번호가 당겨지지 않아야 함)
		mockMvc.perform(get("/api/v1/social/posts/{postId}/comments", postId)
				.header("X-User-Id", commenter2Id.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comments[0].authorNickname").value("너굴2"));
	}

	@Test
	void nicknameTestPageFlowUsesDevUsersAndTrustedHeader() throws Exception {
		UUID postAuthorId = createDevUser();
		UUID commenter1Id = createDevUser();
		UUID commenter2Id = createDevUser();

		String postBody = mockMvc.perform(post("/api/v1/social/posts")
				.header("X-User-Id", postAuthorId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"개발 화면 플로우 게시글"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.authorNickname").value("너굴이"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID postId = UUID.fromString(objectMapper.readTree(postBody).get("id").asText());

		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments", postId)
				.header("X-User-Id", commenter1Id.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"body":"첫 댓글"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.authorNickname").value("너굴1"));

		mockMvc.perform(post("/api/v1/social/posts/{postId}/comments", postId)
				.header("X-User-Id", commenter2Id.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"body":"두 번째 댓글"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.authorNickname").value("너굴2"));
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

	private UUID createDevUser() throws Exception {
		String body = mockMvc.perform(post("/api/dev/users/test"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode json = objectMapper.readTree(body);
		return UUID.fromString(json.get("userId").asText());
	}

	private UUID insertPost(UUID userId, String title, String body) {
		UUID postId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO feed_posts (id, user_id, title, body, go_count, stop_count, created_at, updated_at) VALUES (?, ?, ?, ?, 0, 0, ?, ?)",
			postId, userId, title, body, now, now
		);
		return postId;
	}
}
