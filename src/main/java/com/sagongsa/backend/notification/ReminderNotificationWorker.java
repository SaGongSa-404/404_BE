package com.sagongsa.backend.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReminderNotificationWorker {

	private static final int BATCH_SIZE = 50;
	private static final Logger log = LoggerFactory.getLogger(ReminderNotificationWorker.class);

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;
	private final NotificationPublisher notificationPublisher;

	public ReminderNotificationWorker(
		JdbcTemplate jdbcTemplate,
		TransactionTemplate transactionTemplate,
		NotificationPublisher notificationPublisher
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
		this.notificationPublisher = notificationPublisher;
	}

	public int processDueReminders() {
		List<UUID> reminderIds = findDueReminderIds();
		int processedCount = 0;
		for (UUID reminderId : reminderIds) {
			if (processReminder(reminderId)) {
				processedCount++;
			}
		}
		return processedCount;
	}

	public int processDueReminder(UUID reminderId) {
		if (!isDueReminder(reminderId)) {
			return 0;
		}
		return processReminder(reminderId) ? 1 : 0;
	}

	private boolean isDueReminder(UUID reminderId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"""
			select exists(
				select 1
				from reminder_schedules rs
				join users u on u.id = rs.user_id
				left join user_notification_settings uns on uns.user_id = rs.user_id
				where rs.id = ?
				  and rs.reminder_type = 'REGRET_CHECK_7_DAYS'
				  and rs.status = 'SCHEDULED'
				  and rs.scheduled_for <= ?
				  and u.status = 'ACTIVE'
				  and coalesce(uns.regret_reminder_enabled, true) = true
			)
			""",
			Boolean.class,
			reminderId,
			OffsetDateTime.now(ZoneOffset.UTC)
		);
		return Boolean.TRUE.equals(exists);
	}

	private List<UUID> findDueReminderIds() {
		return jdbcTemplate.query(
			"""
			select rs.id
			from reminder_schedules rs
			join users u on u.id = rs.user_id
			left join user_notification_settings uns on uns.user_id = rs.user_id
			where rs.reminder_type = 'REGRET_CHECK_7_DAYS'
			  and rs.status = 'SCHEDULED'
			  and rs.scheduled_for <= ?
			  and u.status = 'ACTIVE'
			  and coalesce(uns.regret_reminder_enabled, true) = true
			order by rs.scheduled_for asc, rs.id asc
			limit ?
			for update of rs skip locked
			""",
			(rs, rowNumber) -> rs.getObject("id", UUID.class),
			OffsetDateTime.now(ZoneOffset.UTC),
			BATCH_SIZE
		);
	}

	private boolean processReminder(UUID reminderId) {
		try {
			Boolean processed = transactionTemplate.execute(status -> processReminderInTransaction(reminderId));
			return Boolean.TRUE.equals(processed);
		}
		catch (RuntimeException exception) {
			log.warn("Failed to process reminder {}", reminderId, exception);
			return false;
		}
	}

	private boolean processReminderInTransaction(UUID reminderId) {
		ReminderTarget target = lockReminderTarget(reminderId);
		if (target == null || !"SCHEDULED".equals(target.status())) {
			return false;
		}
		if (!notificationExists(target.reminderId())) {
			publishNotification(target);
		}
		markSent(target.reminderId());
		return true;
	}

	private ReminderTarget lockReminderTarget(UUID reminderId) {
		return jdbcTemplate.query(
				"""
				select
					rs.id as reminder_id,
					rs.user_id,
					rs.item_id,
					rs.decision_id,
					rs.status,
					si.title as item_title
				from reminder_schedules rs
				join purchase_decisions pd on pd.id = rs.decision_id
				join saved_items si on si.id = rs.item_id
				join users u on u.id = rs.user_id
				left join user_notification_settings uns on uns.user_id = rs.user_id
				where rs.id = ?
				  and rs.reminder_type = 'REGRET_CHECK_7_DAYS'
				  and u.status = 'ACTIVE'
				  and coalesce(uns.regret_reminder_enabled, true) = true
				for update of rs
				""",
				this::mapReminderTarget,
				reminderId
			)
			.stream()
			.findFirst()
			.orElse(null);
	}

	private boolean notificationExists(UUID reminderId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"""
			select exists(
				select 1
				from notifications
				where reminder_id = ?
				  and notification_type = 'REGRET_CHECK_READY'
			)
			""",
			Boolean.class,
			reminderId
		);
		return Boolean.TRUE.equals(exists);
	}

	private void publishNotification(ReminderTarget target) {
		notificationPublisher.publish(new NotificationPublishRequest(
			target.userId(),
			"REGRET_CHECK_READY",
			"위시 돌아보기",
			NotificationMessages.regretCheckReadyBody(target.itemTitle()),
			target.itemId(),
			target.decisionId(),
			target.reminderId(),
			"/reflections?decisionId=" + target.decisionId(),
			"regret:first:" + target.reminderId(),
			NotificationChannels.CONSUMPTION_MANAGEMENT
		));
	}

	private void markSent(UUID reminderId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			update reminder_schedules
			set status = 'SENT',
				sent_at = ?,
				updated_at = ?
			where id = ?
			  and status = 'SCHEDULED'
			""",
			now,
			now,
			reminderId
		);
	}

	private ReminderTarget mapReminderTarget(ResultSet rs, int rowNumber) throws SQLException {
		return new ReminderTarget(
			rs.getObject("reminder_id", UUID.class),
			rs.getObject("user_id", UUID.class),
			rs.getObject("item_id", UUID.class),
			rs.getObject("decision_id", UUID.class),
			rs.getString("status"),
			rs.getString("item_title")
		);
	}

	private record ReminderTarget(
		UUID reminderId,
		UUID userId,
		UUID itemId,
		UUID decisionId,
		String status,
		String itemTitle
	) {
	}

}
