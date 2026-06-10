package com.sagongsa.backend.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

	private static final int RETENTION_MONTHS = 1;

	private final JdbcTemplate jdbcTemplate;

	public NotificationService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public List<NotificationResponse> list(UUID userId, boolean unreadOnly) {
		requireUsableUser(userId);
		String unreadFilter = unreadOnly ? "and is_read = false" : "";
		OffsetDateTime cutoff = retentionCutoff();
		return jdbcTemplate.query(
			"""
			select id, notification_type, title, body, item_id, decision_id, reminder_id,
			       target_path, is_read, read_at, created_at
			from notifications
			where user_id = ?
			  and created_at >= ?
			%s
			order by created_at desc, id desc
			""".formatted(unreadFilter),
			this::mapNotification,
			userId,
			cutoff
		);
	}

	@Transactional
	public NotificationResponse markAsRead(UUID userId, UUID notificationId) {
		requireUsableUser(userId);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		int updated = jdbcTemplate.update(
			"""
			update notifications
			set is_read = true,
				read_at = coalesce(read_at, ?),
				updated_at = ?
			where user_id = ?
			  and id = ?
			  and created_at >= ?
			""",
			now,
			now,
			userId,
			notificationId,
			retentionCutoff()
		);
		if (updated == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification was not found.");
		}
		return findById(userId, notificationId);
	}

	private NotificationResponse findById(UUID userId, UUID notificationId) {
		try {
			return jdbcTemplate.queryForObject(
				"""
				select id, notification_type, title, body, item_id, decision_id, reminder_id,
				       target_path, is_read, read_at, created_at
				from notifications
				where user_id = ?
				  and id = ?
				  and created_at >= ?
				""",
				this::mapNotification,
				userId,
				notificationId,
				retentionCutoff()
			);
		}
		catch (EmptyResultDataAccessException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification was not found.");
		}
	}

	private void requireUsableUser(UUID userId) {
		try {
			UserState user = jdbcTemplate.queryForObject(
				"select status, onboarding_status from users where id = ?",
				(rs, rowNumber) -> new UserState(rs.getString("status"), rs.getString("onboarding_status")),
				userId
			);
			if (!Objects.equals(user.status(), "ACTIVE") || !Objects.equals(user.onboardingStatus(), "COMPLETED")) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Notifications can be used only after onboarding.");
			}
		}
		catch (EmptyResultDataAccessException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found.");
		}
	}

	private NotificationResponse mapNotification(ResultSet rs, int rowNumber) throws SQLException {
		return new NotificationResponse(
			rs.getObject("id", UUID.class),
			rs.getString("notification_type"),
			rs.getString("title"),
			rs.getString("body"),
			rs.getObject("item_id", UUID.class),
			rs.getObject("decision_id", UUID.class),
			rs.getObject("reminder_id", UUID.class),
			rs.getString("target_path"),
			rs.getBoolean("is_read"),
			readInstant(rs, "read_at"),
			readInstant(rs, "created_at")
		);
	}

	private Instant readInstant(ResultSet rs, String columnName) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(columnName);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private OffsetDateTime retentionCutoff() {
		return OffsetDateTime.now(ZoneOffset.UTC).minusMonths(RETENTION_MONTHS);
	}

	private record UserState(String status, String onboardingStatus) {
	}
}
