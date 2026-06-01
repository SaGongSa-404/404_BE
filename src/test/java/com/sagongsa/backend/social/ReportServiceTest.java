package com.sagongsa.backend.social;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.domain.enums.ReportCategory;
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
class ReportServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private ReportService reportService;

	@Autowired
	private CommentService commentService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── 게시글 신고 ──────────────────────────────────────────────────────────

	@Test
	void 게시글_신고_성공() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);

		assertThatNoException().isThrownBy(() ->
			reportService.reportPost(reporter, postId, ReportCategory.SPAM, "스팸입니다"));
	}

	@Test
	void 같은_게시글_두번_신고하면_예외() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);
		reportService.reportPost(reporter, postId, ReportCategory.SPAM, null);

		assertThatThrownBy(() -> reportService.reportPost(reporter, postId, ReportCategory.SPAM, null))
			.isInstanceOf(SocialFeedForbiddenException.class);
	}

	@Test
	void 존재하지_않는_게시글_신고하면_예외() {
		UUID reporter = insertUser();

		assertThatThrownBy(() -> reportService.reportPost(reporter, UUID.randomUUID(), ReportCategory.SPAM, null))
			.isInstanceOf(SocialFeedNotFoundException.class);
	}

	@Test
	void 삭제된_게시글_신고하면_예외() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);
		jdbcTemplate.update("UPDATE feed_posts SET deleted_at = NOW() WHERE id = ?", postId);

		assertThatThrownBy(() -> reportService.reportPost(reporter, postId, ReportCategory.SPAM, null))
			.isInstanceOf(SocialFeedNotFoundException.class);
	}

	// ── 댓글 신고 ────────────────────────────────────────────────────────────

	@Test
	void 댓글_신고_성공() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);
		CommentResponse comment = commentService.createComment(author, postId, new CreateCommentRequest("댓글"));

		assertThatNoException().isThrownBy(() ->
			reportService.reportComment(reporter, postId, comment.id(), ReportCategory.OBSCENE, "부적절한 내용")
		);
	}

	@Test
	void 같은_댓글_두번_신고하면_예외() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);
		CommentResponse comment = commentService.createComment(author, postId, new CreateCommentRequest("댓글"));
		reportService.reportComment(reporter, postId, comment.id(), ReportCategory.PROFANITY, null);

		assertThatThrownBy(() -> reportService.reportComment(reporter, postId, comment.id(), ReportCategory.PROFANITY, null))
			.isInstanceOf(SocialFeedForbiddenException.class);
	}

	@Test
	void 존재하지_않는_댓글_신고하면_예외() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);

		assertThatThrownBy(() -> reportService.reportComment(reporter, postId, UUID.randomUUID(), ReportCategory.SPAM, null))
			.isInstanceOf(SocialFeedNotFoundException.class);
	}

	@Test
	void 다른_게시글의_댓글_신고하면_예외() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId1 = insertPost(author);
		UUID postId2 = insertPost(author);
		CommentResponse comment = commentService.createComment(author, postId1, new CreateCommentRequest("댓글"));

		// postId2에 속하지 않는 commentId를 넘김
		assertThatThrownBy(() -> reportService.reportComment(reporter, postId2, comment.id(), ReportCategory.SPAM, null))
			.isInstanceOf(SocialFeedNotFoundException.class);
	}

	// ── 유저 신고 ────────────────────────────────────────────────────────────

	@Test
	void 유저_신고_성공() {
		UUID reporter = insertUser();
		UUID target = insertUser();

		assertThatNoException().isThrownBy(() ->
			reportService.reportUser(reporter, target, ReportCategory.PROFANITY, null)
		);
	}

	@Test
	void 자기_자신_신고하면_예외() {
		UUID reporter = insertUser();

		assertThatThrownBy(() -> reportService.reportUser(reporter, reporter, ReportCategory.SPAM, null))
			.isInstanceOf(SocialFeedForbiddenException.class);
	}

	@Test
	void 같은_유저_두번_신고하면_예외() {
		UUID reporter = insertUser();
		UUID target = insertUser();
		reportService.reportUser(reporter, target, ReportCategory.PROFANITY, null);

		assertThatThrownBy(() -> reportService.reportUser(reporter, target, ReportCategory.SPAM, null))
			.isInstanceOf(SocialFeedForbiddenException.class);
	}

	@Test
	void 존재하지_않는_유저_신고하면_예외() {
		UUID reporter = insertUser();

		assertThatThrownBy(() -> reportService.reportUser(reporter, UUID.randomUUID(), ReportCategory.SPAM, null))
			.isInstanceOf(SocialFeedNotFoundException.class);
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
