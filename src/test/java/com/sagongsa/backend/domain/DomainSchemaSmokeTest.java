package com.sagongsa.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DomainSchemaSmokeTest extends PostgreSqlContainerTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void createsAllBaselineTables() {
		List<String> tables = List.of(
			"users",
			"social_accounts",
			"refresh_tokens",
			"user_profiles",
			"survey_response_sets",
			"survey_answers",
			"budget_cycles",
			"saved_items",
			"item_source_metadata",
			"purchase_decisions",
			"purchase_decision_change_logs",
			"self_check_response_sets",
			"self_check_answers",
			"user_notification_settings",
			"device_push_tokens",
			"reminder_schedules",
			"notifications",
			"purchase_reflections",
			"mascot_profiles",
			"mascot_state_events",
			"feed_posts",
			"post_votes",
			"post_comments",
			"share_tokens",
			"terms_versions",
			"user_terms_agreements",
			"marketing_consent_histories"
		);

		for (String table : tables) {
			Integer count = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
			assertThat(count).isNotNull();
		}
	}

	@Test
	void includesPlanningDrivenColumns() {
		assertColumns(
			"purchase_decisions",
			"rationality_result",
			"self_check_yes_count",
			"is_changed",
			"change_count",
			"changed_at"
		);
		assertColumns(
			"purchase_decision_change_logs",
			"previous_result",
			"new_result",
			"reason_text",
			"changed_at"
		);
		assertColumns(
			"mascot_profiles",
			"mascot_state",
			"last_reaction_message",
			"reaction_expires_at"
		);
		assertColumns(
			"reminder_schedules",
			"status",
			"canceled_at",
			"cancel_reason"
		);
		assertColumns(
			"feed_posts",
			"go_count",
			"stop_count",
			"deleted_at"
		);
		assertColumns(
			"share_tokens",
			"feed_post_id",
			"token"
		);
		assertColumns(
			"terms_versions",
			"terms_type",
			"version",
			"effective_from"
		);
	}

	@Test
	void includesPostgresSpecificDefinitions() {
		String rawPayloadJsonType = jdbcTemplate.queryForObject(
			"""
			select data_type
			from information_schema.columns
			where table_name = 'item_source_metadata'
			  and column_name = 'raw_payload_json'
			""",
			String.class
		);
		assertThat(rawPayloadJsonType).isEqualTo("jsonb");

		assertPartialIndex("uk_saved_items_user_saved_url", "normalized_url is not null", "'saved'");
		assertPartialIndex("idx_feed_posts_visible_created", "deleted_at is null");
		assertNoConstraint("post_votes", "uk_post_votes_post_user");
		assertPartialIndex("uk_post_votes_post_user_active", "canceled_at is null");
		assertPartialIndex("idx_post_votes_post_type_active", "canceled_at is null");
		assertPartialIndex("idx_post_comments_post_created_visible", "deleted_at is null");
		assertTrigger("trg_post_votes_sync_feed_counts", "post_votes");
		assertTrigger("trg_self_check_matches_decision", "self_check_response_sets");
		assertNoTrigger("trg_prevent_self_check_response_set_delete", "self_check_response_sets");
		assertTrigger("trg_decision_matches_self_check", "purchase_decisions");
		assertConstraintComment("social_accounts", "uk_social_accounts_user_id", "MVP policy");
		assertTableComment("share_tokens", "MVP policy");
	}

	@Test
	void includesPlanningDrivenConstraints() {
		assertConstraint(
			"users",
			"chk_users_withdrawn_state",
			"withdrawn",
			"withdrawn_at",
			"is not null",
			"is null"
		);
		assertConstraint(
			"budget_cycles",
			"chk_budget_cycles_year_month_format",
			"check",
			"year_month",
			"0[1-9]",
			"1[0-2]"
		);
		assertConstraint(
			"budget_cycles",
			"chk_budget_cycles_non_negative_amounts",
			"monthly_budget_amount",
			"spent_amount",
			"warning_threshold_rate",
			">= 0",
			"100"
		);
		assertConstraint(
			"saved_items",
			"chk_saved_items_non_negative_numbers",
			"listed_price",
			"category_confidence",
			">= 0",
			"100"
		);
		assertConstraint("purchase_decisions", "chk_decisions_yes_count", "check", "self_check_yes_count", "0", "4");
		assertConstraint(
			"purchase_decisions",
			"chk_decisions_non_negative_amounts",
			"final_price",
			"budget_after_amount",
			"similar_category_spend_amount",
			"change_count",
			">= 0"
		);
		assertConstraint(
			"purchase_decisions",
			"chk_decisions_rationality",
			"check",
			"self_check_yes_count",
			"rational",
			"irrational"
		);
		assertConstraint("self_check_response_sets", "chk_self_check_sets_yes_count", "check", "yes_count", "0", "4");
		assertConstraint(
			"self_check_response_sets",
			"chk_self_check_sets_rationality",
			"check",
			"yes_count",
			"rational",
			"irrational"
		);
		assertConstraint(
			"purchase_decision_change_logs",
			"chk_decision_logs_prev_yes_count",
			"check",
			"previous_self_check_yes_count",
			"0",
			"4"
		);
		assertConstraint(
			"purchase_decision_change_logs",
			"chk_decision_logs_new_yes_count",
			"check",
			"new_self_check_yes_count",
			"0",
			"4"
		);
		assertConstraint(
			"purchase_decision_change_logs",
			"chk_decision_logs_non_negative_amounts",
			"previous_final_price",
			"new_final_price",
			">= 0"
		);
		assertConstraint(
			"purchase_decision_change_logs",
			"chk_decision_logs_prev_rationality",
			"check",
			"previous_self_check_yes_count",
			"previous_rationality_result",
			"rational",
			"irrational"
		);
		assertConstraint(
			"purchase_decision_change_logs",
			"chk_decision_logs_new_rationality",
			"check",
			"new_self_check_yes_count",
			"new_rationality_result",
			"rational",
			"irrational"
		);
		assertConstraint(
			"survey_answers",
			"uk_survey_answers_response_question",
			"unique",
			"response_set_id",
			"question_code"
		);
		assertConstraint(
			"self_check_answers",
			"uk_self_check_answers_response_question",
			"unique",
			"response_set_id",
			"question_code"
		);
		assertConstraint(
			"reminder_schedules",
			"chk_reminder_schedules_regret_decision",
			"regret_check_7_days",
			"decision_id",
			"is not null"
		);
		assertConstraint(
			"reminder_schedules",
			"chk_reminder_schedules_status_timestamps",
			"scheduled",
			"sent",
			"failed",
			"canceled",
			"sent_at",
			"canceled_at"
		);
		assertConstraint(
			"notifications",
			"chk_notifications_read_state",
			"is_read",
			"read_at",
			"is not null",
			"is null"
		);
		assertConstraint("feed_posts", "chk_feed_posts_vote_counts", "go_count", "stop_count", ">= 0");
	}

	@Test
	void rejectsInvalidCheckConstraintValues() {
		UUID userId = insertUser();
		UUID itemId = insertSavedItem(userId, "https://shop.example/check-violation");

		assertThatThrownBy(() -> insertPurchaseDecision(userId, itemId, (short) 5, "IRRATIONAL"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertPurchaseDecision(userId, itemId, (short) 1, "IRRATIONAL"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsRationalityMismatchBetweenDecisionAndSelfCheckSet() {
		UUID userId = insertUser();
		UUID itemId = insertSavedItem(userId, "https://shop.example/rationality-mismatch");
		UUID decisionId = insertPurchaseDecision(userId, itemId, (short) 1, "RATIONAL");

		assertThatThrownBy(
			() -> insertSelfCheckResponseSet(decisionId, (short) 2, "IRRATIONAL")
		).isInstanceOf(DataIntegrityViolationException.class);

		insertSelfCheckResponseSet(decisionId, (short) 1, "RATIONAL");

		assertThatThrownBy(() ->
			jdbcTemplate.update(
				"""
				update purchase_decisions
				set self_check_yes_count = 2,
				    rationality_result = 'IRRATIONAL',
				    updated_at = now()
				where id = ?
				""",
				decisionId
			)
		).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void allowsDeletingSelfCheckSetForAccountWithdrawalCleanup() {
		UUID userId = insertUser();
		UUID itemId = insertSavedItem(userId, "https://shop.example/self-check-delete");
		UUID decisionId = insertPurchaseDecision(userId, itemId, (short) 1, "RATIONAL");
		UUID responseSetId = insertSelfCheckResponseSet(decisionId, (short) 1, "RATIONAL");

		jdbcTemplate.update("delete from self_check_response_sets where id = ?", responseSetId);

		Integer count = jdbcTemplate.queryForObject(
			"select count(*) from self_check_response_sets where id = ?",
			Integer.class,
			responseSetId
		);
		assertThat(count).isZero();
	}

	@Test
	void allowsRevoteAfterCancelAndKeepsVoteCountsInSync() {
		UUID postOwnerId = insertUser();
		UUID voterId = insertUser();
		UUID postId = insertFeedPost(postOwnerId);

		UUID firstVoteId = insertPostVote(postId, voterId, "GO");
		assertVoteCounts(postId, 1, 0);

		assertThatThrownBy(() -> insertPostVote(postId, voterId, "STOP"))
			.isInstanceOf(DataIntegrityViolationException.class);

		jdbcTemplate.update(
			"update post_votes set canceled_at = now(), updated_at = now() where id = ?",
			firstVoteId
		);
		assertVoteCounts(postId, 0, 0);

		UUID secondVoteId = insertPostVote(postId, voterId, "STOP");
		assertVoteCounts(postId, 0, 1);

		Object updatedAtBeforeMetadataChange = feedPostUpdatedAt(postId);
		jdbcTemplate.update(
			"update post_votes set updated_at = now() where id = ?",
			secondVoteId
		);
		assertThat(feedPostUpdatedAt(postId)).isEqualTo(updatedAtBeforeMetadataChange);
		assertVoteCounts(postId, 0, 1);

		jdbcTemplate.update(
			"update post_votes set vote_type = 'GO', updated_at = now() where id = ?",
			secondVoteId
		);
		assertVoteCounts(postId, 1, 0);

		jdbcTemplate.update("delete from post_votes where id = ?", secondVoteId);
		assertVoteCounts(postId, 0, 0);
	}

	private void assertColumns(String table, String... expectedColumns) {
		Set<String> actualColumns = jdbcTemplate.queryForList(
				"select lower(column_name) from information_schema.columns where lower(table_name) = ?",
				String.class,
				table
			)
			.stream()
			.collect(Collectors.toSet());

		assertThat(actualColumns).contains(expectedColumns);
	}

	private void assertPartialIndex(String indexName, String... expectedPredicates) {
		String indexDefinition = jdbcTemplate.queryForObject(
			"select lower(indexdef) from pg_indexes where schemaname = current_schema() and indexname = ?",
			String.class,
			indexName
		);

		assertThat(indexDefinition).contains("where");
		assertThat(indexDefinition).contains(expectedPredicates);
	}

	private void assertTrigger(String triggerName, String tableName) {
		Integer count = jdbcTemplate.queryForObject(
			"""
			select count(*)
			from pg_trigger trg
			join pg_class tbl on tbl.oid = trg.tgrelid
			join pg_namespace n on n.oid = tbl.relnamespace
			where n.nspname = current_schema()
			  and lower(trg.tgname) = ?
			  and lower(tbl.relname) = ?
			  and not trg.tgisinternal
			""",
			Integer.class,
			triggerName,
			tableName
		);

		assertThat(count).isNotNull().isGreaterThan(0);
	}

	private void assertNoTrigger(String triggerName, String tableName) {
		Integer count = jdbcTemplate.queryForObject(
			"""
			select count(*)
			from pg_trigger trg
			join pg_class tbl on tbl.oid = trg.tgrelid
			join pg_namespace n on n.oid = tbl.relnamespace
			where n.nspname = current_schema()
			  and lower(trg.tgname) = ?
			  and lower(tbl.relname) = ?
			  and not trg.tgisinternal
			""",
			Integer.class,
			triggerName,
			tableName
		);

		assertThat(count).isZero();
	}

	private void assertConstraintComment(String tableName, String constraintName, String expectedFragment) {
		String comment = jdbcTemplate.queryForObject(
			"""
			select obj_description(c.oid, 'pg_constraint')
			from pg_constraint c
			join pg_class t on t.oid = c.conrelid
			join pg_namespace n on n.oid = c.connamespace
			where n.nspname = current_schema()
			  and t.relname = ?
			  and c.conname = ?
			""",
			String.class,
			tableName,
			constraintName
		);

		assertThat(comment).contains(expectedFragment);
	}

	private void assertTableComment(String tableName, String expectedFragment) {
		String comment = jdbcTemplate.queryForObject(
			"""
			select obj_description(c.oid, 'pg_class')
			from pg_class c
			join pg_namespace n on n.oid = c.relnamespace
			where n.nspname = current_schema()
			  and c.relname = ?
			""",
			String.class,
			tableName
		);

		assertThat(comment).contains(expectedFragment);
	}

	private void assertConstraint(String tableName, String constraintName, String... expectedFragments) {
		String constraintDefinition = jdbcTemplate.queryForObject(
			"""
			select lower(pg_get_constraintdef(c.oid))
			from pg_constraint c
			join pg_class t on t.oid = c.conrelid
			join pg_namespace n on n.oid = c.connamespace
			where n.nspname = current_schema()
			  and t.relname = ?
			  and c.conname = ?
			""",
			String.class,
			tableName,
			constraintName
		);

		assertThat(constraintDefinition).contains(expectedFragments);
	}

	private void assertNoConstraint(String tableName, String constraintName) {
		Integer count = jdbcTemplate.queryForObject(
			"""
			select count(*)
			from pg_constraint c
			join pg_class t on t.oid = c.conrelid
			join pg_namespace n on n.oid = c.connamespace
			where n.nspname = current_schema()
			  and t.relname = ?
			  and c.conname = ?
			""",
			Integer.class,
			tableName,
			constraintName
		);

		assertThat(count).isZero();
	}

	private UUID insertUser() {
		UUID userId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, created_at, updated_at)
			values (?, 'ACTIVE', 'COMPLETED', now(), now())
			""",
			userId
		);
		return userId;
	}

	private UUID insertSavedItem(UUID userId, String normalizedUrl) {
		UUID itemId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into saved_items (
			    id, user_id, input_source, original_url, normalized_url, title,
			    listed_price, currency_code, category, category_locked_by_user,
			    status, created_at, updated_at
			)
			values (?, ?, 'DIRECT_INPUT', ?, ?, '테스트 상품', 10000, 'KRW', 'FASHION', false, 'SAVED', now(), now())
			""",
			itemId,
			userId,
			normalizedUrl,
			normalizedUrl
		);
		return itemId;
	}

	private UUID insertPurchaseDecision(UUID userId, UUID itemId, short yesCount, String rationalityResult) {
		UUID decisionId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into purchase_decisions (
			    id, user_id, item_id, result, final_price, rationality_result,
			    self_check_yes_count, decided_at, created_at, updated_at
			)
			values (?, ?, ?, 'GO', 10000, ?, ?, now(), now(), now())
			""",
			decisionId,
			userId,
			itemId,
			rationalityResult,
			yesCount
		);
		return decisionId;
	}

	private UUID insertSelfCheckResponseSet(UUID decisionId, short yesCount, String rationalityResult) {
		UUID responseSetId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into self_check_response_sets (
			    id, decision_id, yes_count, rationality_result, submitted_at, created_at, updated_at
			)
			values (?, ?, ?, ?, now(), now(), now())
			""",
			responseSetId,
			decisionId,
			yesCount,
			rationalityResult
		);
		return responseSetId;
	}

	private UUID insertFeedPost(UUID userId) {
		UUID postId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into feed_posts (id, user_id, title, created_at, updated_at)
			values (?, ?, '살까요 말까요', now(), now())
			""",
			postId,
			userId
		);
		return postId;
	}

	private UUID insertPostVote(UUID postId, UUID userId, String voteType) {
		UUID voteId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			insert into post_votes (id, post_id, user_id, vote_type, created_at, updated_at)
			values (?, ?, ?, ?, now(), now())
			""",
			voteId,
			postId,
			userId,
			voteType
		);
		return voteId;
	}

	private void assertVoteCounts(UUID postId, int expectedGoCount, int expectedStopCount) {
		Integer goCount = jdbcTemplate.queryForObject(
			"select go_count from feed_posts where id = ?",
			Integer.class,
			postId
		);
		Integer stopCount = jdbcTemplate.queryForObject(
			"select stop_count from feed_posts where id = ?",
			Integer.class,
			postId
		);

		assertThat(goCount).isEqualTo(expectedGoCount);
		assertThat(stopCount).isEqualTo(expectedStopCount);
	}

	private Object feedPostUpdatedAt(UUID postId) {
		return jdbcTemplate.queryForObject(
			"select updated_at from feed_posts where id = ?",
			Object.class,
			postId
		);
	}
}
