package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.social.FeedPost;
import java.time.Instant;
import java.util.UUID;

public record PostResponse(
	UUID id,
	String authorNickname,
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
	public static PostResponse of(FeedPost post, String authorNickname, long commentCount, PostVoteType myVote) {
		return new PostResponse(
			post.getId(),
			authorNickname,
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
