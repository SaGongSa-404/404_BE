package com.sagongsa.backend.social;

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
class BlockServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private BlockService blockService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── 차단 ─────────────────────────────────────────────────────────────────

	@Test
	void 자기_자신을_차단하면_예외() {
		UUID userId = insertUser();

		assertThatThrownBy(() -> blockService.blockUser(userId, userId))
			.isInstanceOf(SocialFeedForbiddenException.class);
	}

	@Test
	void 이미_차단한_유저_다시_차단하면_예외() {
		UUID blocker = insertUser();
		UUID target = insertUser();
		blockService.blockUser(blocker, target);

		assertThatThrownBy(() -> blockService.blockUser(blocker, target))
			.isInstanceOf(SocialFeedForbiddenException.class);
	}

	@Test
	void 차단_성공_후_차단_목록에_포함() {
		UUID blocker = insertUser();
		UUID target = insertUser();

		blockService.blockUser(blocker, target);

		assertThat(blockService.getBlockedUserIds(blocker)).contains(target);
	}

	@Test
	void 차단하지_않은_유저는_목록에_없음() {
		UUID blocker = insertUser();
		UUID other = insertUser();

		assertThat(blockService.getBlockedUserIds(blocker)).doesNotContain(other);
	}

	@Test
	void 존재하지_않는_유저를_차단하면_예외() {
		UUID blocker = insertUser();

		assertThatThrownBy(() -> blockService.blockUser(blocker, UUID.randomUUID()))
			.isInstanceOf(SocialFeedNotFoundException.class);
	}

	// ── 차단 해제 ────────────────────────────────────────────────────────────

	@Test
	void 차단_해제_성공_후_목록에서_제거() {
		UUID blocker = insertUser();
		UUID target = insertUser();
		blockService.blockUser(blocker, target);

		blockService.unblockUser(blocker, target);

		assertThat(blockService.getBlockedUserIds(blocker)).doesNotContain(target);
	}

	@Test
	void 차단하지_않은_유저_해제하면_예외() {
		UUID blocker = insertUser();
		UUID target = insertUser();

		assertThatThrownBy(() -> blockService.unblockUser(blocker, target))
			.isInstanceOf(SocialFeedNotFoundException.class);
	}

	// ── 차단 목록 격리 ───────────────────────────────────────────────────────

	@Test
	void A가_B차단해도_B의_차단목록에_영향_없음() {
		UUID userA = insertUser();
		UUID userB = insertUser();
		blockService.blockUser(userA, userB);

		assertThat(blockService.getBlockedUserIds(userB)).doesNotContain(userA);
	}

	// ── 헬퍼 ─────────────────────────────────────────────────────────────────

	private UUID insertUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"INSERT INTO users (id, status, onboarding_status, created_at, updated_at) VALUES (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId, now, now);
		jdbcTemplate.update(
			"INSERT INTO user_profiles (user_id, nickname, mascot_name, timezone, created_at, updated_at) VALUES (?, '너굴이', '너구리', 'Asia/Seoul', ?, ?)",
			userId, now, now);
		return userId;
	}
}
