package com.sagongsa.backend.notification;

import java.util.UUID;

public record NotificationPublishResult(
	UUID notificationId,
	boolean created
) {

	static NotificationPublishResult duplicate() {
		return new NotificationPublishResult(null, false);
	}

	static NotificationPublishResult created(UUID notificationId) {
		return new NotificationPublishResult(notificationId, true);
	}
}
