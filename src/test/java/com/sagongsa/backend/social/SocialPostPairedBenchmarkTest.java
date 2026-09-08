package com.sagongsa.backend.social;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.domain.social.FeedPostRepository;
import com.sagongsa.backend.domain.social.PostCommentRepository;
import com.sagongsa.backend.domain.social.PostVoteRepository;
import com.sagongsa.backend.domain.user.UserProfileRepository;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(SocialPostPairedBenchmarkTest.BaselineConfiguration.class)
class SocialPostPairedBenchmarkTest extends PostgreSqlContainerTest {
	@Autowired SocialPostService current;
	@Autowired SocialPostQueries queries;
	@Autowired LegacySocialPostReadPath baseline;
	@Autowired JdbcTemplate jdbc;
	private UUID viewer;

	@TestConfiguration(proxyBeanMethods = false)
	static class BaselineConfiguration {
		@Bean
		LegacySocialPostReadPath legacySocialPostReadPath(FeedPostRepository posts,
			PostVoteRepository votes, PostCommentRepository comments,
			UserProfileRepository profiles, BlockService blocks) {
			return new LegacySocialPostReadPath(posts, votes, comments, profiles, blocks);
		}
	}

	@BeforeEach
	void prepareFeed() {
		jdbc.execute("truncate table users cascade");
		viewer = insertUser();
		for (int i = 0; i < 21; i++) {
			UUID author = insertUser();
			UUID item = UUID.randomUUID();
			UUID post = UUID.randomUUID();
			jdbc.update("""
				insert into saved_items (id, user_id, input_source, original_url, normalized_url,
				 title, listed_price, currency_code, category, category_locked_by_user, status, created_at, updated_at)
				values (?, ?, 'DIRECT_INPUT', ?, ?, 'item', 10000, 'KRW', 'ETC', false, 'SAVED', now(), now())
				""", item, author, "https://example.com/" + item, "https://example.com/" + item);
			jdbc.update("""
				insert into feed_posts (id, user_id, item_id, title, body, go_count, stop_count, created_at, updated_at)
				values (?, ?, ?, 'post', 'body', 0, 0, now() + (? * interval '1 second'), now())
				""", post, author, item, i);
			jdbc.update("insert into post_votes (id, post_id, user_id, vote_type, created_at, updated_at) values (?, ?, ?, 'GO', now(), now())", UUID.randomUUID(), post, viewer);
			jdbc.update("insert into post_comments (id, post_id, user_id, body, created_at, updated_at) values (?, ?, ?, 'comment', now(), now())", UUID.randomUUID(), post, viewer);
		}
	}

	@Test
	void feedAndCursorResponsesMatchThePreviousImplementation() {
		assertThat(AopUtils.isAopProxy(baseline)).isTrue();
		assertThat(AopUtils.isAopProxy(queries)).isTrue();
		assertThat(AopUtils.isAopProxy(current)).isFalse();
		for (UUID user : new UUID[]{viewer, null}) {
			PostListResponse first = current.getPosts(user, null, 20);
			assertThat(first).isEqualTo(baseline.getPosts(user, null, 20));
			assertThat(first.hasMore()).isTrue();
			assertThat(current.getPosts(user, first.nextCursor(), 20))
				.isEqualTo(baseline.getPosts(user, first.nextCursor(), 20));
		}
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "RUN_BACKEND_BENCHMARK", matches = "true")
	void interleavePreviousAndCurrentFeedReads() throws Exception {
		assertThat(current.getPosts(viewer, null, 20)).isEqualTo(baseline.getPosts(viewer, null, 20));
		StringBuilder csv = new StringBuilder("scenario,block,lane,samples,p50_ms,p95_ms\n");
		compare("authenticated_20", () -> baseline.getPosts(viewer, null, 20), () -> current.getPosts(viewer, null, 20), csv);
		compare("anonymous_20", () -> baseline.getPosts(null, null, 20), () -> current.getPosts(null, null, 20), csv);
		compare("control_legacy_legacy", () -> baseline.getPosts(viewer, null, 20), () -> baseline.getPosts(viewer, null, 20), csv);
		Path output = Path.of("build/reports/social-paired-benchmark.csv");
		Files.createDirectories(output.getParent());
		Files.writeString(output, csv);
	}

	private void compare(String name, Supplier<PostListResponse> before,
		Supplier<PostListResponse> after, StringBuilder csv) {
		for (int i = 0; i < 1000; i++) {
			before.get();
			after.get();
		}
		for (int block = 0; block < 12; block++) {
			long[] a = new long[250], b = new long[250];
			for (int i = 0; i < a.length; i++) {
				if ((i + block) % 2 == 0) {
					a[i] = measure(before);
					b[i] = measure(after);
				} else {
					b[i] = measure(after);
					a[i] = measure(before);
				}
			}
			append(csv, name, block, "baseline", a);
			append(csv, name, block, "candidate", b);
		}
	}

	private long measure(Supplier<PostListResponse> operation) {
		long start = System.nanoTime();
		PostListResponse result = operation.get();
		long elapsed = System.nanoTime() - start;
		assertThat(result.posts()).hasSize(20);
		return elapsed;
	}

	private void append(StringBuilder csv, String name, int block, String lane, long[] values) {
		Arrays.sort(values);
		csv.append(String.format(Locale.ROOT, "%s,%d,%s,250,%.6f,%.6f%n", name, block, lane,
			values[124] / 1e6, values[237] / 1e6));
	}

	private UUID insertUser() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', now(), now())", id);
		jdbc.update("insert into user_profiles (user_id, nickname, mascot_name, timezone, created_at, updated_at) values (?, '너굴이', '너구리', 'Asia/Seoul', now(), now())", id);
		return id;
	}
}
