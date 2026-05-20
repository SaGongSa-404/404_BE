package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.social.FeedPost;
import java.time.Instant;
import java.util.UUID;

record PostResponse(
	UUID id,
	UUID userId,
	String title,
	String body,
	String imageUrl,
	Integer price,
	int goCount,
	int stopCount,
	long commentCount,
	PostVoteType myVote,
	Instant createdAt
) {
	static PostResponse of(FeedPost post, long commentCount, PostVoteType myVote) {
		return new PostResponse(
			post.getId(),
			post.getUser().getId(),
			post.getTitle(),
			post.getBody(),
			post.getImageUrl(),
			post.getPrice(),
			post.getGoCount(),
			post.getStopCount(),
			commentCount,
			myVote,
			post.getCreatedAt()
		);
	}
}
