package com.sagongsa.backend.dev;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QaScenarioService {

	private static final String QA_NICKNAME_PREFIX = "QA너굴";
	private static final String QA_PROVIDER_USER_ID_PREFIX = "DEV_QA:";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final JdbcTemplate jdbcTemplate;

	public QaScenarioService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public QaUserScenarioResponse createQaUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String yearMonth = currentYearMonth();

		jdbcTemplate.update(
			"insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId,
			now,
			now
		);
		jdbcTemplate.update(
			"""
			insert into social_accounts (
				id, user_id, provider, provider_user_id, email, profile_image_url, created_at, updated_at
			)
			values (?, ?, 'KAKAO', ?, null, null, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			QA_PROVIDER_USER_ID_PREFIX + userId,
			now,
			now
		);

		Long seq = jdbcTemplate.queryForObject("select nextval('user_nickname_seq')", Long.class);
		String nickname = QA_NICKNAME_PREFIX + seq;
		jdbcTemplate.update(
			"""
			insert into user_profiles (
				user_id, nickname, mascot_name, timezone, profile_image_url, notification_enabled, created_at, updated_at
			)
			values (?, ?, '너구리', 'Asia/Seoul', null, true, ?, ?)
			""",
			userId,
			nickname,
			now,
			now
		);

		jdbcTemplate.update(
			"""
			insert into user_notification_settings (
				user_id, push_enabled, regret_reminder_enabled, wishlist_reminder_enabled,
				budget_warning_enabled, social_vote_enabled, updated_at
			)
			values (?, true, true, false, false, false, ?)
			""",
			userId,
			now
		);

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

		UUID budgetCycleId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into budget_cycles (
				id, user_id, year_month, monthly_budget_amount, spent_amount,
				warning_threshold_rate, created_at, updated_at
			)
			values (?, ?, ?, 500000, 0, 80.00, ?, ?)
			""",
			budgetCycleId,
			userId,
			yearMonth,
			now,
			now
		);

		return new QaUserScenarioResponse(
			userId,
			nickname,
			budgetCycleId,
			yearMonth,
			paths(userId)
		);
	}

	@Transactional
	public void deleteQaUser(UUID userId) {
		if (!userExists(userId)) {
			return;
		}
		if (!isQaUser(userId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only QA scenario users can be deleted.");
		}

		deleteSocialData(userId);
		deleteDecisionData(userId);
		deleteUserSettings(userId);
		jdbcTemplate.update("delete from users where id = ?", userId);
	}

	private boolean userExists(UUID userId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"select exists(select 1 from users where id = ?)",
			Boolean.class,
			userId
		);
		return Boolean.TRUE.equals(exists);
	}

	private boolean isQaUser(UUID userId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"""
			select exists(
				select 1
				from social_accounts
				where user_id = ?
				  and provider_user_id = ?
			)
			""",
			Boolean.class,
			userId,
			QA_PROVIDER_USER_ID_PREFIX + userId
		);
		return Boolean.TRUE.equals(exists);
	}

	private void deleteSocialData(UUID userId) {
		jdbcTemplate.update(
			"""
			delete from share_tokens
			where feed_post_id in (
				select id from feed_posts where user_id = ?
			)
			""",
			userId
		);
		jdbcTemplate.update(
			"""
			delete from post_reports
			where reporter_user_id = ?
			   or (target_type = 'POST' and target_id in (
					select id from feed_posts where user_id = ?
			   ))
			   or (target_type = 'COMMENT' and target_id in (
					select id
					from post_comments
					where user_id = ?
					   or post_id in (select id from feed_posts where user_id = ?)
			   ))
			   or (target_type = 'USER' and target_id = ?)
			""",
			userId,
			userId,
			userId,
			userId,
			userId
		);
		jdbcTemplate.update("delete from user_blocks where blocker_user_id = ? or blocked_user_id = ?", userId, userId);
		jdbcTemplate.update(
			"""
			delete from post_votes
			where user_id = ?
			   or post_id in (select id from feed_posts where user_id = ?)
			""",
			userId,
			userId
		);
		jdbcTemplate.update(
			"""
			delete from post_comments
			where user_id = ?
			   or post_id in (select id from feed_posts where user_id = ?)
			""",
			userId,
			userId
		);
		jdbcTemplate.update("delete from feed_posts where user_id = ?", userId);
	}

	private void deleteDecisionData(UUID userId) {
		jdbcTemplate.update("delete from notifications where user_id = ?", userId);
		jdbcTemplate.update("delete from purchase_reflections where user_id = ?", userId);
		jdbcTemplate.update("delete from reminder_schedules where user_id = ?", userId);
		jdbcTemplate.update("delete from mascot_state_events where user_id = ?", userId);
		jdbcTemplate.update(
			"""
			delete from self_check_answers
			where response_set_id in (
				select sc.id
				from self_check_response_sets sc
				join purchase_decisions pd on pd.id = sc.decision_id
				where pd.user_id = ?
			)
			""",
			userId
		);
		jdbcTemplate.update(
			"""
			delete from self_check_response_sets
			where decision_id in (
				select id from purchase_decisions where user_id = ?
			)
			""",
			userId
		);
		jdbcTemplate.update("delete from purchase_decision_change_logs where user_id = ?", userId);
		jdbcTemplate.update("delete from purchase_decisions where user_id = ?", userId);
		jdbcTemplate.update(
			"""
			delete from item_source_metadata
			where item_id in (
				select id from saved_items where user_id = ?
			)
			""",
			userId
		);
		jdbcTemplate.update("delete from saved_items where user_id = ?", userId);
		jdbcTemplate.update("delete from budget_cycles where user_id = ?", userId);
	}

	private void deleteUserSettings(UUID userId) {
		jdbcTemplate.update(
			"""
			delete from survey_answers
			where response_set_id in (
				select id from survey_response_sets where user_id = ?
			)
			""",
			userId
		);
		jdbcTemplate.update("delete from survey_response_sets where user_id = ?", userId);
		jdbcTemplate.update("delete from user_terms_agreements where user_id = ?", userId);
		jdbcTemplate.update("delete from marketing_consent_histories where user_id = ?", userId);
		jdbcTemplate.update("delete from device_push_tokens where user_id = ?", userId);
		jdbcTemplate.update("delete from user_notification_settings where user_id = ?", userId);
		jdbcTemplate.update("delete from refresh_tokens where user_id = ?", userId);
		jdbcTemplate.update("delete from social_accounts where user_id = ?", userId);
		jdbcTemplate.update("delete from mascot_profiles where user_id = ?", userId);
		jdbcTemplate.update("delete from user_profiles where user_id = ?", userId);
	}

	private Map<String, String> paths(UUID userId) {
		Map<String, String> paths = new LinkedHashMap<>();
		paths.put("home", "/api/v1/home/summary");
		paths.put("notifications", "/api/v1/notifications");
		paths.put("wishlist", "/api/v1/wishlist/items");
		paths.put("mypage", "/api/v1/users/me");
		paths.put("cleanup", "/api/dev/qa/users/" + userId);
		return paths;
	}

	private String currentYearMonth() {
		return YearMonth.now(SEOUL_ZONE).toString();
	}
}
