package com.sagongsa.backend.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OnboardingServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private OnboardingService onboardingService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── TC1: 입력값 검증 ───────────────────────────────────────────────────────

	@Test
	void rejectsMascotNameNull() {
		UUID userId = insertUser("NOT_STARTED");
		assertThatThrownBy(() -> onboardingService.complete(userId, request(null, "Asia/Seoul", 100000, "LESS_THAN_ONCE")))
			.isInstanceOf(OnboardingBadRequestException.class);
	}

	@Test
	void rejectsMascotNameBlank() {
		UUID userId = insertUser("NOT_STARTED");
		assertThatThrownBy(() -> onboardingService.complete(userId, request("   ", "Asia/Seoul", 100000, "LESS_THAN_ONCE")))
			.isInstanceOf(OnboardingBadRequestException.class);
	}

	@Test
	void rejectsMascotNameExceeding40Characters() {
		UUID userId = insertUser("NOT_STARTED");
		assertThatThrownBy(() -> onboardingService.complete(userId, request("a".repeat(41), "Asia/Seoul", 100000, "LESS_THAN_ONCE")))
			.isInstanceOf(OnboardingBadRequestException.class);
	}

	@Test
	void acceptsMascotNameExactly40Characters() {
		UUID userId = insertUser("NOT_STARTED");
		OnboardingCompleteResponse response = onboardingService.complete(userId, request("a".repeat(40), "Asia/Seoul", 100000, "LESS_THAN_ONCE"));
		assertThat(response.onboardingStatus()).isEqualTo("COMPLETED");
	}

	@Test
	void defaultsTimezoneToSeoulWhenNull() {
		UUID userId = insertUser("NOT_STARTED");
		onboardingService.complete(userId, request("너굴", null, 100000, "LESS_THAN_ONCE"));
		assertThat(queryString("select timezone from user_profiles where user_id = ?", userId))
			.isEqualTo("Asia/Seoul");
	}

	@Test
	void rejectsBlankTimezone() {
		UUID userId = insertUser("NOT_STARTED");
		assertThatThrownBy(() -> onboardingService.complete(userId, request("너굴", "  ", 100000, "LESS_THAN_ONCE")))
			.isInstanceOf(OnboardingBadRequestException.class);
	}

	@Test
	void rejectsInvalidTimezone() {
		UUID userId = insertUser("NOT_STARTED");
		assertThatThrownBy(() -> onboardingService.complete(userId, request("너굴", "Not/AValidZone", 100000, "LESS_THAN_ONCE")))
			.isInstanceOf(OnboardingBadRequestException.class);
	}

	@Test
	void rejectsNullMonthlyBudgetAmount() {
		UUID userId = insertUser("NOT_STARTED");
		assertThatThrownBy(() -> onboardingService.complete(userId, request("너굴", "Asia/Seoul", null, "LESS_THAN_ONCE")))
			.isInstanceOf(OnboardingBadRequestException.class);
	}

	@Test
	void rejectsNegativeMonthlyBudgetAmount() {
		UUID userId = insertUser("NOT_STARTED");
		assertThatThrownBy(() -> onboardingService.complete(userId, request("너굴", "Asia/Seoul", -1, "LESS_THAN_ONCE")))
			.isInstanceOf(OnboardingBadRequestException.class);
	}

	@Test
	void acceptsZeroMonthlyBudgetAmount() {
		UUID userId = insertUser("NOT_STARTED");
		OnboardingCompleteResponse response = onboardingService.complete(userId, request("너굴", "Asia/Seoul", 0, "LESS_THAN_ONCE"));
		assertThat(response.onboardingStatus()).isEqualTo("COMPLETED");
	}

	@Test
	void rejectsInvalidRegretFrequencyChoice() {
		UUID userId = insertUser("NOT_STARTED");
		assertThatThrownBy(() -> onboardingService.complete(userId, request("너굴", "Asia/Seoul", 100000, "INVALID_CHOICE")))
			.isInstanceOf(OnboardingBadRequestException.class);
	}

	// ── TC2: 접근 제어 및 중복 방지 ──────────────────────────────────────────

	@Test
	void throwsNotFoundWhenUserDoesNotExist() {
		assertThatThrownBy(() -> onboardingService.complete(UUID.randomUUID(), defaultRequest()))
			.isInstanceOf(OnboardingNotFoundException.class);
	}

	@Test
	void throwsForbiddenWhenUserIsNotActive() {
		UUID userId = insertUser("SUSPENDED", "NOT_STARTED");
		assertThatThrownBy(() -> onboardingService.complete(userId, defaultRequest()))
			.isInstanceOf(OnboardingForbiddenException.class);
	}

	@Test
	void throwsConflictWhenOnboardingAlreadyCompleted() {
		UUID userId = insertUser("COMPLETED");
		assertThatThrownBy(() -> onboardingService.complete(userId, defaultRequest()))
			.isInstanceOf(OnboardingConflictException.class);
	}

	@Test
	void throwsConflictWhenSurveyAlreadyExists() {
		UUID userId = insertUser("NOT_STARTED");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"insert into survey_response_sets (id, user_id, survey_type, submitted_at, created_at, updated_at) values (?, ?, 'ONBOARDING', ?, ?, ?)",
			UUID.randomUUID(), userId, now, now, now
		);
		assertThatThrownBy(() -> onboardingService.complete(userId, defaultRequest()))
			.isInstanceOf(OnboardingConflictException.class);
	}

	// ── TC3: 정상 완료 ────────────────────────────────────────────────────────

	@Test
	void completesOnboardingAndMarksUserCompleted() {
		UUID userId = insertUser("NOT_STARTED");
		OnboardingCompleteResponse response = onboardingService.complete(userId, defaultRequest());

		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.onboardingStatus()).isEqualTo("COMPLETED");
		assertThat(queryString("select onboarding_status from users where id = ?", userId)).isEqualTo("COMPLETED");
	}

	@Test
	void insertsAllFiveTablesOnComplete() {
		UUID userId = insertUser("NOT_STARTED");
		onboardingService.complete(userId, defaultRequest());

		assertThat(queryCount("select count(*) from user_profiles where user_id = ?", userId)).isEqualTo(1);
		assertThat(queryCount("select count(*) from budget_cycles where user_id = ?", userId)).isEqualTo(1);
		assertThat(queryCount("select count(*) from survey_response_sets where user_id = ?", userId)).isEqualTo(1);
		assertThat(queryCount("select count(*) from mascot_profiles where user_id = ?", userId)).isEqualTo(1);
	}

	@Test
	void storesBudgetCycleWithCorrectAmounts() {
		UUID userId = insertUser("NOT_STARTED");
		onboardingService.complete(userId, request("너굴", "Asia/Seoul", 200000, "ONE_TO_THREE"));

		assertThat(queryInteger("select monthly_budget_amount from budget_cycles where user_id = ?", userId)).isEqualTo(200000);
		assertThat(queryInteger("select spent_amount from budget_cycles where user_id = ?", userId)).isZero();
	}

	@Test
	void storesCorrectSurveyAnswerForEachChoice() {
		UUID userId1 = insertUser("NOT_STARTED");
		onboardingService.complete(userId1, request("너굴1", "Asia/Seoul", 100000, "LESS_THAN_ONCE"));

		UUID userId2 = insertUser("NOT_STARTED");
		onboardingService.complete(userId2, request("너굴2", "Asia/Seoul", 100000, "ONE_TO_THREE"));

		UUID userId3 = insertUser("NOT_STARTED");
		onboardingService.complete(userId3, request("너굴3", "Asia/Seoul", 100000, "FOUR_OR_MORE"));

		assertThat(querySurveyAnswerNumber(userId1)).isEqualTo(1);
		assertThat(querySurveyAnswerNumber(userId2)).isEqualTo(2);
		assertThat(querySurveyAnswerNumber(userId3)).isEqualTo(3);
	}

	@Test
	void storesMascotStateAsDefault() {
		UUID userId = insertUser("NOT_STARTED");
		onboardingService.complete(userId, defaultRequest());

		assertThat(queryString("select mascot_state from mascot_profiles where user_id = ?", userId)).isEqualTo("DEFAULT");
	}

	// ── 헬퍼 ─────────────────────────────────────────────────────────────────

	private UUID insertUser(String onboardingStatus) {
		return insertUser("ACTIVE", onboardingStatus);
	}

	private UUID insertUser(String status, String onboardingStatus) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"insert into users (id, status, onboarding_status, created_at, updated_at) values (?, ?, ?, ?, ?)",
			userId, status, onboardingStatus, now, now
		);
		return userId;
	}

	private OnboardingCompleteRequest defaultRequest() {
		return request("너굴", "Asia/Seoul", 100000, "LESS_THAN_ONCE");
	}

	private OnboardingCompleteRequest request(String mascotName, String timezone, Integer monthlyBudgetAmount, String regretFrequencyChoice) {
		return new OnboardingCompleteRequest(mascotName, timezone, monthlyBudgetAmount, regretFrequencyChoice);
	}

	private String queryString(String sql, UUID userId) {
		return jdbcTemplate.queryForObject(sql, String.class, userId);
	}

	private Integer queryInteger(String sql, UUID userId) {
		return jdbcTemplate.queryForObject(sql, Integer.class, userId);
	}

	private Integer queryCount(String sql, UUID userId) {
		return jdbcTemplate.queryForObject(sql, Integer.class, userId);
	}

	private Integer querySurveyAnswerNumber(UUID userId) {
		return jdbcTemplate.queryForObject(
			"select a.answer_number from survey_answers a join survey_response_sets s on s.id = a.response_set_id where s.user_id = ?",
			Integer.class, userId
		);
	}
}
