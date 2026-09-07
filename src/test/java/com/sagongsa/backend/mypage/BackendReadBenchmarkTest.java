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

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.minimum-idle=0"})
class BackendReadBenchmarkTest extends PostgreSqlContainerTest {

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
	@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RUN_BACKEND_BENCHMARK", matches = "true")
	void comparesFixedSizeReadPaths() throws Exception {
		UUID userId = insertUser();
		for (int i = 0; i < POST_COUNT; i++) {
			UUID itemId = insertItem(userId, i);
			UUID postId = insertPost(userId, itemId, i);
			insertVote(userId, postId);
		}
		Instant cursor = Instant.now().plusSeconds(60);
		java.util.List<String> rows = new java.util.ArrayList<>();
		rows.add("round,operation,p50_ms,p95_ms,statements_per_call,samples");
		for (int round = 1; round <= 4; round++) {
			measure(rows, round, "my_posts", () -> mypageService.getMyPosts(userId, null, POST_COUNT), 5);
			measure(rows, round, "my_voted_posts", () -> mypageService.getMyVotedPosts(userId, cursor, POST_COUNT), 4);
		}
		java.nio.file.Path report = java.nio.file.Path.of("build/reports/backend-read-benchmark.csv");
		java.nio.file.Files.createDirectories(report.getParent());
		java.nio.file.Files.writeString(report, String.join("\n", rows) + "\n");
		System.out.println(String.join("\n", rows));
	}

	private void measure(java.util.List<String> rows, int round, String operation,
		java.util.function.Supplier<PostListResponse> request, int expectedStatements) {
		for (int i = 0; i < 100; i++) request.get();
		long[] elapsed = new long[200];
		statistics.clear();
		for (int i = 0; i < elapsed.length; i++) {
			long started = System.nanoTime();
			PostListResponse response = request.get();
			elapsed[i] = System.nanoTime() - started;
			assertThat(response.posts()).hasSize(POST_COUNT);
		}
		long statements = statistics.getPrepareStatementCount();
		assertThat(statements).isEqualTo((long) expectedStatements * elapsed.length);
		java.util.Arrays.sort(elapsed);
		rows.add(String.format(java.util.Locale.ROOT, "%d,%s,%.4f,%.4f,%d,%d", round, operation,
			elapsed[99] / 1_000_000.0, elapsed[189] / 1_000_000.0, expectedStatements, elapsed.length));
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
