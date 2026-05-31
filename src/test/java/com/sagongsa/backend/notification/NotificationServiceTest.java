package com.sagongsa.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class NotificationServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void listsUnreadNotificationsNewestFirst() {
		UUID userId = createUser("ACTIVE", "COMPLETED");
		UUID oldUnreadId = insertNotification(userId, "WISHLIST_REMINDER", false, OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
		insertNotification(userId, "SOCIAL_VOTE", true, OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
		UUID newUnreadId = insertNotification(userId, "BUDGET_WARNING", false, OffsetDateTime.now(ZoneOffset.UTC));

		List<NotificationResponse> responses = notificationService.list(userId, true);

		assertThat(responses).extracting(NotificationResponse::id)
			.containsExactly(newUnreadId, oldUnreadId);
		assertThat(responses).allSatisfy(response -> assertThat(response.read()).isFalse());
	}

	@Test
	void markAsReadSetsReadAtOnlyOnce() {
		UUID userId = createUser("ACTIVE", "COMPLETED");
		UUID notificationId = insertNotification(userId, "REGRET_CHECK_READY", false, OffsetDateTime.now(ZoneOffset.UTC));

		NotificationResponse first = notificationService.markAsRead(userId, notificationId);
		NotificationResponse second = notificationService.markAsRead(userId, notificationId);

		assertThat(first.read()).isTrue();
		assertThat(first.readAt()).isNotNull();
		assertThat(second.readAt()).isEqualTo(first.readAt());
		assertThat(queryBoolean("select is_read from notifications where id = ?", notificationId)).isTrue();
	}

	@Test
	void rejectsNotificationsBeforeOnboardingCompletion() {
		UUID userId = createUser("ACTIVE", "NOT_STARTED");

		assertThatThrownBy(() -> notificationService.list(userId, false))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.FORBIDDEN));
	}

	@Test
	void throwsNotFoundWhenMarkingAnotherUsersNotification() {
		UUID ownerId = createUser("ACTIVE", "COMPLETED");
		UUID otherUserId = createUser("ACTIVE", "COMPLETED");
		UUID notificationId = insertNotification(ownerId, "WISHLIST_REMINDER", false, OffsetDateTime.now(ZoneOffset.UTC));

		assertThatThrownBy(() -> notificationService.markAsRead(otherUserId, notificationId))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND));
	}

	private UUID createUser(String status, String onboardingStatus) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, created_at, updated_at)
			values (?, ?, ?, ?, ?)
			""",
			userId,
			status,
			onboardingStatus,
			now,
			now
		);
		return userId;
	}

	private UUID insertNotification(UUID userId, String type, boolean read, OffsetDateTime createdAt) {
		UUID notificationId = UUID.randomUUID();
		OffsetDateTime readAt = read ? createdAt.plusMinutes(1) : null;
		jdbcTemplate.update(
			"""
			insert into notifications (
				id, user_id, notification_type, title, body, target_path,
				is_read, read_at, created_at, updated_at
			)
			values (?, ?, ?, '알림', '내용', '/notifications', ?, ?, ?, ?)
			""",
			notificationId,
			userId,
			type,
			read,
			readAt,
			createdAt,
			createdAt
		);
		return notificationId;
	}

	private Boolean queryBoolean(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Boolean.class, args);
	}
}
