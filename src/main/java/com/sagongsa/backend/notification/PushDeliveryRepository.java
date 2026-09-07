package com.sagongsa.backend.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
class PushDeliveryRepository {
	private final JdbcTemplate jdbcTemplate;
	PushDeliveryRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

	public boolean pushEnabled(UUID userId) {
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

	public List<PushTokenTarget> activeTokens(UUID userId) {
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

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deactivateToken(String pushToken) {
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

	private PushTokenTarget mapPushTokenTarget(ResultSet rs, int rowNumber) throws SQLException {
		return new PushTokenTarget(rs.getString("push_token"));
	}
	record PushTokenTarget(String pushToken) {
	}
}
