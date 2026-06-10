package com.sagongsa.backend.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushNotificationService {

	private final JdbcTemplate jdbcTemplate;
	private final FcmMessageSender fcmMessageSender;

	public PushNotificationService(JdbcTemplate jdbcTemplate, FcmMessageSender fcmMessageSender) {
		this.jdbcTemplate = jdbcTemplate;
		this.fcmMessageSender = fcmMessageSender;
	}

	@Transactional
	public void send(NotificationPushMessage message) {
		if (!pushEnabled(message.userId())) {
			return;
		}

		List<PushTokenTarget> tokens = activeTokens(message.userId());
		for (PushTokenTarget token : tokens) {
			FcmSendResult result = fcmMessageSender.send(new FcmSendRequest(
				token.pushToken(),
				message.title(),
				message.body(),
				payload(message),
				message.channelId()
			));
			if (result.invalidToken()) {
				deactivateToken(token.pushToken());
			}
		}
	}

	private boolean pushEnabled(UUID userId) {
		Boolean enabled = jdbcTemplate.queryForObject(
			"""
			select coalesce(up.notification_enabled, true)
			       and coalesce(uns.push_enabled, true) as enabled
			from users u
			left join user_profiles up on up.user_id = u.id
			left join user_notification_settings uns on uns.user_id = u.id
			where u.id = ?
			""",
			Boolean.class,
			userId
		);
		return Boolean.TRUE.equals(enabled);
	}

	private List<PushTokenTarget> activeTokens(UUID userId) {
		return jdbcTemplate.query(
			"""
			select push_token
			from device_push_tokens
			where user_id = ?
			  and is_active = true
			order by updated_at desc, id desc
			""",
			this::mapPushTokenTarget,
			userId
		);
	}

	private void deactivateToken(String pushToken) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			update device_push_tokens
			set is_active = false,
				disabled_at = coalesce(disabled_at, ?),
				updated_at = ?
			where push_token = ?
			""",
			now,
			now,
			pushToken
		);
	}

	private Map<String, String> payload(NotificationPushMessage message) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put("notificationId", message.notificationId().toString());
		data.put("notificationType", message.notificationType());
		if (message.targetPath() != null && !message.targetPath().isBlank()) {
			data.put("targetPath", message.targetPath());
		}
		if (message.channelId() != null && !message.channelId().isBlank()) {
			data.put("channelId", message.channelId());
		}
		return data;
	}

	private PushTokenTarget mapPushTokenTarget(ResultSet rs, int rowNumber) throws SQLException {
		return new PushTokenTarget(rs.getString("push_token"));
	}

	private record PushTokenTarget(String pushToken) {
	}
}
