package com.sagongsa.backend.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notification.trigger-worker.enabled", havingValue = "true", matchIfMissing = true)
class NotificationTriggerScheduler {

	private final NotificationTriggerWorker notificationTriggerWorker;

	NotificationTriggerScheduler(NotificationTriggerWorker notificationTriggerWorker) {
		this.notificationTriggerWorker = notificationTriggerWorker;
	}

	@Scheduled(fixedDelayString = "${app.notification.trigger-worker.fixed-delay-ms:60000}")
	void processDueNotifications() {
		notificationTriggerWorker.processDueNotifications();
	}
}
