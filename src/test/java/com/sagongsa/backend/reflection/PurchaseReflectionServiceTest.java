package com.sagongsa.backend.reflection;

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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class PurchaseReflectionServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private PurchaseReflectionService purchaseReflectionService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void createsReflectionForGoDecisionAndTrimsOptionalNote() {
		UUID userId = createReadyUser();
		UUID itemId = insertSavedItem(userId, "구매 상품", "GO");
		UUID decisionId = insertDecision(userId, itemId, "GO");
		UUID reminderId = insertReminder(userId, itemId, decisionId);

		PurchaseReflectionResponse response = purchaseReflectionService.create(
			userId,
			new PurchaseReflectionRequest(decisionId, 5, "low", true, "  잘 쓰고 있어요  ")
		);

		assertThat(response.decisionId()).isEqualTo(decisionId);
		assertThat(response.itemId()).isEqualTo(itemId);
		assertThat(response.reminderId()).isEqualTo(reminderId);
		assertThat(response.satisfactionScore()).isEqualTo(5);
		assertThat(response.regretLevel()).isEqualTo("LOW");
		assertThat(response.stillUsing()).isTrue();
		assertThat(response.reflectionNote()).isEqualTo("잘 쓰고 있어요");
		assertThat(queryInteger("select count(*) from purchase_reflections where decision_id = ?", decisionId)).isEqualTo(1);
	}

	@Test
	void rejectsDuplicateReflectionForSameDecision() {
		UUID userId = createReadyUser();
		UUID itemId = insertSavedItem(userId, "중복 상품", "GO");
		UUID decisionId = insertDecision(userId, itemId, "GO");

		PurchaseReflectionResponse created = purchaseReflectionService.create(
			userId,
			new PurchaseReflectionRequest(decisionId, 4, "NONE", true, null)
		);

		assertThat(created.reminderId()).isNull();
		assertThat(created.satisfactionScore()).isEqualTo(4);
		assertThatThrownBy(() -> purchaseReflectionService.create(
			userId,
			new PurchaseReflectionRequest(decisionId, 3, "LOW", true, null)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void keepsNullableSatisfactionScoreWhenReminderExists() {
		UUID userId = createReadyUser();
		UUID itemId = insertSavedItem(userId, "점수 없는 상품", "GO");
		UUID decisionId = insertDecision(userId, itemId, "GO");
		UUID reminderId = insertReminder(userId, itemId, decisionId);

		PurchaseReflectionResponse response = purchaseReflectionService.create(
			userId,
			new PurchaseReflectionRequest(decisionId, null, "NONE", true, null)
		);

		assertThat(response.reminderId()).isEqualTo(reminderId);
		assertThat(response.satisfactionScore()).isNull();
	}

	@Test
	void rejectsStopDecisionReflection() {
		UUID userId = createReadyUser();
		UUID itemId = insertSavedItem(userId, "참은 상품", "STOP");
		UUID decisionId = insertDecision(userId, itemId, "STOP");

		assertThatThrownBy(() -> purchaseReflectionService.create(
			userId,
			new PurchaseReflectionRequest(decisionId, 3, "MEDIUM", false, null)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void rejectsInvalidRequestBody() {
		UUID userId = createReadyUser();

		assertThatThrownBy(() -> purchaseReflectionService.create(userId, null))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST));
	}

	private UUID createReadyUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, created_at, updated_at)
			values (?, 'ACTIVE', 'COMPLETED', ?, ?)
			""",
			userId,
			now,
			now
		);
		return userId;
	}

	private UUID insertSavedItem(UUID userId, String title, String status) {
		UUID itemId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into saved_items (
				id, user_id, input_source, title, listed_price, currency_code,
				category, category_locked_by_user, status, created_at, updated_at
			)
			values (?, ?, 'DIRECT_INPUT', ?, 30000, 'KRW', 'BEAUTY', false, ?, ?, ?)
			""",
			itemId,
			userId,
			title,
			status,
			now,
			now
		);
		return itemId;
	}

	private UUID insertDecision(UUID userId, UUID itemId, String result) {
		UUID decisionId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
		jdbcTemplate.update(
			"""
			insert into purchase_decisions (
				id, user_id, item_id, result, final_price, budget_after_amount,
				similar_category_spend_amount, rationality_result, self_check_yes_count,
				decided_at, created_at, updated_at
			)
			values (?, ?, ?, ?, 30000, 30000, 0, 'RATIONAL', 1, ?, ?, ?)
			""",
			decisionId,
			userId,
			itemId,
			result,
			now,
			now,
			now
		);
		return decisionId;
	}

	private UUID insertReminder(UUID userId, UUID itemId, UUID decisionId) {
		UUID reminderId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into reminder_schedules (
				id, user_id, item_id, decision_id, reminder_type, scheduled_for,
				status, sent_at, created_at, updated_at
			)
			values (?, ?, ?, ?, 'REGRET_CHECK_7_DAYS', ?, 'SENT', ?, ?, ?)
			""",
			reminderId,
			userId,
			itemId,
			decisionId,
			now.minusMinutes(1),
			now,
			now,
			now
		);
		return reminderId;
	}

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}
}
