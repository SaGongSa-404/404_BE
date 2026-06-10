package com.sagongsa.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
	"app.admin.notification-token=test-admin-token",
	"app.notification.reminder-worker.enabled=false",
	"app.notification.trigger-worker.enabled=false"
})
@AutoConfigureMockMvc
class AdminNotificationControllerIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockBean
	private PushNotificationService pushNotificationService;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void publishesAppUpdateWithAdminToken() throws Exception {
		UUID userId = createUser("ACTIVE", "COMPLETED");
		createUser("ACTIVE", "NOT_STARTED");

		mockMvc.perform(post("/api/v1/admin/notifications/app-update")
				.header("X-Admin-Token", "test-admin-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "version": "1.2.3",
					  "playStoreUrl": "https://play.google.com/store/apps/details?id=com.sagongsa.wigul"
					}
					"""))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.createdCount").value(1));

		assertThat(queryInteger(
			"""
			select count(*)
			from notifications
			where user_id = ?
			  and notification_type = 'APP_UPDATE'
			  and dedupe_key = 'version:1.2.3'
			""",
			userId
		)).isEqualTo(1);
	}

	@Test
	void publishesMaintenanceNoticeWithAdminRole() throws Exception {
		createUser("ACTIVE", "COMPLETED");

		mockMvc.perform(post("/api/v1/admin/notifications/maintenance")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "startsAt": "2026-06-10T07:00:00Z",
					  "durationHours": 2
					}
					"""))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.createdCount").value(1));

		assertThat(queryString("select body from notifications where notification_type = 'MAINTENANCE_NOTICE'"))
			.isEqualTo("🔧오후 4시부터 2시간 동안 점검이 예정되어 있어요.");
	}

	@Test
	void rejectsAdminNotificationWithoutAdminTokenOrRole() throws Exception {
		createUser("ACTIVE", "COMPLETED");

		mockMvc.perform(post("/api/v1/admin/notifications/app-update")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "version": "1.2.3",
					  "playStoreUrl": "https://play.google.com/store/apps/details?id=com.sagongsa.wigul"
					}
					"""))
			.andExpect(status().isForbidden());

		assertThat(queryInteger("select count(*) from notifications")).isZero();
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
