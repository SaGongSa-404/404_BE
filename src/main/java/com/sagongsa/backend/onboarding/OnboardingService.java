package com.sagongsa.backend.onboarding;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRulesException;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {

	static final String ONBOARDING_SURVEY_TYPE = "ONBOARDING";
	static final String REGRET_FREQUENCY_QUESTION_CODE = "RECENT_MONTH_PURCHASE_REGRET_FREQUENCY";

	private static final String ACTIVE_STATUS = "ACTIVE";
	private static final String COMPLETED_STATUS = "COMPLETED";
	private static final String DEFAULT_TIMEZONE = "Asia/Seoul";
	private static final BigDecimal DEFAULT_WARNING_THRESHOLD_RATE = new BigDecimal("80.00");
	private static final Map<String, Integer> REGRET_FREQUENCY_CHOICES = Map.of(
		"LESS_THAN_ONCE", 1,
		"ONE_TO_THREE", 2,
		"FOUR_OR_MORE", 3
	);

	private final JdbcTemplate jdbcTemplate;

	public OnboardingService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public OnboardingCompleteResponse complete(UUID userId, OnboardingCompleteRequest request) {
		NormalizedOnboardingRequest normalizedRequest = normalize(request);
		OnboardingUser onboardingUser = findOnboardingUser(userId);

		if (!ACTIVE_STATUS.equals(onboardingUser.status())) {
			throw new OnboardingForbiddenException("Only active users can complete onboarding.");
		}
		if (COMPLETED_STATUS.equals(onboardingUser.onboardingStatus())) {
			throw new OnboardingConflictException("Onboarding has already been completed.");
		}
		if (hasOnboardingSurvey(userId)) {
			throw new OnboardingConflictException("Onboarding survey has already been submitted.");
		}

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String budgetYearMonth = YearMonth.now(normalizedRequest.zoneId()).toString();
		UUID budgetCycleId = UUID.randomUUID();
		UUID surveyResponseSetId = UUID.randomUUID();
		UUID surveyAnswerId = UUID.randomUUID();

		try {
			insertUserProfile(userId, normalizedRequest, now);
			insertBudgetCycle(userId, budgetCycleId, normalizedRequest.monthlyBudgetAmount(), budgetYearMonth, now);
			insertSurveyResponseSet(userId, surveyResponseSetId, now);
			insertSurveyAnswer(surveyResponseSetId, surveyAnswerId, normalizedRequest.regretFrequencyChoice(), now);
			insertMascotProfile(userId, now);
		} catch (DuplicateKeyException exception) {
			throw new OnboardingConflictException("Onboarding data already exists for this user.");
		}

		int updated = jdbcTemplate.update(
			"""
			update users
			set onboarding_status = ?, updated_at = ?
			where id = ?
			  and status = ?
			  and onboarding_status <> ?
			""",
			COMPLETED_STATUS,
			now,
			userId,
			ACTIVE_STATUS,
			COMPLETED_STATUS
		);
		if (updated != 1) {
			throw new OnboardingConflictException("Onboarding has already been completed.");
		}

		return new OnboardingCompleteResponse(userId, COMPLETED_STATUS, budgetYearMonth, surveyResponseSetId);
	}

	private OnboardingUser findOnboardingUser(UUID userId) {
		try {
			return jdbcTemplate.queryForObject(
				"select status, onboarding_status from users where id = ?",
				(rs, rowNum) -> new OnboardingUser(
					rs.getString("status"),
					rs.getString("onboarding_status")
				),
				userId
			);
		} catch (EmptyResultDataAccessException exception) {
			throw new OnboardingNotFoundException("User was not found.");
		}
	}

	private boolean hasOnboardingSurvey(UUID userId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"select exists (select 1 from survey_response_sets where user_id = ? and survey_type = ?)",
			Boolean.class,
			userId,
			ONBOARDING_SURVEY_TYPE
		);
		return Boolean.TRUE.equals(exists);
	}

	private void insertUserProfile(UUID userId, NormalizedOnboardingRequest request, OffsetDateTime now) {
		Long seq = jdbcTemplate.queryForObject("SELECT nextval('user_nickname_seq')", Long.class);
		String autoNickname = "너굴" + seq;
		jdbcTemplate.update(
			"""
				insert into user_profiles (
					user_id, nickname, mascot_name, timezone, profile_image_url, created_at, updated_at
				)
				values (?, ?, ?, ?, null, ?, ?)
				""",
			userId,
			autoNickname,
			request.mascotName(),
			request.timezone(),
			now,
			now
		);
	}

	private void insertBudgetCycle(UUID userId, UUID budgetCycleId, int monthlyBudgetAmount, String budgetYearMonth, OffsetDateTime now) {
		jdbcTemplate.update(
			"""
				insert into budget_cycles (
					id, user_id, year_month, monthly_budget_amount, spent_amount, warning_threshold_rate, created_at, updated_at
				)
				values (?, ?, ?, ?, 0, ?, ?, ?)
				""",
			budgetCycleId,
			userId,
			budgetYearMonth,
			monthlyBudgetAmount,
			DEFAULT_WARNING_THRESHOLD_RATE,
			now,
			now
		);
	}

	private void insertSurveyResponseSet(UUID userId, UUID surveyResponseSetId, OffsetDateTime now) {
		jdbcTemplate.update(
			"""
				insert into survey_response_sets (
					id, user_id, survey_type, submitted_at, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?)
				""",
			surveyResponseSetId,
			userId,
			ONBOARDING_SURVEY_TYPE,
			now,
			now,
			now
		);
	}

	private void insertSurveyAnswer(UUID surveyResponseSetId, UUID surveyAnswerId, String regretFrequencyChoice, OffsetDateTime now) {
		jdbcTemplate.update(
			"""
				insert into survey_answers (
					id, response_set_id, question_code, answer_text, answer_number, answer_choice, created_at, updated_at
				)
				values (?, ?, ?, null, ?, ?, ?, ?)
				""",
			surveyAnswerId,
			surveyResponseSetId,
			REGRET_FREQUENCY_QUESTION_CODE,
			REGRET_FREQUENCY_CHOICES.get(regretFrequencyChoice),
			regretFrequencyChoice,
			now,
			now
		);
	}

	private void insertMascotProfile(UUID userId, OffsetDateTime now) {
		jdbcTemplate.update(
			"""
				insert into mascot_profiles (
					user_id, mascot_state, last_reaction_message, last_state_changed_at, reaction_expires_at, updated_at
				)
				values (?, 'DEFAULT', null, ?, null, ?)
				""",
			userId,
			now,
			now
		);
	}

	private NormalizedOnboardingRequest normalize(OnboardingCompleteRequest request) {
		if (request == null) {
			throw new OnboardingBadRequestException("Request body is required.");
		}

		String mascotName = requireText(request.mascotName(), "mascotName", 40);
		String timezone = normalizeTimezone(request.timezone());
		int monthlyBudgetAmount = normalizeMonthlyBudgetAmount(request.monthlyBudgetAmount());
		String regretFrequencyChoice = normalizeRegretFrequencyChoice(request.regretFrequencyChoice());

		return new NormalizedOnboardingRequest(
			mascotName,
			timezone,
			ZoneId.of(timezone),
			monthlyBudgetAmount,
			regretFrequencyChoice
		);
	}

	private String requireText(String value, String fieldName, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new OnboardingBadRequestException(fieldName + " is required.");
		}

		String trimmed = value.trim();
		if (trimmed.length() > maxLength) {
			throw new OnboardingBadRequestException(fieldName + " must be " + maxLength + " characters or less.");
		}
		return trimmed;
	}

	private String normalizeTimezone(String timezone) {
		if (timezone == null) {
			return DEFAULT_TIMEZONE;
		}
		if (timezone.isBlank()) {
			throw new OnboardingBadRequestException("timezone must not be blank.");
		}

		String trimmed = timezone.trim();
		if (trimmed.length() > 40) {
			throw new OnboardingBadRequestException("timezone must be 40 characters or less.");
		}
		try {
			ZoneId.of(trimmed);
		} catch (ZoneRulesException exception) {
			throw new OnboardingBadRequestException("timezone must be a valid time zone ID.");
		}
		return trimmed;
	}

	private int normalizeMonthlyBudgetAmount(Integer monthlyBudgetAmount) {
		if (monthlyBudgetAmount == null) {
			throw new OnboardingBadRequestException("monthlyBudgetAmount is required.");
		}
		if (monthlyBudgetAmount < 0) {
			throw new OnboardingBadRequestException("monthlyBudgetAmount must be zero or greater.");
		}
		return monthlyBudgetAmount;
	}

	private String normalizeRegretFrequencyChoice(String regretFrequencyChoice) {
		String normalized = requireText(regretFrequencyChoice, "regretFrequencyChoice", 80);
		if (!REGRET_FREQUENCY_CHOICES.containsKey(normalized)) {
			throw new OnboardingBadRequestException(
				"regretFrequencyChoice must be one of LESS_THAN_ONCE, ONE_TO_THREE, FOUR_OR_MORE."
			);
		}
		return normalized;
	}

	private record NormalizedOnboardingRequest(
		String mascotName,
		String timezone,
		ZoneId zoneId,
		int monthlyBudgetAmount,
		String regretFrequencyChoice
	) {
	}

	private record OnboardingUser(String status, String onboardingStatus) {
	}
}
