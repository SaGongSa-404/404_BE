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
class CommentServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private CommentService commentService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── 댓글 생성 ────────────────────────────────────────────────────────────

	@Test
	void 댓글_생성_성공() {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);

		CommentResponse response = commentService.createComment(userId, postId, new CreateCommentRequest("댓글 내용"));

		assertThat(response.id()).isNotNull();
		assertThat(response.body()).isEqualTo("댓글 내용");
	}

	@Test
	void 존재하지_않는_게시글에_댓글_생성하면_예외() {
		UUID userId = insertUser();

		assertThatThrownBy(() ->
			commentService.createComment(userId, UUID.randomUUID(), new CreateCommentRequest("댓글"))
		).isInstanceOf(SocialFeedNotFoundException.class);
	}

	@Test
	void 삭제된_게시글에_댓글_생성하면_예외() {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);
		jdbcTemplate.update("UPDATE feed_posts SET deleted_at = NOW() WHERE id = ?", postId);

		assertThatThrownBy(() ->
			commentService.createComment(userId, postId, new CreateCommentRequest("댓글"))
		).isInstanceOf(SocialFeedNotFoundException.class);
	}

	// ── 댓글 삭제 ────────────────────────────────────────────────────────────

	@Test
	void 본인_댓글_삭제_성공() {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);
		CommentResponse comment = commentService.createComment(userId, postId, new CreateCommentRequest("삭제할 댓글"));

		commentService.deleteComment(userId, postId, comment.id());

		CommentListResponse list = commentService.getComments(userId, postId, 1, 20);
		assertThat(list.comments()).isEmpty();
	}

	@Test
	void 타인_댓글_삭제하면_예외() {
		UUID owner = insertUser();
		UUID other = insertUser();
		UUID postId = insertPost(owner);
		CommentResponse comment = commentService.createComment(owner, postId, new CreateCommentRequest("댓글"));

		assertThatThrownBy(() -> commentService.deleteComment(other, postId, comment.id()))
			.isInstanceOf(SocialFeedForbiddenException.class);
	}

	@Test
	void 존재하지_않는_댓글_삭제하면_예외() {
		UUID userId = insertUser();
		UUID postId = insertPost(userId);

		assertThatThrownBy(() -> commentService.deleteComment(userId, postId, UUID.randomUUID()))
			.isInstanceOf(SocialFeedNotFoundException.class);
	}

	// ── 댓글 목록 — 차단 필터링 ─────────────────────────────────────────────

	@Test
	void 차단_유저_댓글은_목록에서_제외() {
		UUID viewer = insertUser();
		UUID blocked = insertUser();
		UUID postId = insertPost(viewer);
		commentService.createComment(blocked, postId, new CreateCommentRequest("차단 유저 댓글"));
		jdbcTemplate.update(
			"INSERT INTO user_blocks (id, blocker_user_id, blocked_user_id, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())",
			UUID.randomUUID(), viewer, blocked);

		CommentListResponse list = commentService.getComments(viewer, postId, 1, 20);

		assertThat(list.comments()).isEmpty();
		assertThat(list.total()).isZero();
	}

	@Test
	void 차단하지_않은_유저_댓글은_목록에_포함() {
		UUID viewer = insertUser();
		UUID commenter = insertUser();
		UUID postId = insertPost(viewer);
		commentService.createComment(commenter, postId, new CreateCommentRequest("정상 댓글"));

		CommentListResponse list = commentService.getComments(viewer, postId, 1, 20);

		assertThat(list.comments()).hasSize(1);
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
