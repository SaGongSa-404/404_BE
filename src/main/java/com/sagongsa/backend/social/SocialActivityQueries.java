package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.enums.ModerationStatus;
import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.social.FeedPostRepository;
import com.sagongsa.backend.domain.social.PostCommentCount;
import com.sagongsa.backend.domain.social.PostCommentRepository;
import com.sagongsa.backend.domain.social.PostVote;
import com.sagongsa.backend.domain.social.PostVoteRepository;
import com.sagongsa.backend.domain.user.UserProfile;
import com.sagongsa.backend.domain.user.UserProfileRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SocialActivityQueries {
	private final UserProfileRepository userProfileRepository;
	private final FeedPostRepository feedPostRepository;
	private final PostVoteRepository postVoteRepository;
	private final PostCommentRepository postCommentRepository;
	private final BlockService blockService;

	public SocialActivityQueries(
		UserProfileRepository userProfileRepository,
		FeedPostRepository feedPostRepository,
		PostVoteRepository postVoteRepository,
		PostCommentRepository postCommentRepository,
		BlockService blockService
	) {
		this.userProfileRepository = userProfileRepository;
		this.feedPostRepository = feedPostRepository;
		this.postVoteRepository = postVoteRepository;
		this.postCommentRepository = postCommentRepository;
		this.blockService = blockService;
	}

	public long countMyPosts(UUID userId) {
		return feedPostRepository.countByUserIdAndDeletedAtIsNullAndModerationStatus(userId, ModerationStatus.ACTIVE);
	}

	public PostListResponse getMyPosts(UUID userId, Instant cursor, int size) {
		PageRequest pageable = PageRequest.of(0, size + 1);
		var posts = cursor != null
			? feedPostRepository.findByUserIdVisibleBefore(userId, cursor, pageable)
			: feedPostRepository.findByUserIdVisible(userId, pageable);

		boolean hasMore = posts.size() > size;
		if (hasMore) posts = posts.subList(0, size);

		String myNickname = userProfileRepository.findByUserId(userId).isPresent()
			? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;
		List<UUID> blockedIds = blockService.getBlockedUserIds(userId);
		Map<UUID, Long> commentCounts = countVisibleCommentsByPostIds(
			posts.stream().map(post -> post.getId()).toList(),
			blockedIds);
		Map<UUID, PostVoteType> activeVotes = findActiveVotesByPostIds(
			posts.stream().map(post -> post.getId()).toList(),
			userId);

		var items = posts.stream().map(post -> {
			long cc = commentCounts.getOrDefault(post.getId(), 0L);
			return PostResponse.of(post, myNickname, cc, activeVotes.get(post.getId()), userId);
		}).toList();

		Instant nextCursor = hasMore && !posts.isEmpty() ? posts.get(posts.size() - 1).getCreatedAt() : null;
		return new PostListResponse(items, nextCursor, hasMore);
	}

	public PostListResponse getMyVotedPosts(UUID userId, Instant cursor, int size) {
		PageRequest pageable = PageRequest.of(0, size + 1);
		var votes = cursor != null
			? postVoteRepository.findActiveByUserIdBefore(userId, cursor, pageable)
			: postVoteRepository.findActiveByUserId(userId, pageable);

		boolean hasMore = votes.size() > size;
		if (hasMore) votes = votes.subList(0, size);

		List<UUID> authorIds = votes.stream()
			.map(v -> v.getPost().getUser().getId()).distinct().toList();
		Set<UUID> existingProfileIds = new HashSet<>(userProfileRepository.findExistingProfileUserIds(authorIds));
		List<UUID> blockedIds = blockService.getBlockedUserIds(userId);
		Map<UUID, Long> commentCounts = countVisibleCommentsByPostIds(
			votes.stream().map(v -> v.getPost().getId()).toList(),
			blockedIds);

		var items = votes.stream().map(vote -> {
			var post = vote.getPost();
			String authorNickname = existingProfileIds.contains(post.getUser().getId())
				? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;
			long cc = commentCounts.getOrDefault(post.getId(), 0L);
			return PostResponse.of(post, authorNickname, cc, vote.getVoteType(), userId);
		}).toList();

		Instant nextCursor = hasMore && !votes.isEmpty() ? votes.get(votes.size() - 1).getCreatedAt() : null;
		return new PostListResponse(items, nextCursor, hasMore);
	}

	private Map<UUID, Long> countVisibleCommentsByPostIds(List<UUID> postIds, List<UUID> blockedIds) {
		if (postIds.isEmpty()) return Collections.emptyMap();

		List<PostCommentCount> counts = blockedIds.isEmpty()
			? postCommentRepository.countVisibleByPostIds(postIds)
			: postCommentRepository.countVisibleByPostIdsExcludingBlockers(postIds, blockedIds);
		return counts.stream()
			.collect(Collectors.toMap(PostCommentCount::postId, PostCommentCount::commentCount));
	}

	private Map<UUID, PostVoteType> findActiveVotesByPostIds(List<UUID> postIds, UUID userId) {
		if (postIds.isEmpty()) return Collections.emptyMap();

		return postVoteRepository.findByPostIdsAndUserId(postIds, userId).stream()
			.filter(PostVote::isActive)
			.collect(Collectors.toMap(vote -> vote.getPost().getId(), PostVote::getVoteType));
	}
}
