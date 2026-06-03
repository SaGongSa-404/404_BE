package com.sagongsa.backend.mypage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemStatus;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MypageServiceTest extends PostgreSqlContainerTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Autowired
	private MypageService mypageService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── 프로필 조회 ──────────────────────────────────────────────────────────

	@Test
	void 프로필_조회_성공() {
		UUID userId = insertUser("너굴이", "너구리");

		MyProfileResponse response = mypageService.getMyProfile(userId);

		assertThat(response.id()).isEqualTo(userId);
		assertThat(response.nickname()).isEqualTo("너굴이");
		assertThat(response.mascotName()).isEqualTo("너구리");
	}

	@Test
	void 존재하지_않는_유저_프로필_조회시_예외() {
		assertThatThrownBy(() -> mypageService.getMyProfile(UUID.randomUUID()))
			.isInstanceOf(MypageNotFoundException.class);
	}

	@Test
	void 게시글_수_프로필에_포함() {
		UUID userId = insertUser("너굴이", "너구리");
		insertPost(userId);
		insertPost(userId);

		MyProfileResponse response = mypageService.getMyProfile(userId);

		assertThat(response.postCount()).isEqualTo(2);
	}

	@Test
	void 삭제된_게시글은_프로필_게시글_수에_미포함() {
		UUID userId = insertUser("너굴이", "너구리");
		UUID postId = insertPost(userId);
		jdbcTemplate.update("UPDATE feed_posts SET deleted_at = NOW() WHERE id = ?", postId);

		MyProfileResponse response = mypageService.getMyProfile(userId);

		assertThat(response.postCount()).isZero();
	}

	// ── 프로필 수정 ──────────────────────────────────────────────────────────

	@Test
	void 닉네임_수정_반영() {
		UUID userId = insertUser("원래닉네임", "너구리");

		MyProfileResponse response = mypageService.updateProfile(userId,
			new UpdateProfileRequest("새닉네임", null));

		assertThat(response.nickname()).isEqualTo("새닉네임");
	}

	@Test
	void 마스코트_이름_수정_반영() {
		UUID userId = insertUser("너굴이", "원래이름");

		MyProfileResponse response = mypageService.updateProfile(userId,
			new UpdateProfileRequest(null, "새이름"));

		assertThat(response.mascotName()).isEqualTo("새이름");
	}

	// ── 예산 수정 ────────────────────────────────────────────────────────────

	@Test
	void 예산_신규_생성() {
		UUID userId = insertUser("너굴이", "너구리");

		MypageService.BudgetUpdateResponse response =
			mypageService.updateBudget(userId, new UpdateBudgetRequest(300_000));

		assertThat(response.monthlyBudget()).isEqualTo(300_000);
	}

	@Test
	void 예산_기존_사이클_업데이트() {
		UUID userId = insertUser("너굴이", "너구리");
		String yearMonth = YearMonth.now(KST).toString();
		insertBudgetCycle(userId, yearMonth, 200_000, 0);

		MypageService.BudgetUpdateResponse response =
			mypageService.updateBudget(userId, new UpdateBudgetRequest(500_000));

		assertThat(response.monthlyBudget()).isEqualTo(500_000);
		Integer stored = jdbcTemplate.queryForObject(
			"SELECT monthly_budget_amount FROM budget_cycles WHERE user_id = ? AND year_month = ?",
			Integer.class, userId, yearMonth);
		assertThat(stored).isEqualTo(500_000);
	}

	// ── 알림 설정 ────────────────────────────────────────────────────────────

	@Test
	void 알림_설정_조회_프로필_없으면_기본값_true() {
		UUID userId = insertUserNoProfile();

		MypageService.NotificationSettingsResponse response =
			mypageService.getNotificationSettings(userId);

		assertThat(response.notificationEnabled()).isTrue();
	}

	@Test
	void 알림_설정_변경_반영() {
		UUID userId = insertUser("너굴이", "너구리");

		MypageService.NotificationSettingsResponse response =
			mypageService.updateNotificationSettings(userId,
				new NotificationSettingsRequest(false));

		assertThat(response.notificationEnabled()).isFalse();
	}

	@Test
	void 알림_설정_다시_켜기() {
		UUID userId = insertUser("너굴이", "너구리");
		mypageService.updateNotificationSettings(userId, new NotificationSettingsRequest(false));

		MypageService.NotificationSettingsResponse response =
			mypageService.updateNotificationSettings(userId, new NotificationSettingsRequest(true));

		assertThat(response.notificationEnabled()).isTrue();
	}

	// ── 통계 ─────────────────────────────────────────────────────────────────

	@Test
	void 통계_예산_없으면_null() {
		UUID userId = insertUser("너굴이", "너구리");
		String yearMonth = YearMonth.now(KST).toString();

		StatsResponse response = mypageService.getStats(userId, yearMonth);

		assertThat(response.budgetAmount()).isNull();
		assertThat(response.spentAmount()).isZero();
	}

	@Test
	void 통계_지출액_GO_결정만_집계() {
		UUID userId = insertUser("너굴이", "너구리");
		String yearMonth = YearMonth.now(KST).toString();
		insertBudgetCycle(userId, yearMonth, 500_000, 100_000);
		insertDecision(userId, "GO", 100_000, yearMonth);
		insertDecision(userId, "STOP", 50_000, yearMonth);

		StatsResponse response = mypageService.getStats(userId, yearMonth);

		assertThat(response.spentAmount()).isEqualTo(100_000);
		assertThat(response.restrainedAmount()).isEqualTo(50_000);
		assertThat(response.boughtCount()).isEqualTo(1);
		assertThat(response.restrainedCount()).isEqualTo(1);
	}

	@Test
	void 통계_카테고리별_소비와_합리성_집계() {
		UUID userId = insertUser("너굴이", "너구리");
		String yearMonth = YearMonth.now(KST).toString();
		insertBudgetCycle(userId, yearMonth, 500_000, 100_000);
		insertDecision(userId, "GO", 45_000, yearMonth, "FASHION", "RATIONAL");
		insertDecision(userId, "GO", 15_000, yearMonth, "BEAUTY", "IRRATIONAL");
		insertDecision(userId, "STOP", 27_000, yearMonth, "ETC", "IRRATIONAL");

		StatsResponse response = mypageService.getStats(userId, yearMonth);

		assertThat(response.categorySpendAmounts()).containsExactly(
			new CategorySpendAmountResponse(ItemCategory.FASHION, 45_000),
			new CategorySpendAmountResponse(ItemCategory.BEAUTY, 15_000)
		);
		assertThat(response.rationalChoiceRate()).isEqualTo(33.3);
		assertThat(response.irrationalChoiceCount()).isEqualTo(2);
	}

	@Test
	void 통계_예산_사용률_계산() {
		UUID userId = insertUser("너굴이", "너구리");
		String yearMonth = YearMonth.now(KST).toString();
		insertBudgetCycle(userId, yearMonth, 200_000, 0);
		insertDecision(userId, "GO", 100_000, yearMonth);

		StatsResponse response = mypageService.getStats(userId, yearMonth);

		assertThat(response.usageRate()).isEqualTo(50.0);
	}

	@Test
	void 통계_연월_없으면_현재_달_사용() {
		UUID userId = insertUser("너굴이", "너구리");

		StatsResponse response = mypageService.getStats(userId, null);

		assertThat(response.yearMonth()).isEqualTo(YearMonth.now(KST).toString());
	}

	// ── 이용 가능 월 목록 ────────────────────────────────────────────────────

	@Test
	void 이용_가능_월_현재_달_항상_포함() {
		UUID userId = insertUser("너굴이", "너구리");

		AvailableMonthsResponse response = mypageService.getAvailableMonths(userId);

		assertThat(response.months()).contains(YearMonth.now(KST).toString());
		assertThat(response.currentMonth()).isEqualTo(YearMonth.now(KST).toString());
	}

	@Test
	void 이용_가능_월_예산_사이클_있으면_포함() {
		UUID userId = insertUser("너굴이", "너구리");
		insertBudgetCycle(userId, "2026-03", 300_000, 0);
		insertBudgetCycle(userId, "2026-04", 300_000, 0);

		AvailableMonthsResponse response = mypageService.getAvailableMonths(userId);

		assertThat(response.months()).contains("2026-03", "2026-04");
	}

	@Test
	void 이용_가능_월_내림차순_정렬() {
		UUID userId = insertUser("너굴이", "너구리");
		insertBudgetCycle(userId, "2026-01", 100_000, 0);
		insertBudgetCycle(userId, "2026-03", 100_000, 0);

		AvailableMonthsResponse response = mypageService.getAvailableMonths(userId);

		int idxCurrentOrLater = response.months().indexOf(YearMonth.now(KST).toString());
		int idx01 = response.months().indexOf("2026-01");
		assertThat(idxCurrentOrLater).isLessThan(idx01);
	}

	// ── 위시 히스토리 ────────────────────────────────────────────────────────

	@Test
	void 위시_히스토리_GO_필터링() {
		UUID userId = insertUser("너굴이", "너구리");
		String yearMonth = YearMonth.now(KST).toString();
		insertDecision(userId, "GO", 50_000, yearMonth);
		insertDecision(userId, "STOP", 30_000, yearMonth);

		WishHistoryResponse response = mypageService.getWishHistory(
			userId, ItemStatus.GO, yearMonth, 0, 20);

		assertThat(response.wishes()).hasSize(1);
		assertThat(response.wishes().get(0).status()).isEqualTo(ItemStatus.GO);
	}

	@Test
	void 위시_히스토리_평가_결과_포함() {
		UUID userId = insertUser("너굴이", "너구리");
		String yearMonth = YearMonth.now(KST).toString();
		UUID decisionId = insertDecision(userId, "GO", 50_000, yearMonth);
		insertReflection(userId, decisionId, 5, "NONE", true, "잘 쓰고 있어요");

		WishHistoryResponse response = mypageService.getWishHistory(userId, ItemStatus.GO, yearMonth, 0, 20);

		WishSummaryResponse wish = response.wishes().get(0);
		assertThat(wish.decisionId()).isEqualTo(decisionId);
		assertThat(wish.reflection()).isNotNull();
		assertThat(wish.reflection().satisfactionScore()).isEqualTo(5);
		assertThat(wish.reflection().regretLevel()).isEqualTo("NONE");
		assertThat(wish.reflection().stillUsing()).isTrue();
	}

	// ── 회원 탈퇴 ────────────────────────────────────────────────────────────

	@Test
	void 탈퇴_후_유저_상태_WITHDRAWN() {
		UUID userId = insertUser("너굴이", "너구리");

		mypageService.deleteAccount(userId);

		String status = jdbcTemplate.queryForObject(
			"SELECT status FROM users WHERE id = ?", String.class, userId);
		assertThat(status).isEqualTo("WITHDRAWN");
	}

	@Test
	void 탈퇴_후_프로필_삭제() {
		UUID userId = insertUser("너굴이", "너구리");

		mypageService.deleteAccount(userId);

		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_profiles WHERE user_id = ?", Integer.class, userId);
		assertThat(count).isZero();
	}

	@Test
	void 탈퇴_후_게시글_소프트_삭제() {
		UUID userId = insertUser("너굴이", "너구리");
		insertPost(userId);

		mypageService.deleteAccount(userId);

		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM feed_posts WHERE user_id = ? AND deleted_at IS NULL", Integer.class, userId);
		assertThat(count).isZero();
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

	private UUID insertUserNoProfile() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO users (id, status, onboarding_status, created_at, updated_at) VALUES (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId, now, now);
		return userId;
	}

	private UUID insertPost(UUID userId) {
		UUID postId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO feed_posts (id, user_id, title, body, go_count, stop_count, created_at, updated_at) VALUES (?, ?, '테스트 게시글', '내용', 0, 0, ?, ?)",
			postId, userId, now, now);
		return postId;
	}

	private UUID insertBudgetCycle(UUID userId, String yearMonth, int monthlyBudget, int spentAmount) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO budget_cycles (id, user_id, year_month, monthly_budget_amount, spent_amount, warning_threshold_rate, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 80.00, ?, ?)",
			id, userId, yearMonth, monthlyBudget, spentAmount, now, now);
		return id;
	}

	private UUID insertDecision(UUID userId, String result, int price, String yearMonth) {
		return insertDecision(userId, result, price, yearMonth, "DIGITAL", "RATIONAL");
	}

	private UUID insertDecision(UUID userId, String result, int price, String yearMonth, String category,
		String rationalityResult) {
		UUID itemId = UUID.randomUUID();
		UUID decisionId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		ZoneId kst = ZoneId.of("Asia/Seoul");
		java.time.YearMonth ym = java.time.YearMonth.parse(yearMonth);
		OffsetDateTime monthMid = OffsetDateTime.ofInstant(
			ym.atDay(15).atStartOfDay(kst).toInstant(), ZoneOffset.UTC);

		jdbcTemplate.update(
			"INSERT INTO saved_items (id, user_id, input_source, title, listed_price, currency_code, category, category_locked_by_user, status, created_at, updated_at) VALUES (?, ?, 'DIRECT_INPUT', '테스트 상품', ?, 'KRW', ?, false, ?, ?, ?)",
			itemId, userId, price, category, result, monthMid, monthMid);
		short yesCount = "IRRATIONAL".equals(rationalityResult) ? (short) 2 : (short) 0;
		jdbcTemplate.update(
			"INSERT INTO purchase_decisions (id, user_id, item_id, result, final_price, rationality_result, self_check_yes_count, is_changed, change_count, decided_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, false, 0, ?, ?, ?)",
			decisionId, userId, itemId, result, price, rationalityResult, yesCount, monthMid, now, now);
		return decisionId;
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
