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
