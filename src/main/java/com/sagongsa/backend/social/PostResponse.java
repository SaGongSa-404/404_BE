package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.item.SavedItem;
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
	ProductSection product,
	boolean linkAvailable,
	Instant createdAt
) {
	public record ProductSection(String name, Integer price, String link) {}

	public static PostResponse of(FeedPost post, String authorNickname, long commentCount, PostVoteType myVote) {
		SavedItem item = post.getItem();
		ProductSection product = null;
		boolean linkAvailable = false;
		if (item != null) {
			product = new ProductSection(item.getTitle(), item.getListedPrice(), item.getOriginalUrl());
			linkAvailable = item.getOriginalUrl() != null && !item.getOriginalUrl().isBlank();
		}
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
			product,
			linkAvailable,
			post.getCreatedAt()
		);
	}
}
