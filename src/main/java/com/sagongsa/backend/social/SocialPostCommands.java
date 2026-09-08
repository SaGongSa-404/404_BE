package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.item.SavedItem;
import com.sagongsa.backend.domain.item.SavedItemRepository;
import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.FeedPostRepository;
import com.sagongsa.backend.domain.user.UserProfile;
import com.sagongsa.backend.domain.user.UserProfileRepository;
import java.util.Collections;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class SocialPostCommands {

	private final FeedPostRepository feedPostRepository;
	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final SavedItemRepository savedItemRepository;
	private final SocialPostLookup posts;
	private final SocialPostResponseQueries responses;
	private final SocialPostCreationPolicy policy;
	private final SocialPostCreationRepository creation;

	SocialPostCommands(FeedPostRepository feedPostRepository,
		UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		SavedItemRepository savedItemRepository,
		SocialPostLookup posts,
		SocialPostResponseQueries responses,
		SocialPostCreationPolicy policy,
		SocialPostCreationRepository creation) {
		this.feedPostRepository = feedPostRepository;
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.savedItemRepository = savedItemRepository;
		this.posts = posts;
		this.responses = responses;
		this.policy = policy;
		this.creation = creation;
	}

	PostResponse createPost(UUID userId, CreatePostRequest request) {
		UserAccount user = findUserOrThrow(userId);
		SavedItem item = resolveItem(userId, request.itemId());
		String imageUrl = policy.resolveImageUrl(request.imageUrl(), item);
		creation.acquireDuplicateLock(policy.postDuplicateLockKey(userId, request, item, imageUrl));
		FeedPost duplicate = creation.findRecentDuplicatePost(userId, request, item, imageUrl);
		if (duplicate != null) {
			String authorNickname = userProfileRepository.findByUserId(userId).isPresent()
				? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;
			return PostResponse.of(duplicate, authorNickname, responses.countVisibleCommentsByPostId(duplicate.getId(), Collections.emptyList()), null, userId);
		}
		FeedPost post = new FeedPost(user, item, request.title(), request.body(), imageUrl, request.price());
		feedPostRepository.save(post);
		String authorNickname = userProfileRepository.findByUserId(userId).isPresent()
			? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;
		return PostResponse.of(post, authorNickname, 0, null, userId);
	}

	PostResponse updatePost(UUID userId, UUID postId, UpdatePostRequest request) {
		FeedPost post = posts.findPostOrThrow(postId);
		if (!post.getUser().getId().equals(userId)) {
			throw new SocialFeedForbiddenException("본인의 게시글만 수정할 수 있습니다.");
		}
		post.updateBody(request.body());
		long commentCount = responses.countVisibleCommentsByPostId(postId, responses.blockedIdsFor(userId));
		PostVoteType myVote = responses.resolveMyVote(userId, postId);
		String authorNickname = userProfileRepository.findByUserId(post.getUser().getId()).isPresent()
			? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;
		return PostResponse.of(post, authorNickname, commentCount, myVote, userId);
	}

	void deletePost(UUID userId, UUID postId) {
		FeedPost post = posts.findPostOrThrow(postId);
		if (!post.getUser().getId().equals(userId)) {
			throw new SocialFeedForbiddenException("본인의 게시글만 삭제할 수 있습니다.");
		}
		post.softDelete();
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
