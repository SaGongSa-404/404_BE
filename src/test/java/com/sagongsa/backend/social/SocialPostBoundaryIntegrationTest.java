package com.sagongsa.backend.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SocialPostBoundaryIntegrationTest extends PostgreSqlContainerTest {

	@Autowired SocialPostService posts;
	@Autowired SocialPostCreationRepository creation;
	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManagerFactory entityManagerFactory;
	@Autowired PlatformTransactionManager transactions;
	private Statistics statistics;

	@BeforeEach
	void setUp() {
		jdbc.execute("truncate table users cascade");
		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();
	}

	@AfterEach
	void resetStatistics() {
		statistics.setStatisticsEnabled(false);
	}

	@Test
	void duplicateLockRequiresTheCallingWriteTransaction() {
		assertThatThrownBy(() -> creation.acquireDuplicateLock(42L))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void postCreationParticipatesInCallerRollback() {
		UUID author = insertUser();
		assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
			posts.createPost(author, new CreatePostRequest("rollback", "body", null, null, null));
			throw new IllegalStateException("rollback probe");
		})).isInstanceOf(IllegalStateException.class).hasMessage("rollback probe");
		assertThat(jdbc.queryForObject("select count(*) from feed_posts", Long.class)).isZero();
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 20})
	void feedReadsKeepConstantQueryCounts(int count) {
		UUID author = insertUser();
		UUID viewer = insertUser();
		for (int i = 0; i < count; i++) {
			posts.createPost(author, new CreatePostRequest("post " + i, "body", null, null, null));
		}

		statistics.clear();
		assertThat(posts.getPosts(viewer, null, count).posts()).hasSize(count);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);

		statistics.clear();
		assertThat(posts.getPosts(null, null, count).posts()).hasSize(count);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);

		statistics.clear();
		assertThat(posts.getMyPosts(author, null, count).posts()).hasSize(count);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);
	}

	private UUID insertUser() {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbc.update("insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', ?, ?)", id, now, now);
		jdbc.update("insert into user_profiles (user_id, nickname, mascot_name, timezone, created_at, updated_at) values (?, '너굴이', '너구리', 'Asia/Seoul', ?, ?)", id, now, now);
		return id;
	}
}
