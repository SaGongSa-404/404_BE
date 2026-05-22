package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.social.PostComment;
import java.time.Instant;
import java.util.UUID;

record CommentResponse(
	UUID id,
	String body,
	boolean mine,
	String authorNickname,
	Instant createdAt
) {
	static CommentResponse of(PostComment comment, UUID currentUserId, String authorNickname) {
		return new CommentResponse(
			comment.getId(),
			comment.getBody(),
			comment.getUser().getId().equals(currentUserId),
			authorNickname,
			comment.getCreatedAt()
		);
	}
}
