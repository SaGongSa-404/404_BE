package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.FeedPostRepository;
import com.sagongsa.backend.domain.social.PostCommentRepository;
import com.sagongsa.backend.domain.social.PostVote;
import com.sagongsa.backend.domain.social.PostVoteRepository;
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

	SocialPostService(FeedPostRepository feedPostRepository,
		PostVoteRepository postVoteRepository,
		PostCommentRepository postCommentRepository,
		UserAccountRepository userAccountRepository) {
		this.feedPostRepository = feedPostRepository;
		this.postVoteRepository = postVoteRepository;
		this.postCommentRepository = postCommentRepository;
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional
	PostResponse createPost(UUID userId, CreatePostRequest request) {
		UserAccount user = findUserOrThrow(userId);
		FeedPost post = new FeedPost(user, request.title(), request.body(), request.imageUrl(), request.price());
		feedPostRepository.save(post);
		return PostResponse.of(post, 0, null);
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
		return PostResponse.of(post, commentCount, myVote);
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

		Map<UUID, Long> commentCounts = feedPostRepository.countCommentsByPostIds(postIds).stream()
			.collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

		Map<UUID, PostVote> myVotes = userId == null
			? Collections.emptyMap()
			: postVoteRepository.findByPostIdsAndUserId(postIds, userId).stream()
				.collect(Collectors.toMap(v -> v.getPost().getId(), v -> v));

		return posts.stream()
			.map(post -> {
				long commentCount = commentCounts.getOrDefault(post.getId(), 0L);
				PostVote vote = myVotes.get(post.getId());
				PostVoteType myVote = (vote != null && vote.isActive()) ? vote.getVoteType() : null;
				return PostResponse.of(post, commentCount, myVote);
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

	private UserAccount findUserOrThrow(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new SocialFeedNotFoundException("사용자를 찾을 수 없습니다."));
	}
}
