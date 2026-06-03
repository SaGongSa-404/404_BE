package com.sagongsa.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class PushNotificationServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RecordingFcmMessageSender fcmMessageSender;
	private PushNotificationService pushNotificationService;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
		fcmMessageSender = new RecordingFcmMessageSender();
		pushNotificationService = new PushNotificationService(jdbcTemplate, fcmMessageSender);
	}

	@Test
	void sendsActiveTokensWithNotificationIdPayload() {
		UUID userId = insertUser();
		insertPushToken(userId, "active-token");
		UUID notificationId = UUID.randomUUID();

		pushNotificationService.send(message(userId, notificationId));

		assertThat(fcmMessageSender.requests).hasSize(1);
		FcmSendRequest request = fcmMessageSender.requests.getFirst();
		assertThat(request.token()).isEqualTo("active-token");
		assertThat(request.data()).containsEntry("notificationId", notificationId.toString());
		assertThat(request.data()).containsEntry("notificationType", "REGRET_CHECK_READY");
	}

	@Test
	void skipsFcmWhenNotificationSettingDisabled() {
		UUID userId = insertUser();
		insertProfile(userId, false);
		insertPushToken(userId, "disabled-user-token");

		pushNotificationService.send(message(userId, UUID.randomUUID()));

		assertThat(fcmMessageSender.requests).isEmpty();
		assertTokenActive("disabled-user-token", true);
	}

	@Test
	void deactivatesInvalidTokenAfterFcmFailure() {
		UUID userId = insertUser();
		insertPushToken(userId, "invalid-token");
		fcmMessageSender.nextResult = FcmSendResult.invalid();

		pushNotificationService.send(message(userId, UUID.randomUUID()));

		assertThat(fcmMessageSender.requests).hasSize(1);
		assertTokenActive("invalid-token", false);
	}

	private NotificationPushMessage message(UUID userId, UUID notificationId) {
		return new NotificationPushMessage(
			userId,
			notificationId,
			"REGRET_CHECK_READY",
			"구매 후 7일이 지났어요",
			"[상품] 지금도 잘 샀다고 생각하나요?",
			"/reflections?decisionId=" + UUID.randomUUID()
		);
	}

	private UUID insertUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, created_at, updated_at)
			values (?, 'ACTIVE', 'COMPLETED', ?, ?)
			""",
			userId,
			now,
			now
		);
		return userId;
	}

	private void insertProfile(UUID userId, boolean notificationEnabled) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into user_profiles (
				user_id, nickname, mascot_name, timezone, notification_enabled, created_at, updated_at
			)
			values (?, '너굴이', '너구리', 'Asia/Seoul', ?, ?, ?)
			""",
			userId,
			notificationEnabled,
			now,
			now
		);
	}

	private void insertPushToken(UUID userId, String token) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into device_push_tokens (
				id, user_id, platform, push_token, is_active, created_at, updated_at
			)
			values (?, ?, 'ANDROID', ?, true, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			token,
			now,
			now
		);
	}

	private void assertTokenActive(String token, boolean active) {
		Boolean actual = jdbcTemplate.queryForObject(
			"select is_active from device_push_tokens where push_token = ?",
			Boolean.class,
			token
		);
		assertThat(actual).isEqualTo(active);
	}

	private static class RecordingFcmMessageSender implements FcmMessageSender {

		private final List<FcmSendRequest> requests = new ArrayList<>();
		private FcmSendResult nextResult = FcmSendResult.success();

		@Override
		public FcmSendResult send(FcmSendRequest request) {
			requests.add(request);
			return nextResult;
		}
	}
}
