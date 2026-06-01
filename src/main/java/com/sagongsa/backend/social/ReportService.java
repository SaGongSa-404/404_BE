package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.ReportCategory;
import com.sagongsa.backend.domain.enums.ReportTargetType;
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
    private final PostCommentRepository postCommentRepository;
    private final UserAccountRepository userAccountRepository;
    private final SocialPostService socialPostService;

    ReportService(PostReportRepository postReportRepository,
        PostCommentRepository postCommentRepository,
        UserAccountRepository userAccountRepository,
        SocialPostService socialPostService) {
        this.postReportRepository = postReportRepository;
        this.postCommentRepository = postCommentRepository;
        this.userAccountRepository = userAccountRepository;
        this.socialPostService = socialPostService;
    }

    void reportPost(UUID reporterId, UUID postId, ReportCategory category, String reason) {
        socialPostService.findPostOrThrow(postId);
        if (postReportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, ReportTargetType.POST, postId)) {
            throw new SocialFeedForbiddenException("이미 신고한 게시글입니다.");
        }
        UserAccount reporter = findUserOrThrow(reporterId);
        postReportRepository.save(new PostReport(reporter, ReportTargetType.POST, postId, category, reason));
    }

    void reportComment(UUID reporterId, UUID postId, UUID commentId, ReportCategory category, String reason) {
        socialPostService.findPostOrThrow(postId);
        PostComment comment = postCommentRepository.findById(commentId)
            .orElseThrow(() -> new SocialFeedNotFoundException("댓글을 찾을 수 없습니다."));
        if (!comment.getPost().getId().equals(postId)) {
            throw new SocialFeedNotFoundException("댓글을 찾을 수 없습니다.");
        }
        if (postReportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, ReportTargetType.COMMENT, commentId)) {
            throw new SocialFeedForbiddenException("이미 신고한 댓글입니다.");
        }
        UserAccount reporter = findUserOrThrow(reporterId);
        postReportRepository.save(new PostReport(reporter, ReportTargetType.COMMENT, commentId, category, reason));
    }

    private UserAccount findUserOrThrow(UUID userId) {
        return userAccountRepository.findById(userId)
            .orElseThrow(() -> new SocialFeedNotFoundException("사용자를 찾을 수 없습니다."));
    }
}
