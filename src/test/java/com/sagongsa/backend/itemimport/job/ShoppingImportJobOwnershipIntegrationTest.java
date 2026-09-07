package com.sagongsa.backend.itemimport.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "app.shopping.import.job-worker.enabled=false")
class ShoppingImportJobOwnershipIntegrationTest extends PostgreSqlContainerTest {
	@Autowired JdbcTemplate jdbc;
	@Autowired ShoppingImportJobRepository repository;
	private UUID leader;
	private UUID follower;

	@BeforeEach
	void seed() {
		jdbc.execute("truncate table users cascade");
		UUID user = UUID.randomUUID();
		jdbc.update("insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', now(), now())", user);
		leader = UUID.randomUUID();
		follower = UUID.randomUUID();
		for (UUID id : new UUID[]{leader, follower}) {
			jdbc.update("""
				insert into shopping_import_jobs (id, user_id, status, request_json, request_hash, attempt_count, created_at, updated_at, leader_job_id)
				values (?, ?, 'PENDING', '{}'::jsonb, ?, 0, now(), now(), ?)
				""", id, user, id.toString().replace("-", "").repeat(2), id.equals(leader) ? null : leader);
		}
	}

	@Test
	void lateSuccessCannotCompleteNewAttemptOrItsFollower() {
		var old = repository.claim();
		makeStale();
		assertThat(repository.recoverStaleJobs()).isEqualTo(1);
		var current = repository.claim();
		assertThat(current.attemptCount()).isEqualTo(old.attemptCount() + 1);
		repository.markSucceeded(old, "{\"attempt\":1}");
		assertState("RUNNING", "RUNNING");
		repository.markSucceeded(current, "{\"attempt\":2}");
		assertState("SUCCEEDED", "SUCCEEDED");
		assertThat(jdbc.queryForObject("select result_json ->> 'attempt' from shopping_import_jobs where id = ?", String.class, follower)).isEqualTo("2");
	}

	@Test
	void lateFailureCannotFailRecoveredPendingJobOrFollower() {
		var old = repository.claim();
		makeStale();
		repository.recoverStaleJobs();
		repository.markFailed(old, new ShoppingImportJobRepository.JobFailure("OLD", "old attempt"));
		assertState("PENDING", "PENDING");
	}

	private void makeStale() {
		jdbc.update("update shopping_import_jobs set started_at = now() - interval '1 day' where id = ?", leader);
	}

	private void assertState(String leaderState, String followerState) {
		assertThat(jdbc.queryForObject("select status from shopping_import_jobs where id = ?", String.class, leader)).isEqualTo(leaderState);
		assertThat(jdbc.queryForObject("select status from shopping_import_jobs where id = ?", String.class, follower)).isEqualTo(followerState);
	}
}
