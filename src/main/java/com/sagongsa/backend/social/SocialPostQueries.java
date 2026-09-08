package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.FeedPostRepository;
import com.sagongsa.backend.domain.user.UserProfile;
import com.sagongsa.backend.domain.user.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class SocialPostQueries {

	private final FeedPostRepository feedPostRepository;
	private final UserProfileRepository userProfileRepository;
	private final SocialPostLookup posts;
	private final SocialPostResponseQueries responses;

	SocialPostQueries(FeedPostRepository feedPostRepository,
		UserProfileRepository userProfileRepository,
		SocialPostLookup posts,
		SocialPostResponseQueries responses) {
		this.feedPostRepository = feedPostRepository;
		this.userProfileRepository = userProfileRepository;
		this.posts = posts;
		this.responses = responses;
	}

	PostListResponse getPosts(UUID userId, Instant cursor, int size) {
		PageRequest pageable = PageRequest.of(0, size + 1);
		List<UUID> blockedIds = responses.blockedIdsFor(userId);
		List<FeedPost> posts;
		if (blockedIds.isEmpty()) {
			posts = cursor != null ? feedPostRepository.findAllVisibleBefore(cursor, pageable) : feedPostRepository.findAllVisible(pageable);
		} else {
			posts = cursor != null ? feedPostRepository.findAllVisibleBeforeExcluding(cursor, blockedIds, pageable) : feedPostRepository.findAllVisibleExcluding(blockedIds, pageable);
		}

		boolean hasMore = posts.size() > size;
		if (hasMore) posts = posts.subList(0, size);

		List<PostResponse> items = responses.toPostResponses(posts, userId, blockedIds);

		Instant nextCursor = hasMore && !posts.isEmpty() ? posts.get(posts.size() - 1).getCreatedAt() : null;
		return new PostListResponse(items, nextCursor, hasMore);
	}

	PostResponse getPost(UUID userId, UUID postId) {
		FeedPost post = posts.findPostOrThrow(postId);
		if (userId != null && responses.blockedIdsFor(userId).contains(post.getUser().getId())) {
			throw new SocialFeedNotFoundException("게시글을 찾을 수 없습니다.");
		}
		long commentCount = responses.countVisibleCommentsByPostId(postId, responses.blockedIdsFor(userId));
		PostVoteType myVote = responses.resolveMyVote(userId, postId);
		String authorNickname = userProfileRepository.findByUserId(post.getUser().getId()).isPresent()
			? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;
		return PostResponse.of(post, authorNickname, commentCount, myVote, userId);
	}

	PostListResponse getMyPosts(UUID userId, Instant cursor, int size) {
		PageRequest pageable = PageRequest.of(0, size + 1);
		List<FeedPost> posts = cursor != null
			? feedPostRepository.findByUserIdVisibleBefore(userId, cursor, pageable)
			: feedPostRepository.findByUserIdVisible(userId, pageable);

		boolean hasMore = posts.size() > size;
		if (hasMore) posts = posts.subList(0, size);

		List<PostResponse> items = responses.toPostResponses(posts, userId, responses.blockedIdsFor(userId));

		Instant nextCursor = hasMore && !posts.isEmpty() ? posts.get(posts.size() - 1).getCreatedAt() : null;
		return new PostListResponse(items, nextCursor, hasMore);
	}
}
