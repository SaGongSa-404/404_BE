package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.PostComment;
import com.sagongsa.backend.domain.social.PostCommentRepository;
import com.sagongsa.backend.domain.user.UserProfile;
import com.sagongsa.backend.domain.user.UserProfileRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	private final UserProfileRepository userProfileRepository;
	private final BlockService blockService;

	CommentService(PostCommentRepository postCommentRepository,
		SocialPostService socialPostService,
		UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		BlockService blockService) {
		this.postCommentRepository = postCommentRepository;
		this.socialPostService = socialPostService;
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.blockService = blockService;
	}

	@Transactional
	CommentResponse createComment(UUID userId, UUID postId, CreateCommentRequest request) {
		FeedPost post = socialPostService.findPostOrThrow(postId);
		UserAccount user = userAccountRepository.findById(userId)
			.orElseThrow(() -> new SocialFeedNotFoundException("사용자를 찾을 수 없습니다."));

		PostComment comment = new PostComment(post, user, request.body());
		postCommentRepository.save(comment);
		String authorNickname = resolveCommentNickname(postId, userId);
		return CommentResponse.of(comment, userId, authorNickname);
	}

	CommentListResponse getComments(UUID userId, UUID postId, int page, int size) {
		socialPostService.findPostOrThrow(postId);

		List<UUID> blockedIds = userId != null
			? blockService.getBlockedUserIds(userId)
			: java.util.Collections.emptyList();

		Page<PostComment> commentPage = blockedIds.isEmpty()
			? postCommentRepository.findVisibleByPostId(postId, PageRequest.of(page - 1, size))
			: postCommentRepository.findVisibleByPostIdExcludingBlockers(postId, blockedIds, PageRequest.of(page - 1, size));

		Map<UUID, String> nicknameMap = buildCommentNicknameMap(postId);

		List<CommentResponse> items = commentPage.getContent().stream()
			.map(c -> CommentResponse.of(c, userId,
				nicknameMap.getOrDefault(c.getUser().getId(), UserProfile.UNKNOWN_NICKNAME)))
			.toList();

		return new CommentListResponse(items, commentPage.getTotalElements());
	}

	private Map<UUID, String> buildCommentNicknameMap(UUID postId) {
		List<UUID> commenterIds = postCommentRepository.findCommenterIdsByPostIdOrderedByFirstComment(postId);
		Set<UUID> existingProfileIds = new HashSet<>(userProfileRepository.findExistingProfileUserIds(commenterIds));
		Map<UUID, String> map = new HashMap<>();
		for (int i = 0; i < commenterIds.size(); i++) {
			UUID uid = commenterIds.get(i);
			map.put(uid, existingProfileIds.contains(uid)
				? UserProfile.COMMENT_NICKNAME_PREFIX + (i + 1)
				: UserProfile.UNKNOWN_NICKNAME);
		}
		return map;
	}

	private String resolveCommentNickname(UUID postId, UUID userId) {
		return buildCommentNicknameMap(postId).getOrDefault(userId, UserProfile.UNKNOWN_NICKNAME);
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
