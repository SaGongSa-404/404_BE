package com.sagongsa.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiIntegrationTest extends PostgreSqlContainerTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final OffsetDateTime NOW = OffsetDateTime.parse("2024-03-31T15:30:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void markAllAsReadReturnsUpdatedCount() throws Exception {
		UUID userId = createUser();
		UUID otherUserId = createUser();
		insertNotification(userId, "WISHLIST_REMINDER", false, NOW);
		insertNotification(userId, "BUDGET_WARNING", false, NOW);
		insertNotification(userId, "SOCIAL_VOTE", true, NOW);
		insertNotification(otherUserId, "REGRET_CHECK_READY", false, NOW);

		mockMvc.perform(patch("/api/v1/notifications/read-all")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.updatedCount").value(2));

		assertThat(queryInteger("select count(*) from notifications where user_id = ? and is_read = false", userId))
			.isZero();
		assertThat(queryInteger("select count(*) from notifications where user_id = ? and is_read = false", otherUserId))
			.isOne();
	}

	@Test
	void homeAndNotificationEndpointsUseTheSameUtcCalendarMonthBoundary() throws Exception {
		UUID userId = createUser();
		OffsetDateTime cutoff = OffsetDateTime.parse("2024-02-29T15:30:00Z");
		UUID expired = insertNotification(userId, "WISHLIST_REMINDER", false, cutoff.minusNanos(1_000));
		UUID atCutoff = insertNotification(userId, "BUDGET_WARNING", false, cutoff);
		UUID recent = insertNotification(userId, "SOCIAL_VOTE", false, cutoff.plusSeconds(1));

		mockMvc.perform(get("/api/v1/home/summary").header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notifications.unreadCount").value(2))
			.andExpect(jsonPath("$.notifications.latestNotifications.length()").value(2))
			.andExpect(jsonPath("$.notifications.latestNotifications[0].id").value(recent.toString()))
			.andExpect(jsonPath("$.notifications.latestNotifications[1].id").value(atCutoff.toString()));

		mockMvc.perform(get("/api/v1/notifications").header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(recent.toString()))
			.andExpect(jsonPath("$[1].id").value(atCutoff.toString()));

		mockMvc.perform(patch("/api/v1/notifications/{id}/read", expired).header(USER_ID_HEADER, userId))
			.andExpect(status().isNotFound());
		mockMvc.perform(patch("/api/v1/notifications/{id}/read", atCutoff).header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.read").value(true));
		mockMvc.perform(patch("/api/v1/notifications/read-all").header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.updatedCount").value(1));
		assertThat(queryInteger("select count(*) from notifications where id = ? and is_read = false", expired))
			.isOne();
	}

	private UUID createUser() {
		UUID userId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, created_at, updated_at)
			values (?, 'ACTIVE', 'COMPLETED', ?, ?)
			""",
			userId,
			NOW,
			NOW
		);
		return userId;
	}

	private UUID insertNotification(UUID userId, String type, boolean read, OffsetDateTime createdAt) {
		UUID notificationId = UUID.randomUUID();
		OffsetDateTime readAt = read ? createdAt : null;
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

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	@TestConfiguration
	static class FixedClockConfiguration {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul"));
		}
	}
}
