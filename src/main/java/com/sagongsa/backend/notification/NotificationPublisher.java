package com.sagongsa.backend.notification;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationPublisher {

	private final JdbcTemplate jdbcTemplate;
	private final PushNotificationService pushNotificationService;

	public NotificationPublisher(JdbcTemplate jdbcTemplate, PushNotificationService pushNotificationService) {
		this.jdbcTemplate = jdbcTemplate;
		this.pushNotificationService = pushNotificationService;
	}

	@Transactional
	public NotificationPublishResult publish(NotificationPublishRequest request) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UUID notificationId = UUID.randomUUID();
		int inserted = jdbcTemplate.update(
			"""
			insert into notifications (
				id, user_id, notification_type, title, body,
				item_id, decision_id, reminder_id, target_path,
				channel_id, dedupe_key, is_read, created_at, updated_at
			)
			values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, ?, ?)
			on conflict (user_id, notification_type, dedupe_key)
				where dedupe_key is not null
				do nothing
			""",
			notificationId,
			request.userId(),
			request.notificationType(),
			request.title(),
			request.body(),
			request.itemId(),
			request.decisionId(),
			request.reminderId(),
			request.targetPath(),
			request.channelId(),
			request.dedupeKey(),
			now,
			now
		);
		if (inserted == 0) {
			return NotificationPublishResult.duplicate();
		}

		sendAfterCommit(new NotificationPushMessage(
			request.userId(),
			notificationId,
			request.notificationType(),
			request.title(),
			request.body(),
			request.targetPath(),
			request.channelId()
		));
		return NotificationPublishResult.created(notificationId);
	}

	private void sendAfterCommit(NotificationPushMessage message) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			pushNotificationService.send(message);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				pushNotificationService.send(message);
			}
		});
	}
}
