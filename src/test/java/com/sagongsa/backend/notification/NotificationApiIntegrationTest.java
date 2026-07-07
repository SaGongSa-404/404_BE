package com.sagongsa.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiIntegrationTest extends PostgreSqlContainerTest {

	private static final String USER_ID_HEADER = "X-User-Id";

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
		insertNotification(userId, "WISHLIST_REMINDER", false);
		insertNotification(userId, "BUDGET_WARNING", false);
		insertNotification(userId, "SOCIAL_VOTE", true);
		insertNotification(otherUserId, "REGRET_CHECK_READY", false);

		mockMvc.perform(patch("/api/v1/notifications/read-all")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.updatedCount").value(2));

		assertThat(queryInteger("select count(*) from notifications where user_id = ? and is_read = false", userId))
			.isZero();
		assertThat(queryInteger("select count(*) from notifications where user_id = ? and is_read = false", otherUserId))
			.isOne();
	}

	private UUID createUser() {
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

	private void insertNotification(UUID userId, String type, boolean read) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime readAt = read ? now : null;
		jdbcTemplate.update(
			"""
			insert into notifications (
				id, user_id, notification_type, title, body, target_path,
				is_read, read_at, created_at, updated_at
			)
			values (?, ?, ?, '알림', '내용', '/notifications', ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			type,
			read,
			readAt,
			now,
			now
		);
	}

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}
}
