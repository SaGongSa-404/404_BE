package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.FeedPostRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SocialPostLookup {
	// Callers own the transaction. Reporting intentionally uses a different visibility policy.

	private final FeedPostRepository feedPostRepository;

	SocialPostLookup(FeedPostRepository feedPostRepository) {
		this.feedPostRepository = feedPostRepository;
	}

	FeedPost findPostOrThrow(UUID postId) {
		FeedPost post = feedPostRepository.findById(postId)
			.orElseThrow(() -> new SocialFeedNotFoundException("게시글을 찾을 수 없습니다."));
		if (!post.isVisible()) {
			throw new SocialFeedNotFoundException("게시글을 찾을 수 없습니다.");
		}
		return post;
	}
}
