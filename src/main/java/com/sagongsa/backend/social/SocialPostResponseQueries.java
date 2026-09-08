package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.PostCommentCount;
import com.sagongsa.backend.domain.social.PostCommentRepository;
import com.sagongsa.backend.domain.social.PostVote;
import com.sagongsa.backend.domain.social.PostVoteRepository;
import com.sagongsa.backend.domain.user.UserProfile;
import com.sagongsa.backend.domain.user.UserProfileRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class SocialPostResponseQueries {
	// Read helpers share the caller's persistence context, including command response assembly.

	private final PostVoteRepository postVoteRepository;
	private final PostCommentRepository postCommentRepository;
	private final UserProfileRepository userProfileRepository;
	private final BlockService blockService;

	SocialPostResponseQueries(PostVoteRepository postVoteRepository,
		PostCommentRepository postCommentRepository,
		UserProfileRepository userProfileRepository,
		BlockService blockService) {
		this.postVoteRepository = postVoteRepository;
		this.postCommentRepository = postCommentRepository;
		this.userProfileRepository = userProfileRepository;
		this.blockService = blockService;
	}

	List<PostResponse> toPostResponses(List<FeedPost> posts, UUID userId, List<UUID> blockedIds) {
		if (posts.isEmpty()) return Collections.emptyList();

		List<UUID> postIds = posts.stream().map(FeedPost::getId).toList();
		List<UUID> authorIds = posts.stream().map(p -> p.getUser().getId()).distinct().toList();

		Map<UUID, Long> commentCounts = countVisibleCommentsByPostIds(postIds, blockedIds);

		Map<UUID, PostVote> myVotes = userId == null
			? Collections.emptyMap()
			: postVoteRepository.findByPostIdsAndUserId(postIds, userId).stream()
				.collect(Collectors.toMap(v -> v.getPost().getId(), v -> v));

		java.util.Set<UUID> existingProfileIds = new java.util.HashSet<>(
				userProfileRepository.findExistingProfileUserIds(authorIds));

		return posts.stream()
			.map(post -> {
				long commentCount = commentCounts.getOrDefault(post.getId(), 0L);
				PostVote vote = myVotes.get(post.getId());
				PostVoteType myVote = (vote != null && vote.isActive()) ? vote.getVoteType() : null;
				String authorNickname = existingProfileIds.contains(post.getUser().getId())
					? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;
				return PostResponse.of(post, authorNickname, commentCount, myVote, userId);
			})
			.toList();
	}

	List<UUID> blockedIdsFor(UUID userId) {
		return userId != null ? blockService.getBlockedUserIds(userId) : Collections.emptyList();
	}

	long countVisibleCommentsByPostId(UUID postId, List<UUID> blockedIds) {
		return countVisibleCommentsByPostIds(List.of(postId), blockedIds).getOrDefault(postId, 0L);
	}

	private Map<UUID, Long> countVisibleCommentsByPostIds(List<UUID> postIds, List<UUID> blockedIds) {
		if (postIds.isEmpty()) return Collections.emptyMap();

		List<PostCommentCount> counts = blockedIds.isEmpty()
			? postCommentRepository.countVisibleByPostIds(postIds)
			: postCommentRepository.countVisibleByPostIdsExcludingBlockers(postIds, blockedIds);
		return counts.stream()
			.collect(Collectors.toMap(PostCommentCount::postId, PostCommentCount::commentCount));
	}

	PostVoteType resolveMyVote(UUID userId, UUID postId) {
		if (userId == null) return null;
		return postVoteRepository.findByPostIdAndUserId(postId, userId)
			.filter(PostVote::isActive)
			.map(PostVote::getVoteType)
			.orElse(null);
	}
}
