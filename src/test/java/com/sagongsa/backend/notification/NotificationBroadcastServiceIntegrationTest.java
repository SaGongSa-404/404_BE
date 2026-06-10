package com.sagongsa.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
	"app.notification.reminder-worker.enabled=false",
	"app.notification.trigger-worker.enabled=false"
})
class NotificationBroadcastServiceIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private NotificationBroadcastService notificationBroadcastService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockBean
	private PushNotificationService pushNotificationService;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void publishesAppUpdateToActiveCompletedUsersOncePerVersion() {
		createUser("ACTIVE", "COMPLETED");
		createUser("ACTIVE", "COMPLETED");
		createUser("ACTIVE", "NOT_STARTED");

		int createdCount = notificationBroadcastService.publishAppUpdate(
			"1.2.3",
			"https://play.google.com/store/apps/details?id=com.sagongsa.wigul"
		);
		int duplicateCount = notificationBroadcastService.publishAppUpdate(
			"1.2.3",
			"https://play.google.com/store/apps/details?id=com.sagongsa.wigul"
		);

		assertThat(createdCount).isEqualTo(2);
		assertThat(duplicateCount).isZero();
		assertThat(queryInteger("select count(*) from notifications where notification_type = 'APP_UPDATE'"))
			.isEqualTo(2);
		assertThat(queryString("select channel_id from notifications where notification_type = 'APP_UPDATE' limit 1"))
			.isEqualTo("service_notice");
		assertThat(queryString("select target_path from notifications where notification_type = 'APP_UPDATE' limit 1"))
			.isEqualTo("https://play.google.com/store/apps/details?id=com.sagongsa.wigul");
		verify(pushNotificationService, times(2)).send(any(NotificationPushMessage.class));
	}

	@Test
	void publishesMaintenanceNoticeToActiveCompletedUsers() {
		createUser("ACTIVE", "COMPLETED");
		createUser("WITHDRAWN", "COMPLETED");

		int createdCount = notificationBroadcastService.publishMaintenanceNotice(
			OffsetDateTime.of(2026, 6, 10, 7, 0, 0, 0, ZoneOffset.UTC),
			2
		);

		assertThat(createdCount).isEqualTo(1);
		assertThat(queryString("select body from notifications where notification_type = 'MAINTENANCE_NOTICE'"))
			.isEqualTo("🔧오후 4시부터 2시간 동안 점검이 예정되어 있어요.");
		assertThat(queryString("select channel_id from notifications where notification_type = 'MAINTENANCE_NOTICE'"))
			.isEqualTo("service_notice");
	}

	private UUID createUser(String status, String onboardingStatus) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime withdrawnAt = "WITHDRAWN".equals(status) ? now : null;
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, withdrawn_at, created_at, updated_at)
			values (?, ?, ?, ?, ?, ?)
			""",
			userId,
			status,
			onboardingStatus,
			withdrawnAt,
			now,
			now
		);
		return userId;
	}

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	private String queryString(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}
}
