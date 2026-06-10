package com.sagongsa.backend.notification;

import java.util.UUID;

public record NotificationPublishRequest(
	UUID userId,
	String notificationType,
	String title,
	String body,
	UUID itemId,
	UUID decisionId,
	UUID reminderId,
	String targetPath,
	String dedupeKey,
	String channelId
) {

	public NotificationPublishRequest {
		if (channelId == null || channelId.isBlank()) {
			channelId = NotificationChannels.fromType(notificationType);
		}
	}
}
