package com.sagongsa.backend.social;

import static org.assertj.core.api.Assertions.assertThat;
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
	private SocialPostService socialPostService;

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
	void 불법_정보_공유_신고_성공() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);

		reportService.reportPost(reporter, postId, ReportCategory.ILLEGAL_INFORMATION, null);

		String category = jdbcTemplate.queryForObject(
			"select report_category from post_reports where reporter_user_id = ? and target_id = ?",
			String.class,
			reporter,
			postId
		);
		assertThat(category).isEqualTo("ILLEGAL_INFORMATION");
	}

	@Test
	void 게시글_신고는_피신고자와_게시글_ID를_저장() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);

		reportService.reportPost(reporter, postId, ReportCategory.DEFAMATION, "명예훼손 내용");

		UUID reportedUserId = jdbcTemplate.queryForObject(
			"select reported_user_id from post_reports where reporter_user_id = ? and target_id = ?",
			UUID.class,
			reporter,
			postId
		);
		UUID storedPostId = jdbcTemplate.queryForObject(
			"select post_id from post_reports where reporter_user_id = ? and target_id = ?",
			UUID.class,
			reporter,
			postId
		);
		assertThat(reportedUserId).isEqualTo(author);
		assertThat(storedPostId).isEqualTo(postId);
	}

	@Test
	void 게시글_신고_3회면_블라인드되어_피드에서_제외() {
		UUID viewer = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);

		reportService.reportPost(insertUser(), postId, ReportCategory.SPAM, null);
		reportService.reportPost(insertUser(), postId, ReportCategory.OBSCENE, null);
		reportService.reportPost(insertUser(), postId, ReportCategory.ADVERTISING, null);

		assertThat(queryString("select moderation_status from feed_posts where id = ?", postId))
			.isEqualTo("BLINDED");
		assertThat(socialPostService.getPosts(viewer, null, 20).posts())
			.noneMatch(post -> post.id().equals(postId));
	}

	@Test
	void 게시글_신고_5회면_관리자_검토대기_상태() {
		UUID author = insertUser();
		UUID postId = insertPost(author);

		for (int i = 0; i < 5; i++) {
			reportService.reportPost(insertUser(), postId, ReportCategory.PROFANITY, null);
		}

		assertThat(queryString("select moderation_status from feed_posts where id = ?", postId))
			.isEqualTo("REVIEW_PENDING");
	}

	@Test
	void 게시글_기타_신고는_상세_사유가_필수() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);

		assertThatThrownBy(() -> reportService.reportPost(reporter, postId, ReportCategory.OTHER, " "))
			.isInstanceOf(SocialFeedBadRequestException.class);
	}

	@Test
	void 게시글_기타_신고는_상세_사유가_있으면_성공() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);

		assertThatNoException().isThrownBy(() ->
			reportService.reportPost(reporter, postId, ReportCategory.OTHER, "기타 상세 사유"));
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
	void 댓글_신고는_피신고자와_원_게시글_ID를_저장() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);
		CommentResponse comment = commentService.createComment(author, postId, new CreateCommentRequest("댓글"));

		reportService.reportComment(reporter, postId, comment.id(), ReportCategory.ADVERTISING, "광고 댓글");

		UUID reportedUserId = jdbcTemplate.queryForObject(
			"select reported_user_id from post_reports where reporter_user_id = ? and target_id = ?",
			UUID.class,
			reporter,
			comment.id()
		);
		UUID storedPostId = jdbcTemplate.queryForObject(
			"select post_id from post_reports where reporter_user_id = ? and target_id = ?",
			UUID.class,
			reporter,
			comment.id()
		);
		assertThat(reportedUserId).isEqualTo(author);
		assertThat(storedPostId).isEqualTo(postId);
	}

	@Test
	void 댓글_신고_3회면_블라인드되어_댓글_목록에서_제외() {
		UUID viewer = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);
		CommentResponse comment = commentService.createComment(author, postId, new CreateCommentRequest("댓글"));

		reportService.reportComment(insertUser(), postId, comment.id(), ReportCategory.PROFANITY, null);
		reportService.reportComment(insertUser(), postId, comment.id(), ReportCategory.OBSCENE, null);
		reportService.reportComment(insertUser(), postId, comment.id(), ReportCategory.SPAM, null);

		assertThat(queryString("select moderation_status from post_comments where id = ?", comment.id()))
			.isEqualTo("BLINDED");
		assertThat(commentService.getComments(viewer, postId, 1, 20).comments()).isEmpty();
		assertThat(socialPostService.getPost(viewer, postId).commentCount()).isZero();
	}

	@Test
	void 댓글_기타_신고는_상세_사유가_필수() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);
		CommentResponse comment = commentService.createComment(author, postId, new CreateCommentRequest("댓글"));

		assertThatThrownBy(() -> reportService.reportComment(reporter, postId, comment.id(), ReportCategory.OTHER, null))
			.isInstanceOf(SocialFeedBadRequestException.class);
	}

	@Test
	void 댓글_기타_신고는_상세_사유가_있으면_성공() {
		UUID reporter = insertUser();
		UUID author = insertUser();
		UUID postId = insertPost(author);
		CommentResponse comment = commentService.createComment(author, postId, new CreateCommentRequest("댓글"));

		assertThatNoException().isThrownBy(() ->
			reportService.reportComment(reporter, postId, comment.id(), ReportCategory.OTHER, "기타 상세 사유")
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
	void 유저_기타_신고는_상세_사유가_필수() {
		UUID reporter = insertUser();
		UUID target = insertUser();

		assertThatThrownBy(() -> reportService.reportUser(reporter, target, ReportCategory.OTHER, " "))
			.isInstanceOf(SocialFeedBadRequestException.class);
	}

	@Test
	void 유저_기타_신고는_상세_사유가_있으면_성공() {
		UUID reporter = insertUser();
		UUID target = insertUser();

		assertThatNoException().isThrownBy(() ->
			reportService.reportUser(reporter, target, ReportCategory.OTHER, "기타 상세 사유")
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

	private String queryString(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}
}
