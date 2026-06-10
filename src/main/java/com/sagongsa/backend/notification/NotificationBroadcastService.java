package com.sagongsa.backend.notification;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationBroadcastService {

	private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

	private final JdbcTemplate jdbcTemplate;
	private final NotificationPublisher notificationPublisher;

	public NotificationBroadcastService(JdbcTemplate jdbcTemplate, NotificationPublisher notificationPublisher) {
		this.jdbcTemplate = jdbcTemplate;
		this.notificationPublisher = notificationPublisher;
	}

	public int publishAppUpdate(String version, String playStoreUrl) {
		int createdCount = 0;
		for (UUID userId : activeCompletedUserIds()) {
			NotificationPublishResult result = notificationPublisher.publish(new NotificationPublishRequest(
				userId,
				"APP_UPDATE",
				"앱 업데이트",
				"✨위굴이 업데이트됐어요! 새로운 기능을 확인해보세요",
				null,
				null,
				null,
				playStoreUrl,
				"version:" + version,
				NotificationChannels.SERVICE_NOTICE
			));
			if (result.created()) {
				createdCount++;
			}
		}
		return createdCount;
	}

	public int publishMaintenanceNotice(OffsetDateTime startsAt, int durationHours) {
		int createdCount = 0;
		String body = maintenanceBody(startsAt, durationHours);
		String dedupeKey = "maintenance:" + startsAt.toInstant() + ":" + durationHours;
		for (UUID userId : activeCompletedUserIds()) {
			NotificationPublishResult result = notificationPublisher.publish(new NotificationPublishRequest(
				userId,
				"MAINTENANCE_NOTICE",
				"점검 공지",
				body,
				null,
				null,
				null,
				null,
				dedupeKey,
				NotificationChannels.SERVICE_NOTICE
			));
			if (result.created()) {
				createdCount++;
			}
		}
		return createdCount;
	}

	private List<UUID> activeCompletedUserIds() {
		return jdbcTemplate.query(
			"""
			select id
			from users
			where status = 'ACTIVE'
			  and onboarding_status = 'COMPLETED'
			order by created_at asc, id asc
			""",
			(rs, rowNumber) -> rs.getObject("id", UUID.class)
		);
	}

	private String maintenanceBody(OffsetDateTime startsAt, int durationHours) {
		var koreaStartsAt = startsAt.atZoneSameInstant(KOREA_ZONE_ID);
		int hour = koreaStartsAt.getHour();
		String meridiem = hour < 12 ? "오전" : "오후";
		int displayHour = hour % 12 == 0 ? 12 : hour % 12;
		return "🔧%s %d시부터 %d시간 동안 점검이 예정되어 있어요."
			.formatted(meridiem, displayHour, durationHours);
	}
}
