package com.sagongsa.backend.notification;

import java.util.UUID;

final class NotificationTriggerTargets {
	private NotificationTriggerTargets() {}

	record VotePostTarget(UUID postId, UUID userId, UUID itemId, int voteCount) {
	}

	record RegretFollowUpTarget(
		UUID userId,
		UUID itemId,
		UUID decisionId,
		UUID reminderId,
		String itemTitle
	) {
	}

	record WishlistReminderTarget(UUID itemId, UUID userId) {
	}
}
