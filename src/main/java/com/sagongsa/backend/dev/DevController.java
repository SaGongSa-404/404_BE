package com.sagongsa.backend.dev;

import com.sagongsa.backend.auth.CurrentUserId;
import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.domain.item.SavedItem;
import com.sagongsa.backend.domain.item.SavedItemRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
@Profile("!prod")
public class DevController {

	private final UserAccountRepository userAccountRepository;
	private final SavedItemRepository savedItemRepository;
	private final EntityManager em;
	private final JdbcTemplate jdbcTemplate;

	public DevController(UserAccountRepository userAccountRepository,
		SavedItemRepository savedItemRepository,
		EntityManager em,
		JdbcTemplate jdbcTemplate) {
		this.userAccountRepository = userAccountRepository;
		this.savedItemRepository = savedItemRepository;
		this.em = em;
		this.jdbcTemplate = jdbcTemplate;
	}

	@PostMapping("/users/test")
	@Transactional
	public ResponseEntity<Map<String, String>> createTestUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		jdbcTemplate.update(
			"INSERT INTO users (id, status, onboarding_status, created_at, updated_at) VALUES (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId, now, now);

		Long seq = jdbcTemplate.queryForObject("SELECT nextval('user_nickname_seq')", Long.class);
		String nickname = "너굴" + seq;

		jdbcTemplate.update(
			"INSERT INTO user_profiles (user_id, nickname, mascot_name, timezone, notification_enabled, created_at, updated_at) VALUES (?, ?, '너구리', 'Asia/Seoul', true, ?, ?)",
			userId, nickname, now, now);

		return ResponseEntity.ok(Map.of("userId", userId.toString(), "nickname", nickname));
	}

	@DeleteMapping("/profiles/{userId}")
	@Transactional
	public ResponseEntity<Void> deleteTestProfile(@PathVariable UUID userId) {
		jdbcTemplate.update("DELETE FROM user_profiles WHERE user_id = ?", userId);
		return ResponseEntity.noContent().build();
	}

	record TestDecisionRequest(String title, Integer price, String result) {}

	@PostMapping("/decisions/test")
	@Transactional
	public ResponseEntity<Map<String, String>> createTestDecision(
		@CurrentUserId UUID userId,
		@RequestBody TestDecisionRequest request) {

		String title = request.title() != null ? request.title() : "테스트 상품";
		int price = request.price() != null ? request.price() : 0;
		String result = request.result() != null ? request.result().toUpperCase() : "GO";

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String yearMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();

		// 예산 사이클 없으면 생성
		UUID budgetCycleId = jdbcTemplate.query(
			"SELECT id FROM budget_cycles WHERE user_id = ? AND year_month = ?",
			(rs, i) -> rs.getObject("id", UUID.class),
			userId, yearMonth
		).stream().findFirst().orElseGet(() -> {
			UUID id = UUID.randomUUID();
			jdbcTemplate.update(
				"INSERT INTO budget_cycles (id, user_id, year_month, monthly_budget_amount, spent_amount, warning_threshold_rate, created_at, updated_at) VALUES (?, ?, ?, 500000, 0, 80.00, ?, ?)",
				id, userId, yearMonth, now, now
			);
			return id;
		});

		// 상품 삽입
		UUID itemId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO saved_items (id, user_id, input_source, title, listed_price, currency_code, category, category_locked_by_user, status, created_at, updated_at) VALUES (?, ?, 'DIRECT_INPUT', ?, ?, 'KRW', 'DIGITAL', false, ?, ?, ?)",
			itemId, userId, title, price, result, now, now
		);

		// 결정 삽입
		UUID decisionId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO purchase_decisions (id, user_id, item_id, budget_cycle_id, result, final_price, rationality_result, self_check_yes_count, is_changed, change_count, decided_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'RATIONAL', 0, false, 0, ?, ?, ?)",
			decisionId, userId, itemId, budgetCycleId, result, price, now, now, now
		);

		// GO면 예산 소비 반영
		if ("GO".equals(result)) {
			jdbcTemplate.update(
				"UPDATE budget_cycles SET spent_amount = spent_amount + ?, updated_at = ? WHERE id = ?",
				price, now, budgetCycleId
			);
		}

		return ResponseEntity.ok(Map.of(
			"decisionId", decisionId.toString(),
			"itemId", itemId.toString(),
			"result", result,
			"month", yearMonth
		));
	}

	// 마이페이지 소비관리 테스트용: GO 2건 + STOP 1건 결정 데이터 생성
	@PostMapping("/decisions/test")
	@Transactional
	public ResponseEntity<Map<String, Object>> createTestDecisions(@CurrentUserId UUID userId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String yearMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();

		UUID budgetCycleId = jdbcTemplate.queryForObject(
			"SELECT id FROM budget_cycles WHERE user_id = ? AND year_month = ?",
			UUID.class, userId, yearMonth);

		record TestCase(String title, int price, String category, String result, int yesCount, String rationality) {}
		var cases = java.util.List.of(
			new TestCase("테스트 에어팟 프로", 350000, "DIGITAL", "GO", 1, "RATIONAL"),
			new TestCase("테스트 나이키 운동화", 150000, "FASHION", "GO", 3, "IRRATIONAL"),
			new TestCase("테스트 스타벅스 텀블러", 55000, "LIVING", "STOP", 2, "IRRATIONAL")
		);

		int totalGoSpent = 0;
		var ids = new java.util.ArrayList<String>();
		for (var tc : cases) {
			UUID itemId = UUID.randomUUID();
			jdbcTemplate.update(
				"INSERT INTO saved_items (id, user_id, input_source, title, listed_price, category, status, created_at, updated_at) VALUES (?, ?, 'DIRECT_INPUT', ?, ?, ?, ?, ?, ?)",
				itemId, userId, tc.title(), tc.price(), tc.category(), tc.result(), now, now);

			Integer finalPrice = tc.result().equals("GO") ? tc.price() : null;
			if (tc.result().equals("GO")) totalGoSpent += tc.price();
			int budgetAfter = totalGoSpent;

			UUID decId = UUID.randomUUID();
			jdbcTemplate.update("""
				INSERT INTO purchase_decisions
				  (id, user_id, item_id, budget_cycle_id, result, final_price,
				   budget_after_amount, similar_category_spend_amount,
				   rationality_result, self_check_yes_count, decided_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
				""",
				decId, userId, itemId, budgetCycleId, tc.result(), finalPrice,
				budgetAfter, tc.rationality(), tc.yesCount(), now, now, now);

			UUID scId = UUID.randomUUID();
			jdbcTemplate.update(
				"INSERT INTO self_check_response_sets (id, decision_id, yes_count, rationality_result, submitted_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
				scId, decId, tc.yesCount(), tc.rationality(), now, now, now);
			for (String code : new String[]{"NEED", "BUDGET", "ALTERNATIVE", "DELAY"}) {
				jdbcTemplate.update(
					"INSERT INTO self_check_answers (id, response_set_id, question_code, answer_boolean, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
					UUID.randomUUID(), scId, code, tc.yesCount() > 0 && !code.equals("DELAY"), now, now);
			}
			ids.add(decId.toString());
		}

		jdbcTemplate.update(
			"UPDATE budget_cycles SET spent_amount = spent_amount + ? WHERE id = ?",
			totalGoSpent, budgetCycleId);

		return ResponseEntity.ok(Map.of("created", cases.size(), "decisionIds", ids));
	}

	// 마이페이지 소비관리 테스트용: GO 2건 + STOP 1건 결정 데이터 생성
	@PostMapping("/decisions/test")
	@Transactional
	public ResponseEntity<Map<String, Object>> createTestDecisions(@CurrentUserId UUID userId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String yearMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();

		UUID budgetCycleId = jdbcTemplate.queryForObject(
			"SELECT id FROM budget_cycles WHERE user_id = ? AND year_month = ?",
			UUID.class, userId, yearMonth);

		record TestCase(String title, int price, String category, String result, int yesCount, String rationality) {}
		var cases = java.util.List.of(
			new TestCase("테스트 에어팟 프로", 350000, "DIGITAL", "GO", 1, "RATIONAL"),
			new TestCase("테스트 나이키 운동화", 150000, "FASHION", "GO", 3, "IRRATIONAL"),
			new TestCase("테스트 스타벅스 텀블러", 55000, "LIVING", "STOP", 2, "IRRATIONAL")
		);

		int totalGoSpent = 0;
		var ids = new java.util.ArrayList<String>();
		for (var tc : cases) {
			UUID itemId = UUID.randomUUID();
			jdbcTemplate.update(
				"INSERT INTO saved_items (id, user_id, input_source, title, listed_price, category, status, created_at, updated_at) VALUES (?, ?, 'DIRECT_INPUT', ?, ?, ?, ?, ?, ?)",
				itemId, userId, tc.title(), tc.price(), tc.category(), tc.result(), now, now);

			Integer finalPrice = tc.result().equals("GO") ? tc.price() : null;
			if (tc.result().equals("GO")) totalGoSpent += tc.price();
			int budgetAfter = totalGoSpent;

			UUID decId = UUID.randomUUID();
			jdbcTemplate.update("""
				INSERT INTO purchase_decisions
				  (id, user_id, item_id, budget_cycle_id, result, final_price,
				   budget_after_amount, similar_category_spend_amount,
				   rationality_result, self_check_yes_count, decided_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
				""",
				decId, userId, itemId, budgetCycleId, tc.result(), finalPrice,
				budgetAfter, tc.rationality(), tc.yesCount(), now, now, now);

			UUID scId = UUID.randomUUID();
			jdbcTemplate.update(
				"INSERT INTO self_check_response_sets (id, decision_id, yes_count, rationality_result, submitted_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
				scId, decId, tc.yesCount(), tc.rationality(), now, now, now);
			for (String code : new String[]{"NEED", "BUDGET", "ALTERNATIVE", "DELAY"}) {
				jdbcTemplate.update(
					"INSERT INTO self_check_answers (id, response_set_id, question_code, answer_boolean, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
					UUID.randomUUID(), scId, code, tc.yesCount() > 0 && !code.equals("DELAY"), now, now);
			}
			ids.add(decId.toString());
		}

		jdbcTemplate.update(
			"UPDATE budget_cycles SET spent_amount = spent_amount + ? WHERE id = ?",
			totalGoSpent, budgetCycleId);

		return ResponseEntity.ok(Map.of("created", cases.size(), "decisionIds", ids));
	}

	@PostMapping("/wishes/test")
	@Transactional
	public ResponseEntity<Map<String, String>> createTestWish(@CurrentUserId UUID userId) {
		UserAccount user = userAccountRepository.findById(userId)
			.orElseThrow(() -> new RuntimeException("로그인 유저를 찾을 수 없음"));

		SavedItem item = SavedItem.create(user,
			"테스트 상품 (에어팟 프로)", 350000, null, null,
			ItemCategory.DIGITAL, ItemInputSource.DIRECT_INPUT);
		savedItemRepository.save(item);

		em.createNativeQuery("UPDATE saved_items SET created_at = :ts WHERE id = :id")
			.setParameter("ts", Instant.now().minus(25, ChronoUnit.HOURS))
			.setParameter("id", item.getId().toString())
			.executeUpdate();

		return ResponseEntity.ok(Map.of(
			"itemId", item.getId().toString(),
			"tip", "GET /api/v1/deliberations/items/" + item.getId() + " 으로 숙려 화면 조회 가능"
		));
	}
}
