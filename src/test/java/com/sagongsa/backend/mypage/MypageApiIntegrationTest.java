package com.sagongsa.backend.mypage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MypageApiIntegrationTest extends PostgreSqlContainerTest {

	private static final String BASE = "/api/v1/users/me";
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── GET /api/v1/users/me ─────────────────────────────────────────────────

	@Test
	void 내_프로필_조회_200() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(get(BASE).header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(userId.toString()))
			.andExpect(jsonPath("$.nickname").value("너굴이"))
			.andExpect(jsonPath("$.mascotName").value("너구리"));
	}

	@Test
	void 존재하지_않는_유저_프로필_조회_404() throws Exception {
		mockMvc.perform(get(BASE).header("X-User-Id", UUID.randomUUID()))
			.andExpect(status().isNotFound());
	}

	// ── PATCH /api/v1/users/me/profile ───────────────────────────────────────

	@Test
	void 프로필_수정_200() throws Exception {
		UUID userId = insertUser("기존닉네임", "기존이름");

		mockMvc.perform(patch(BASE + "/profile")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"nickname":"새닉네임","raccoonName":"새이름"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("새닉네임"))
			.andExpect(jsonPath("$.mascotName").value("새이름"));
	}

	@Test
	void 닉네임_10자_초과_400() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(patch(BASE + "/profile")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"nickname":"열한글자닉네임초과123"}
					"""))
			.andExpect(status().isBadRequest());
	}

	// ── PATCH /api/v1/users/me/budget ────────────────────────────────────────

	@Test
	void 예산_수정_200() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(patch(BASE + "/budget")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"monthlyBudget":400000}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.monthlyBudget").value(400_000));
	}

	@Test
	void 예산_0원_미만_400() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(patch(BASE + "/budget")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"monthlyBudget":0}
					"""))
			.andExpect(status().isBadRequest());
	}

	// ── GET /api/v1/users/me/notification-settings ───────────────────────────

	@Test
	void 알림_설정_조회_200() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(get(BASE + "/notification-settings").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notificationEnabled").isBoolean());
	}

	// ── PATCH /api/v1/users/me/notification-settings ─────────────────────────

	@Test
	void 알림_설정_변경_200() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(patch(BASE + "/notification-settings")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"notificationEnabled":false}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notificationEnabled").value(false));
	}

	// ── DELETE /api/v1/users/me ──────────────────────────────────────────────

	@Test
	void 회원탈퇴_204() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(delete(BASE).header("X-User-Id", userId))
			.andExpect(status().isNoContent());

		String status = jdbcTemplate.queryForObject(
			"SELECT status FROM users WHERE id = ?", String.class, userId);
		org.assertj.core.api.Assertions.assertThat(status).isEqualTo("WITHDRAWN");
	}

	@Test
	void 탈퇴_후_유저_상태_WITHDRAWN() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");
		mockMvc.perform(delete(BASE).header("X-User-Id", userId))
			.andExpect(status().isNoContent());

		String status = jdbcTemplate.queryForObject(
			"SELECT status FROM users WHERE id = ?", String.class, userId);
		org.assertj.core.api.Assertions.assertThat(status).isEqualTo("WITHDRAWN");
	}

	// ── GET /api/v1/users/me/stats/months ────────────────────────────────────

	@Test
	void 이용_가능_월_목록_조회_200() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");
		insertBudgetCycle(userId, "2026-03", 300_000, 0);

		mockMvc.perform(get(BASE + "/stats/months").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.months").isArray())
			.andExpect(jsonPath("$.currentMonth").isNotEmpty());
	}

	// ── GET /api/v1/users/me/stats ───────────────────────────────────────────

	@Test
	void 통계_조회_200() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");
		String yearMonth = YearMonth.now(KST).toString();
		insertBudgetCycle(userId, yearMonth, 300_000, 50_000);

		mockMvc.perform(get(BASE + "/stats")
				.header("X-User-Id", userId)
				.param("yearMonth", yearMonth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.yearMonth").value(yearMonth))
			.andExpect(jsonPath("$.budgetAmount").value(300_000));
	}

	// ── GET /api/v1/users/me/wishes/history ──────────────────────────────────

	@Test
	void 위시_히스토리_조회_200() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(get(BASE + "/wishes/history").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.wishes").isArray());
	}

	@Test
	void 위시_히스토리_페이지_음수_400() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(get(BASE + "/wishes/history")
				.header("X-User-Id", userId)
				.param("page", "-1"))
			.andExpect(status().isBadRequest());
	}

	// ── GET /api/v1/users/me/posts ───────────────────────────────────────────

	@Test
	void 내_게시글_목록_조회_200() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");
		insertPost(userId);

		mockMvc.perform(get(BASE + "/posts").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts").isArray())
			.andExpect(jsonPath("$.posts[0].id").isNotEmpty());
	}

	// ── GET /api/v1/users/me/votes ───────────────────────────────────────────

	@Test
	void 투표한_게시글_목록_조회_200() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(get(BASE + "/votes").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts").isArray());
	}

	// ── 헬퍼 ─────────────────────────────────────────────────────────────────

	private UUID insertUser(String nickname, String mascotName) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO users (id, status, onboarding_status, created_at, updated_at) VALUES (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId, now, now);
		jdbcTemplate.update(
			"INSERT INTO user_profiles (user_id, nickname, mascot_name, timezone, created_at, updated_at) VALUES (?, ?, ?, 'Asia/Seoul', ?, ?)",
			userId, nickname, mascotName, now, now);
		return userId;
	}

	private void insertBudgetCycle(UUID userId, String yearMonth, int monthlyBudget, int spentAmount) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO budget_cycles (id, user_id, year_month, monthly_budget_amount, spent_amount, warning_threshold_rate, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 80.00, ?, ?)",
			UUID.randomUUID(), userId, yearMonth, monthlyBudget, spentAmount, now, now);
	}

	private void insertPost(UUID userId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO feed_posts (id, user_id, title, body, go_count, stop_count, created_at, updated_at) VALUES (?, ?, '테스트 게시글', '내용', 0, 0, ?, ?)",
			UUID.randomUUID(), userId, now, now);
	}
}
