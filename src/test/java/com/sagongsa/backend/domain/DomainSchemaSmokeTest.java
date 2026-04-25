package com.sagongsa.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
		assertPartialIndex("idx_post_votes_post_type_active", "canceled_at is null");
		assertPartialIndex("idx_post_comments_post_created_visible", "deleted_at is null");
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
}
