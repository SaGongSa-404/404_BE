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
	void 닉네임_9자_400() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(patch(BASE + "/profile")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"nickname":"123456789"}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 닉네임_1자_400() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(patch(BASE + "/profile")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"nickname":"a"}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 닉네임_공백_400() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");

		mockMvc.perform(patch(BASE + "/profile")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"nickname":" 닉네임"}
					"""))
			.andExpect(status().isBadRequest());
	}

	// ── PATCH /api/v1/users/me/budget ────────────────────────────────────────

	@Test
	void 예산_수정_200() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");
		String yearMonth = YearMonth.now(KST).toString();

		mockMvc.perform(patch(BASE + "/budget")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"monthlyBudget":400000}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.monthlyBudget").value(400_000));

		mockMvc.perform(get(BASE + "/stats")
				.header("X-User-Id", userId)
				.param("yearMonth", yearMonth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budgetAmount").value(400_000));
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
	void 회원탈퇴_결정_셀프체크_이력이_있어도_204() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");
		String yearMonth = YearMonth.now(KST).toString();
		UUID decisionId = insertDecision(userId, "GO", 100_000, yearMonth);
		UUID responseSetId = insertSelfCheckResponseSet(decisionId);

		mockMvc.perform(delete(BASE).header("X-User-Id", userId))
			.andExpect(status().isNoContent());

		Integer responseSetCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM self_check_response_sets WHERE id = ?", Integer.class, responseSetId);
		Integer decisionCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM purchase_decisions WHERE id = ?", Integer.class, decisionId);
		org.assertj.core.api.Assertions.assertThat(responseSetCount).isZero();
		org.assertj.core.api.Assertions.assertThat(decisionCount).isZero();
	}

	@Test
	void 탈퇴_후_프로필_삭제_확인() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");
		mockMvc.perform(delete(BASE).header("X-User-Id", userId))
			.andExpect(status().isNoContent());

		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_profiles WHERE user_id = ?", Integer.class, userId);
		org.assertj.core.api.Assertions.assertThat(count).isZero();
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
		insertBudgetCycle(userId, yearMonth, 300_000, 0);
		insertDecision(userId, "GO", 100_000, yearMonth);
		insertDecision(userId, "STOP", 50_000, yearMonth);

		mockMvc.perform(get(BASE + "/stats")
				.header("X-User-Id", userId)
				.param("yearMonth", yearMonth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.yearMonth").value(yearMonth))
			.andExpect(jsonPath("$.budgetAmount").value(300_000))
			.andExpect(jsonPath("$.spentAmount").value(100_000))
			.andExpect(jsonPath("$.restrainedAmount").value(50_000))
			.andExpect(jsonPath("$.boughtCount").value(1))
			.andExpect(jsonPath("$.restrainedCount").value(1))
			.andExpect(jsonPath("$.usageRate").value(33.3))
			.andExpect(jsonPath("$.categorySpendAmounts[0].category").value("DIGITAL"))
			.andExpect(jsonPath("$.categorySpendAmounts[0].amount").value(100_000))
			.andExpect(jsonPath("$.rationalChoiceRate").value(100.0))
			.andExpect(jsonPath("$.irrationalChoiceCount").value(0));
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
	void 위시_히스토리_평가_결과_조회_200() throws Exception {
		UUID userId = insertUser("너굴이", "너구리");
		String yearMonth = YearMonth.now(KST).toString();
		UUID decisionId = insertDecision(userId, "GO", 100_000, yearMonth);
		insertReflection(userId, decisionId, 5, "NONE", true, "잘 쓰고 있어요");

		mockMvc.perform(get(BASE + "/wishes/history")
				.header("X-User-Id", userId)
				.param("status", "GO")
				.param("yearMonth", yearMonth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.wishes[0].decisionId").value(decisionId.toString()))
			.andExpect(jsonPath("$.wishes[0].reflection.satisfactionScore").value(5))
			.andExpect(jsonPath("$.wishes[0].reflection.regretLevel").value("NONE"))
			.andExpect(jsonPath("$.wishes[0].reflection.stillUsing").value(true));
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
		UUID postId = insertPost(userId);
		insertVote(userId, postId, "GO");

		mockMvc.perform(get(BASE + "/votes").header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts").isArray())
			.andExpect(jsonPath("$.posts[0].id").value(postId.toString()))
			.andExpect(jsonPath("$.posts[0].myVote").value("GO"));
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

	private UUID insertPost(UUID userId) {
		UUID postId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO feed_posts (id, user_id, title, body, go_count, stop_count, created_at, updated_at) VALUES (?, ?, '테스트 게시글', '내용', 0, 0, ?, ?)",
			postId, userId, now, now);
		return postId;
	}

	private void insertVote(UUID userId, UUID postId, String voteType) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO post_votes (id, post_id, user_id, vote_type, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
			UUID.randomUUID(), postId, userId, voteType, now, now);
	}

	private UUID insertDecision(UUID userId, String result, int price, String yearMonth) {
		UUID itemId = UUID.randomUUID();
		UUID decisionId = UUID.randomUUID();
		ZoneId kst = ZoneId.of("Asia/Seoul");
		java.time.YearMonth ym = java.time.YearMonth.parse(yearMonth);
		OffsetDateTime monthMid = OffsetDateTime.ofInstant(
			ym.atDay(15).atStartOfDay(kst).toInstant(), ZoneOffset.UTC);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO saved_items (id, user_id, input_source, title, listed_price, currency_code, category, category_locked_by_user, status, created_at, updated_at) VALUES (?, ?, 'DIRECT_INPUT', '테스트 상품', ?, 'KRW', 'DIGITAL', false, ?, ?, ?)",
			itemId, userId, price, result, monthMid, monthMid);
		jdbcTemplate.update(
			"INSERT INTO purchase_decisions (id, user_id, item_id, result, final_price, rationality_result, self_check_yes_count, is_changed, change_count, decided_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'RATIONAL', 0, false, 0, ?, ?, ?)",
			decisionId, userId, itemId, result, price, monthMid, now, now);
		return decisionId;
	}

	private UUID insertSelfCheckResponseSet(UUID decisionId) {
		UUID responseSetId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO self_check_response_sets (id, decision_id, yes_count, rationality_result, submitted_at, created_at, updated_at) VALUES (?, ?, 0, 'RATIONAL', ?, ?, ?)",
			responseSetId, decisionId, now, now, now);
		jdbcTemplate.update(
			"INSERT INTO self_check_answers (id, response_set_id, question_code, answer_boolean, created_at, updated_at) VALUES (?, ?, 'NEED', false, ?, ?)",
			UUID.randomUUID(), responseSetId, now, now);
		return responseSetId;
	}

	private void insertReflection(UUID userId, UUID decisionId, Integer satisfactionScore, String regretLevel,
		Boolean stillUsing, String reflectionNote) {
		UUID itemId = jdbcTemplate.queryForObject(
			"SELECT item_id FROM purchase_decisions WHERE id = ?", UUID.class, decisionId);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO purchase_reflections (id, user_id, item_id, decision_id, satisfaction_score, regret_level, still_using, reflection_note, reflected_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			UUID.randomUUID(), userId, itemId, decisionId, satisfactionScore, regretLevel, stillUsing, reflectionNote,
			now, now, now);
	}
}
