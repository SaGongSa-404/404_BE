package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.item.SavedItem;
import com.sagongsa.backend.domain.item.SavedItemRepository;
import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.FeedPostRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class SocialPostService {

	private final FeedPostRepository feedPostRepository;
	private final PostVoteRepository postVoteRepository;
	private final PostCommentRepository postCommentRepository;
	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final SavedItemRepository savedItemRepository;

	SocialPostService(FeedPostRepository feedPostRepository,
		PostVoteRepository postVoteRepository,
		PostCommentRepository postCommentRepository,
		UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		SavedItemRepository savedItemRepository) {
		this.feedPostRepository = feedPostRepository;
		this.postVoteRepository = postVoteRepository;
		this.postCommentRepository = postCommentRepository;
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.savedItemRepository = savedItemRepository;
	}

	@Transactional
	PostResponse createPost(UUID userId, CreatePostRequest request) {
		UserAccount user = findUserOrThrow(userId);
		SavedItem item = resolveItem(userId, request.itemId());
		FeedPost post = new FeedPost(user, item, request.title(), request.body(), request.imageUrl(), request.price());
		feedPostRepository.save(post);
		String authorNickname = userProfileRepository.findByUserId(userId).isPresent()
			? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;
		return PostResponse.of(post, authorNickname, 0, null, userId);
	}

	PostListResponse getPosts(UUID userId, Instant cursor, int size) {
		PageRequest pageable = PageRequest.of(0, size + 1);
		List<FeedPost> posts = cursor != null
			? feedPostRepository.findAllVisibleBefore(cursor, pageable)
			: feedPostRepository.findAllVisible(pageable);

		boolean hasMore = posts.size() > size;
		if (hasMore) posts = posts.subList(0, size);

		List<PostResponse> items = toPostResponses(posts, userId);

		Instant nextCursor = hasMore && !posts.isEmpty() ? posts.get(posts.size() - 1).getCreatedAt() : null;
		return new PostListResponse(items, nextCursor, hasMore);
	}

	PostResponse getPost(UUID userId, UUID postId) {
		FeedPost post = findPostOrThrow(postId);
		long commentCount = postCommentRepository.countByPostIdAndDeletedAtIsNull(postId);
		PostVoteType myVote = resolveMyVote(userId, postId);
		String authorNickname = userProfileRepository.findByUserId(post.getUser().getId()).isPresent()
			? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;
		return PostResponse.of(post, authorNickname, commentCount, myVote, userId);
	}

	@Transactional
	void deletePost(UUID userId, UUID postId) {
		FeedPost post = findPostOrThrow(postId);
		if (!post.getUser().getId().equals(userId)) {
			throw new SocialFeedForbiddenException("본인의 게시글만 삭제할 수 있습니다.");
		}
		post.softDelete();
	}

	PostListResponse getMyPosts(UUID userId, Instant cursor, int size) {
		PageRequest pageable = PageRequest.of(0, size + 1);
		List<FeedPost> posts = cursor != null
			? feedPostRepository.findByUserIdVisibleBefore(userId, cursor, pageable)
			: feedPostRepository.findByUserIdVisible(userId, pageable);

		boolean hasMore = posts.size() > size;
		if (hasMore) posts = posts.subList(0, size);

		List<PostResponse> items = toPostResponses(posts, userId);

		Instant nextCursor = hasMore && !posts.isEmpty() ? posts.get(posts.size() - 1).getCreatedAt() : null;
		return new PostListResponse(items, nextCursor, hasMore);
	}

	FeedPost findPostOrThrow(UUID postId) {
		FeedPost post = feedPostRepository.findById(postId)
			.orElseThrow(() -> new SocialFeedNotFoundException("게시글을 찾을 수 없습니다."));
		if (post.isDeleted()) {
			throw new SocialFeedNotFoundException("게시글을 찾을 수 없습니다.");
		}
		return post;
	}

	private List<PostResponse> toPostResponses(List<FeedPost> posts, UUID userId) {
		if (posts.isEmpty()) return Collections.emptyList();

		List<UUID> postIds = posts.stream().map(FeedPost::getId).toList();
		List<UUID> authorIds = posts.stream().map(p -> p.getUser().getId()).distinct().toList();

		Map<UUID, Long> commentCounts = feedPostRepository.countCommentsByPostIds(postIds).stream()
			.collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

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

	private PostVoteType resolveMyVote(UUID userId, UUID postId) {
		if (userId == null) return null;
		return postVoteRepository.findByPostIdAndUserId(postId, userId)
			.filter(PostVote::isActive)
			.map(PostVote::getVoteType)
			.orElse(null);
	}

	private SavedItem resolveItem(UUID userId, UUID itemId) {
		if (itemId == null) return null;
		return savedItemRepository.findById(itemId)
			.filter(item -> item.getUser().getId().equals(userId))
			.orElse(null);
	}

	private UserAccount findUserOrThrow(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new SocialFeedNotFoundException("사용자를 찾을 수 없습니다."));
	}
}
