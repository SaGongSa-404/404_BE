package com.sagongsa.backend.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
	UUID id,
	String type,
	String title,
	String body,
	UUID itemId,
	UUID decisionId,
	UUID reminderId,
	String targetPath,
	boolean read,
	Instant readAt,
	Instant createdAt
) {
}
