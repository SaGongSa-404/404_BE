package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.FeedPostRepository;
import com.sagongsa.backend.domain.social.PostCommentCount;
import com.sagongsa.backend.domain.social.PostCommentRepository;
import com.sagongsa.backend.domain.social.PostVote;
import com.sagongsa.backend.domain.social.PostVoteRepository;
import com.sagongsa.backend.domain.user.UserProfile;
import com.sagongsa.backend.domain.user.UserProfileRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

// Exact feed-read method bodies from ae91f3f; test-only baseline, never a production bean.
@Transactional(readOnly = true)
class LegacySocialPostReadPath {
	private final FeedPostRepository feedPostRepository;
	private final PostVoteRepository postVoteRepository;
	private final PostCommentRepository postCommentRepository;
	private final UserProfileRepository userProfileRepository;
	private final BlockService blockService;

	LegacySocialPostReadPath(FeedPostRepository feedPostRepository,
		PostVoteRepository postVoteRepository,
		PostCommentRepository postCommentRepository,
		UserProfileRepository userProfileRepository,
		BlockService blockService) {
		this.feedPostRepository = feedPostRepository;
		this.postVoteRepository = postVoteRepository;
		this.postCommentRepository = postCommentRepository;
		this.userProfileRepository = userProfileRepository;
		this.blockService = blockService;
	}

	PostListResponse getPosts(UUID userId, Instant cursor, int size) {
		PageRequest pageable = PageRequest.of(0, size + 1);
		List<UUID> blockedIds = blockedIdsFor(userId);
		List<FeedPost> posts;
		if (blockedIds.isEmpty()) {
			posts = cursor != null ? feedPostRepository.findAllVisibleBefore(cursor, pageable) : feedPostRepository.findAllVisible(pageable);
		} else {
			posts = cursor != null ? feedPostRepository.findAllVisibleBeforeExcluding(cursor, blockedIds, pageable) : feedPostRepository.findAllVisibleExcluding(blockedIds, pageable);
		}

		boolean hasMore = posts.size() > size;
		if (hasMore) posts = posts.subList(0, size);

		List<PostResponse> items = toPostResponses(posts, userId, blockedIds);

		Instant nextCursor = hasMore && !posts.isEmpty() ? posts.get(posts.size() - 1).getCreatedAt() : null;
		return new PostListResponse(items, nextCursor, hasMore);
	}

	private List<PostResponse> toPostResponses(List<FeedPost> posts, UUID userId, List<UUID> blockedIds) {
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

	private List<UUID> blockedIdsFor(UUID userId) {
		return userId != null ? blockService.getBlockedUserIds(userId) : Collections.emptyList();
	}

	private Map<UUID, Long> countVisibleCommentsByPostIds(List<UUID> postIds, List<UUID> blockedIds) {
		if (postIds.isEmpty()) return Collections.emptyMap();

		List<PostCommentCount> counts = blockedIds.isEmpty()
			? postCommentRepository.countVisibleByPostIds(postIds)
			: postCommentRepository.countVisibleByPostIdsExcludingBlockers(postIds, blockedIds);
		return counts.stream()
			.collect(Collectors.toMap(PostCommentCount::postId, PostCommentCount::commentCount));
	}
}
