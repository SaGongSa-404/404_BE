package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.PostComment;
import com.sagongsa.backend.domain.social.PostCommentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CommentService {

	private final PostCommentRepository postCommentRepository;
	private final SocialPostService socialPostService;
	private final UserAccountRepository userAccountRepository;

	CommentService(PostCommentRepository postCommentRepository,
		SocialPostService socialPostService,
		UserAccountRepository userAccountRepository) {
		this.postCommentRepository = postCommentRepository;
		this.socialPostService = socialPostService;
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional
	CommentResponse createComment(UUID userId, UUID postId, CreateCommentRequest request) {
		FeedPost post = socialPostService.findPostOrThrow(postId);
		UserAccount user = userAccountRepository.findById(userId)
			.orElseThrow(() -> new SocialFeedNotFoundException("사용자를 찾을 수 없습니다."));

		PostComment comment = new PostComment(post, user, request.body());
		postCommentRepository.save(comment);
		return CommentResponse.of(comment, userId);
	}

	CommentListResponse getComments(UUID userId, UUID postId, int page, int size) {
		socialPostService.findPostOrThrow(postId);
		Page<PostComment> commentPage = postCommentRepository.findVisibleByPostId(
			postId, PageRequest.of(page - 1, size));

		List<CommentResponse> items = commentPage.getContent().stream()
			.map(c -> CommentResponse.of(c, userId))
			.toList();

		return new CommentListResponse(items, commentPage.getTotalElements());
	}

	@Transactional
	void deleteComment(UUID userId, UUID postId, UUID commentId) {
		PostComment comment = postCommentRepository.findById(commentId)
			.orElseThrow(() -> new SocialFeedNotFoundException("댓글을 찾을 수 없습니다."));
		if (!comment.getPost().getId().equals(postId)) {
			throw new SocialFeedNotFoundException("댓글을 찾을 수 없습니다.");
		}
		if (!comment.getUser().getId().equals(userId)) {
			throw new SocialFeedForbiddenException("본인의 댓글만 삭제할 수 있습니다.");
		}
		comment.softDelete();
	}
}
