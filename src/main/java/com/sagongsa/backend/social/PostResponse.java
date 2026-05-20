package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.item.SavedItem;
import com.sagongsa.backend.domain.social.FeedPost;
import java.time.Instant;
import java.util.UUID;

public record PostResponse(
	UUID id,
	String authorNickname,
	UUID authorUserId,
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
	boolean mine,
	Instant createdAt
) {
	public record ProductSection(String name, Integer price, String link) {}

	public static PostResponse of(FeedPost post, String authorNickname, long commentCount, PostVoteType myVote, UUID currentUserId) {
		SavedItem item = post.getItem();
		ProductSection product = null;
		boolean linkAvailable = false;
		if (item != null) {
			product = new ProductSection(item.getTitle(), item.getListedPrice(), item.getOriginalUrl());
			linkAvailable = item.getOriginalUrl() != null && !item.getOriginalUrl().isBlank();
		}
		UUID authorUserId = post.getUser().getId();
		boolean mine = currentUserId != null && currentUserId.equals(authorUserId);
		return new PostResponse(
			post.getId(),
			authorNickname,
			authorUserId,
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
			mine,
			post.getCreatedAt()
		);
	}
}
