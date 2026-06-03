package com.sagongsa.backend.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OnboardingCompleteIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void completePersistsOnboardingData() throws Exception {
		UUID userId = insertUser("NOT_STARTED");
		String expectedYearMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();

		mockMvc.perform(post("/api/v1/onboarding/complete")
				.header("X-User-Id", userId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingRequestBody()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value(userId.toString()))
			.andExpect(jsonPath("$.onboardingStatus").value("COMPLETED"))
			.andExpect(jsonPath("$.budgetYearMonth").value(expectedYearMonth));

		assertThat(countRows("user_profiles", userId)).isEqualTo(1);
		assertThat(countRows("budget_cycles", userId)).isEqualTo(1);
		assertThat(countRows("survey_response_sets", userId)).isEqualTo(1);
		assertThat(countRows("mascot_profiles", userId)).isEqualTo(1);

		assertThat(queryString("select nickname from user_profiles where user_id = ?", userId)).matches("너굴\\d+");
		assertThat(queryString("select mascot_name from user_profiles where user_id = ?", userId)).isEqualTo("wigul");
		assertThat(queryString("select timezone from user_profiles where user_id = ?", userId)).isEqualTo("Asia/Seoul");
		assertThat(queryString("select year_month from budget_cycles where user_id = ?", userId)).isEqualTo(expectedYearMonth);
		assertThat(queryInteger("select monthly_budget_amount from budget_cycles where user_id = ?", userId)).isEqualTo(300000);
		assertThat(queryInteger("select spent_amount from budget_cycles where user_id = ?", userId)).isZero();
		assertThat(queryBigDecimal("select warning_threshold_rate from budget_cycles where user_id = ?", userId))
			.isEqualByComparingTo("80.00");
		assertThat(queryString("select mascot_state from mascot_profiles where user_id = ?", userId)).isEqualTo("DEFAULT");
		assertThat(queryOnboardingSurveyAnswer(userId)).isEqualTo("FOUR_OR_MORE");
		assertThat(queryOnboardingSurveyAnswerNumber(userId)).isEqualTo(3);
	}

	@Test
	void autoGeneratesUniqueNeoGulNicknames() throws Exception {
		UUID userId1 = insertUser("NOT_STARTED");
		UUID userId2 = insertUser("NOT_STARTED");

		mockMvc.perform(post("/api/v1/onboarding/complete")
				.header("X-User-Id", userId1.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingRequestBody()))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/onboarding/complete")
				.header("X-User-Id", userId2.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingRequestBody()))
			.andExpect(status().isOk());

		String nickname1 = queryString("select nickname from user_profiles where user_id = ?", userId1);
		String nickname2 = queryString("select nickname from user_profiles where user_id = ?", userId2);

		assertThat(nickname1).matches("너굴\\d+");
		assertThat(nickname2).matches("너굴\\d+");
		assertThat(nickname1).isNotEqualTo(nickname2);
	}

	@Test
	void completeBlocksDuplicateSubmit() throws Exception {
		UUID userId = insertUser("NOT_STARTED");

		mockMvc.perform(post("/api/v1/onboarding/complete")
				.header("X-User-Id", userId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingRequestBody()))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/onboarding/complete")
				.header("X-User-Id", userId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingRequestBody()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ONBOARDING_CONFLICT"));

		assertThat(countRows("survey_response_sets", userId)).isEqualTo(1);
		assertThat(countRows("survey_answers", userId)).isEqualTo(1);
	}

	@Test
	void completeUpdatesUserStatus() throws Exception {
		UUID userId = insertUser("IN_PROGRESS");

		mockMvc.perform(post("/api/v1/onboarding/complete")
				.header("X-User-Id", userId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingRequestBody()))
			.andExpect(status().isOk());

		assertThat(queryString("select onboarding_status from users where id = ?", userId)).isEqualTo("COMPLETED");
	}

	@Test
	void completeBlocksSuspendedUser() throws Exception {
		UUID userId = insertUser("ACTIVE", "NOT_STARTED");

		jdbcTemplate.update("update users set status = 'SUSPENDED' where id = ?", userId);

		mockMvc.perform(post("/api/v1/onboarding/complete")
				.header("X-User-Id", userId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingRequestBody()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		assertThat(countRows("user_profiles", userId)).isZero();
	}

	@Test
	void completeWithNicknameSavesProvidedNickname() throws Exception {
		UUID userId = insertUser("NOT_STARTED");

		mockMvc.perform(post("/api/v1/onboarding/complete")
				.header("X-User-Id", userId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"nickname": "테스트닉네임",
						"mascotName": "wigul",
						"timezone": "Asia/Seoul",
						"monthlyBudgetAmount": 300000,
						"regretFrequencyChoice": "FOUR_OR_MORE"
					}
					"""))
			.andExpect(status().isOk());

		assertThat(queryString("select nickname from user_profiles where user_id = ?", userId))
			.isEqualTo("테스트닉네임");
	}

	@Test
	void completeWithoutNicknameFallsBackToAutoNickname() throws Exception {
		UUID userId = insertUser("NOT_STARTED");

		mockMvc.perform(post("/api/v1/onboarding/complete")
				.header("X-User-Id", userId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingRequestBody()))
			.andExpect(status().isOk());

		assertThat(queryString("select nickname from user_profiles where user_id = ?", userId))
			.matches("너굴\\d+");
	}

	@Test
	void completeWithTooShortNicknameReturns400() throws Exception {
		UUID userId = insertUser("NOT_STARTED");

		mockMvc.perform(post("/api/v1/onboarding/complete")
				.header("X-User-Id", userId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"nickname": "가",
						"mascotName": "wigul",
						"timezone": "Asia/Seoul",
						"monthlyBudgetAmount": 300000,
						"regretFrequencyChoice": "FOUR_OR_MORE"
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void completeWithTooLongNicknameReturns400() throws Exception {
		UUID userId = insertUser("NOT_STARTED");

		mockMvc.perform(post("/api/v1/onboarding/complete")
				.header("X-User-Id", userId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"nickname": "아홉글자닉네임이야",
						"mascotName": "wigul",
						"timezone": "Asia/Seoul",
						"monthlyBudgetAmount": 300000,
						"regretFrequencyChoice": "FOUR_OR_MORE"
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	private UUID insertUser(String onboardingStatus) {
		return insertUser("ACTIVE", onboardingStatus);
	}

	private UUID insertUser(String status, String onboardingStatus) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
				insert into users (
					id, status, onboarding_status, created_at, updated_at, withdrawn_at
				)
				values (?, ?, ?, ?, ?, null)
				""",
			userId,
			status,
			onboardingStatus,
			now,
			now
		);
		return userId;
	}

	private String onboardingRequestBody() {
		return """
			{
				"mascotName": "wigul",
				"timezone": "Asia/Seoul",
				"monthlyBudgetAmount": 300000,
				"regretFrequencyChoice": "FOUR_OR_MORE"
			}
			""";
	}

	private Integer countRows(String tableName, UUID userId) {
		String userIdColumn = "survey_answers".equals(tableName) ? "s.user_id" : "user_id";
		if ("survey_answers".equals(tableName)) {
			return jdbcTemplate.queryForObject(
				"""
					select count(*)
					from survey_answers a
					join survey_response_sets s on s.id = a.response_set_id
					where s.user_id = ?
					""",
				Integer.class,
				userId
			);
		}
		return jdbcTemplate.queryForObject(
			"select count(*) from " + tableName + " where " + userIdColumn + " = ?",
			Integer.class,
			userId
		);
	}

	private String queryOnboardingSurveyAnswer(UUID userId) {
		return jdbcTemplate.queryForObject(
			"""
				select a.answer_choice
				from survey_answers a
				join survey_response_sets s on s.id = a.response_set_id
				where s.user_id = ?
				  and s.survey_type = ?
				  and a.question_code = ?
				""",
			String.class,
			userId,
			OnboardingService.ONBOARDING_SURVEY_TYPE,
			OnboardingService.REGRET_FREQUENCY_QUESTION_CODE
		);
	}

	private Integer queryOnboardingSurveyAnswerNumber(UUID userId) {
		return jdbcTemplate.queryForObject(
			"""
				select a.answer_number
				from survey_answers a
				join survey_response_sets s on s.id = a.response_set_id
				where s.user_id = ?
				  and s.survey_type = ?
				  and a.question_code = ?
				""",
			Integer.class,
			userId,
			OnboardingService.ONBOARDING_SURVEY_TYPE,
			OnboardingService.REGRET_FREQUENCY_QUESTION_CODE
		);
	}

	private String queryString(String sql, UUID userId) {
		return jdbcTemplate.queryForObject(sql, String.class, userId);
	}

	private Integer queryInteger(String sql, UUID userId) {
		return jdbcTemplate.queryForObject(sql, Integer.class, userId);
	}

	private BigDecimal queryBigDecimal(String sql, UUID userId) {
		return jdbcTemplate.queryForObject(sql, BigDecimal.class, userId);
	}
}
