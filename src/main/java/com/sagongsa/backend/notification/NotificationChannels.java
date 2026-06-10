package com.sagongsa.backend.notification;

import com.sagongsa.backend.domain.enums.NotificationType;

final class NotificationChannels {

	static final String SOCIAL_ACTIVITY = "social_activity";
	static final String CONSUMPTION_MANAGEMENT = "consumption_management";
	static final String SERVICE_NOTICE = "service_notice";

	private NotificationChannels() {
	}

	static String fromType(String notificationType) {
		NotificationType type = NotificationType.valueOf(notificationType);
		return switch (type) {
			case SOCIAL_VOTE, SOCIAL_FIRST_VOTE, SOCIAL_VOTE_SUMMARY, SOCIAL_DECISION_NUDGE, SOCIAL_COMMENT ->
				SOCIAL_ACTIVITY;
			case APP_UPDATE, MAINTENANCE_NOTICE -> SERVICE_NOTICE;
			default -> CONSUMPTION_MANAGEMENT;
		};
	}
}
