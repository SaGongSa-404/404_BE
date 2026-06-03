package com.sagongsa.backend.notification;

import java.util.UUID;

public record NotificationPushMessage(
	UUID userId,
	UUID notificationId,
	String notificationType,
	String title,
	String body,
	String targetPath
) {
}
