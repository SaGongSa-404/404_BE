package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.ReportCategory;
import com.sagongsa.backend.domain.enums.ReportTargetType;
import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.FeedPostRepository;
import com.sagongsa.backend.domain.social.PostComment;
import com.sagongsa.backend.domain.social.PostCommentRepository;
import com.sagongsa.backend.domain.social.PostReport;
import com.sagongsa.backend.domain.social.PostReportRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ReportService {

	private final PostReportRepository postReportRepository;
	private final FeedPostRepository feedPostRepository;
	private final PostCommentRepository postCommentRepository;
	private final UserAccountRepository userAccountRepository;

	ReportService(PostReportRepository postReportRepository,
		FeedPostRepository feedPostRepository,
		PostCommentRepository postCommentRepository,
		UserAccountRepository userAccountRepository) {
		this.postReportRepository = postReportRepository;
		this.feedPostRepository = feedPostRepository;
		this.postCommentRepository = postCommentRepository;
		this.userAccountRepository = userAccountRepository;
	}

	void reportPost(UUID reporterId, UUID postId, ReportCategory category, String reason) {
		validateReason(category, reason);
		FeedPost post = findReportablePostOrThrow(postId);
		if (post.getUser().getId().equals(reporterId)) {
			throw new SocialFeedForbiddenException("본인의 게시글은 신고할 수 없습니다.");
		}
		if (postReportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, ReportTargetType.POST, postId)) {
			throw new SocialFeedForbiddenException("이미 신고한 게시글입니다.");
		}
		UserAccount reporter = findUserOrThrow(reporterId);
		postReportRepository.save(
			new PostReport(reporter, post.getUser(), ReportTargetType.POST, postId, postId, category, reason));
		long reportCount = postReportRepository.countByTargetTypeAndTargetId(ReportTargetType.POST, postId);
		post.applyReportCount(reportCount);
	}

	void reportComment(UUID reporterId, UUID postId, UUID commentId, ReportCategory category, String reason) {
		validateReason(category, reason);
		FeedPost post = findReportablePostOrThrow(postId);
		PostComment comment = findReportableCommentOrThrow(postId, commentId);
		if (comment.getUser().getId().equals(reporterId)) {
			throw new SocialFeedForbiddenException("본인의 댓글은 신고할 수 없습니다.");
		}
		if (postReportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, ReportTargetType.COMMENT, commentId)) {
			throw new SocialFeedForbiddenException("이미 신고한 댓글입니다.");
		}
		UserAccount reporter = findUserOrThrow(reporterId);
		postReportRepository.save(
			new PostReport(reporter, comment.getUser(), ReportTargetType.COMMENT, commentId, post.getId(), category, reason));
		long reportCount = postReportRepository.countByTargetTypeAndTargetId(ReportTargetType.COMMENT, commentId);
		comment.applyReportCount(reportCount);
	}

	void reportUser(UUID reporterId, UUID targetUserId, ReportCategory category, String reason) {
		validateReason(category, reason);
		if (reporterId.equals(targetUserId)) {
			throw new SocialFeedForbiddenException("자기 자신은 신고할 수 없습니다.");
		}
		UserAccount reportedUser = findUserOrThrow(targetUserId);
		if (postReportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, ReportTargetType.USER, targetUserId)) {
			throw new SocialFeedForbiddenException("이미 신고한 사용자입니다.");
		}
		UserAccount reporter = findUserOrThrow(reporterId);
		postReportRepository.save(
			new PostReport(reporter, reportedUser, ReportTargetType.USER, targetUserId, null, category, reason));
	}

	private void validateReason(ReportCategory category, String reason) {
		if (category == ReportCategory.OTHER && (reason == null || reason.isBlank())) {
			throw new SocialFeedBadRequestException("기타 신고 사유는 상세 사유를 입력해야 합니다.");
		}
	}

	private UserAccount findUserOrThrow(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new SocialFeedNotFoundException("사용자를 찾을 수 없습니다."));
	}

	private FeedPost findReportablePostOrThrow(UUID postId) {
		FeedPost post = feedPostRepository.findById(postId)
			.orElseThrow(() -> new SocialFeedNotFoundException("게시글을 찾을 수 없습니다."));
		if (post.isDeleted() || post.isRemovedByModeration()) {
			throw new SocialFeedNotFoundException("게시글을 찾을 수 없습니다.");
		}
		return post;
	}

	private PostComment findReportableCommentOrThrow(UUID postId, UUID commentId) {
		PostComment comment = postCommentRepository.findById(commentId)
			.orElseThrow(() -> new SocialFeedNotFoundException("댓글을 찾을 수 없습니다."));
		if (!comment.getPost().getId().equals(postId) || comment.isDeleted() || comment.isRemovedByModeration()) {
			throw new SocialFeedNotFoundException("댓글을 찾을 수 없습니다.");
		}
		return comment;
	}
}
