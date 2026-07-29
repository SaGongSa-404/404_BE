package com.sagongsa.backend.mypage;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.social.PostListResponse;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MypageQueryCountIntegrationTest extends PostgreSqlContainerTest {

	private static final int POST_COUNT = 20;

	@Autowired
	private MypageService mypageService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private Statistics statistics;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();
	}

	@Test
	void 내_게시글_목록은_게시글_수와_무관하게_고정된_쿼리로_조회한다() {
		UUID userId = insertUser();
		for (int i = 0; i < POST_COUNT; i++) {
			UUID itemId = insertItem(userId, i);
			UUID postId = insertPost(userId, itemId, i);
			insertVote(userId, postId);
		}

		statistics.clear();
		PostListResponse response = mypageService.getMyPosts(userId, null, POST_COUNT);
		long queryCount = statistics.getPrepareStatementCount();

		assertThat(response.posts()).hasSize(POST_COUNT);
		assertThat(queryCount).isEqualTo(5);
	}

	@Test
	void 투표한_게시글_목록은_게시글_수와_무관하게_고정된_쿼리로_조회한다() {
		UUID viewerId = insertUser();
		for (int i = 0; i < POST_COUNT; i++) {
			UUID authorId = insertUser();
			UUID itemId = insertItem(authorId, i);
			UUID postId = insertPost(authorId, itemId, i);
			insertVote(viewerId, postId);
		}

		statistics.clear();
		PostListResponse response = mypageService.getMyVotedPosts(
			viewerId,
			Instant.now().plusSeconds(60),
			POST_COUNT);
		long queryCount = statistics.getPrepareStatementCount();

		assertThat(response.posts()).hasSize(POST_COUNT);
		assertThat(queryCount).isEqualTo(4);
	}

	@Test
	void 사용자별_활성_투표_최신순_인덱스가_적용된다() {
		Boolean exists = jdbcTemplate.queryForObject(
			"""
			select exists (
			    select 1
			    from pg_indexes
			    where schemaname = current_schema()
			      and tablename = 'post_votes'
			      and indexname = 'idx_post_votes_user_active_created'
			)
			""",
			Boolean.class);

		assertThat(exists).isTrue();
	}

	private UUID insertUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId, now, now);
		jdbcTemplate.update(
			"insert into user_profiles (user_id, nickname, mascot_name, timezone, created_at, updated_at) values (?, '너굴이', '너구리', 'Asia/Seoul', ?, ?)",
			userId, now, now);
		return userId;
	}

	private UUID insertItem(UUID userId, int sequence) {
		UUID itemId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(sequence);
		jdbcTemplate.update(
			"""
			insert into saved_items (
			    id, user_id, input_source, original_url, normalized_url, title,
			    listed_price, currency_code, category, category_locked_by_user,
			    status, created_at, updated_at
			) values (?, ?, 'DIRECT_INPUT', ?, ?, ?, ?, 'KRW', 'ETC', false, 'SAVED', ?, ?)
			""",
			itemId,
			userId,
			"https://example.com/item/" + itemId,
			"https://example.com/item/" + itemId,
			"상품 " + sequence,
			10_000 + sequence,
			now,
			now);
		return itemId;
	}

	private UUID insertPost(UUID userId, UUID itemId, int sequence) {
		UUID postId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(sequence);
		jdbcTemplate.update(
			"""
			insert into feed_posts (
			    id, user_id, item_id, title, body, go_count, stop_count, created_at, updated_at
			) values (?, ?, ?, ?, '내용', 0, 0, ?, ?)
			""",
			postId, userId, itemId, "게시글 " + sequence, now, now);
		return postId;
	}

	private void insertVote(UUID userId, UUID postId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into post_votes (
			    id, post_id, user_id, vote_type, created_at, updated_at
			) values (?, ?, ?, 'GO', ?, ?)
			""",
			UUID.randomUUID(), postId, userId, now, now);
	}
}
