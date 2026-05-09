package com.sagongsa.backend.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReminderNotificationWorker {

	private static final int BATCH_SIZE = 50;

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;

	public ReminderNotificationWorker(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
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

	private List<UUID> findDueReminderIds() {
		return jdbcTemplate.query(
			"""
			select id
			from reminder_schedules
			where reminder_type = 'REGRET_CHECK_7_DAYS'
			  and status = 'SCHEDULED'
			  and scheduled_for <= ?
			order by scheduled_for asc, id asc
			limit ?
			for update skip locked
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
			return false;
		}
	}

	private boolean processReminderInTransaction(UUID reminderId) {
		ReminderTarget target = lockReminderTarget(reminderId);
		if (target == null || !"SCHEDULED".equals(target.status())) {
			return false;
		}
		if (!notificationExists(target.reminderId())) {
			insertNotification(target);
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

	private void insertNotification(ReminderTarget target) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		try {
			jdbcTemplate.update(
				"""
				insert into notifications (
					id, user_id, notification_type, title, body,
					item_id, decision_id, reminder_id, target_path,
					is_read, created_at, updated_at
				)
				values (?, ?, 'REGRET_CHECK_READY', ?, ?, ?, ?, ?, ?, false, ?, ?)
				""",
				UUID.randomUUID(),
				target.userId(),
				"구매 후 7일이 지났어요",
				"[%s] 지금도 잘 샀다고 생각하나요?".formatted(target.itemTitle()),
				target.itemId(),
				target.decisionId(),
				target.reminderId(),
				"/reflections?decisionId=" + target.decisionId(),
				now,
				now
			);
		}
		catch (DuplicateKeyException ignored) {
			// Another worker inserted the reminder notification first.
		}
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
