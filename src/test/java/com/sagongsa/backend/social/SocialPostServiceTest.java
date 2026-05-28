package com.sagongsa.backend.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SocialPostServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private SocialPostService socialPostService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── 게시글 생성 ──────────────────────────────────────────────────────────

	@Test
	void 게시글_생성_성공() {
		UUID userId = insertUser();
		CreatePostRequest request = new CreatePostRequest("제목", "내용", null, null, null);

		PostResponse response = socialPostService.createPost(userId, request);

		assertThat(response.id()).isNotNull();
		assertThat(response.title()).isEqualTo("제목");
	}

	@Test
	void 존재하지_않는_유저가_게시글_생성하면_예외() {
		assertThatThrownBy(() ->
			socialPostService.createPost(UUID.randomUUID(), new CreatePostRequest("제목", "내용", null, null, null))
		).isInstanceOf(SocialFeedNotFoundException.class);
	}

	// ── 게시글 조회 ──────────────────────────────────────────────────────────

	@Test
	void 삭제된_게시글_조회하면_예외() {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);
		jdbcTemplate.update("UPDATE feed_posts SET deleted_at = NOW() WHERE id = ?", postId);

		assertThatThrownBy(() -> socialPostService.getPost(userId, postId))
			.isInstanceOf(SocialFeedNotFoundException.class);
	}

	@Test
	void 존재하지_않는_게시글_조회하면_예외() {
		UUID userId = insertUser();

		assertThatThrownBy(() -> socialPostService.getPost(userId, UUID.randomUUID()))
			.isInstanceOf(SocialFeedNotFoundException.class);
	}

	// ── 게시글 수정 ──────────────────────────────────────────────────────────

	@Test
	void 본인_게시글_수정_성공() {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);

		PostResponse response = socialPostService.updatePost(userId, postId, new UpdatePostRequest("수정된 내용"));

		assertThat(response.body()).isEqualTo("수정된 내용");
	}

	@Test
	void 타인_게시글_수정하면_예외() {
		UUID owner = insertUser();
		UUID other = insertUser();
		UUID postId = insertPost(owner);

		assertThatThrownBy(() -> socialPostService.updatePost(other, postId, new UpdatePostRequest("수정")))
			.isInstanceOf(SocialFeedForbiddenException.class);
	}

	// ── 게시글 삭제 ──────────────────────────────────────────────────────────

	@Test
	void 본인_게시글_삭제_성공() {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);

		socialPostService.deletePost(userId, postId);

		assertThatThrownBy(() -> socialPostService.getPost(userId, postId))
			.isInstanceOf(SocialFeedNotFoundException.class);
	}

	@Test
	void 타인_게시글_삭제하면_예외() {
		UUID owner = insertUser();
		UUID other = insertUser();
		UUID postId = insertPost(owner);

		assertThatThrownBy(() -> socialPostService.deletePost(other, postId))
			.isInstanceOf(SocialFeedForbiddenException.class);
	}

	// ── 피드 목록 — 차단 필터링 ─────────────────────────────────────────────

	@Test
	void 차단_유저_게시글은_목록에서_제외() {
		UUID viewer = insertUser();
		UUID blocked = insertUser();
		UUID postId = insertPost(blocked);
		jdbcTemplate.update(
			"INSERT INTO user_blocks (id, blocker_user_id, blocked_user_id, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())",
			UUID.randomUUID(), viewer, blocked);

		PostListResponse response = socialPostService.getPosts(viewer, null, 20);

		boolean contains = response.posts().stream().anyMatch(p -> p.id().equals(postId));
		assertThat(contains).isFalse();
	}

	@Test
	void 비차단_유저_게시글은_목록에_포함() {
		UUID viewer = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);

		PostListResponse response = socialPostService.getPosts(viewer, null, 20);

		boolean contains = response.posts().stream().anyMatch(p -> p.id().equals(postId));
		assertThat(contains).isTrue();
	}

	// ── 커서 페이지네이션 ────────────────────────────────────────────────────

	@Test
	void size보다_게시글이_많으면_hasMore_true() {
		UUID userId = insertUser();
		for (int i = 0; i < 3; i++) {
			insertPost(userId);
		}

		PostListResponse response = socialPostService.getPosts(userId, null, 2);

		assertThat(response.hasMore()).isTrue();
		assertThat(response.posts()).hasSize(2);
	}

	@Test
	void size_이하면_hasMore_false() {
		UUID userId = insertUser();
		insertPost(userId);

		PostListResponse response = socialPostService.getPosts(userId, null, 20);

		assertThat(response.hasMore()).isFalse();
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
}
