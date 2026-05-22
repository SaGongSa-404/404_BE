package com.sagongsa.backend.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notification.reminder-worker.enabled", havingValue = "true", matchIfMissing = true)
class ReminderNotificationScheduler {

	private final ReminderNotificationWorker reminderNotificationWorker;

	ReminderNotificationScheduler(ReminderNotificationWorker reminderNotificationWorker) {
		this.reminderNotificationWorker = reminderNotificationWorker;
	}

	@Scheduled(fixedDelayString = "${app.notification.reminder-worker.fixed-delay-ms:60000}")
	void runScheduled() {
		reminderNotificationWorker.processDueReminders();
	}
}
