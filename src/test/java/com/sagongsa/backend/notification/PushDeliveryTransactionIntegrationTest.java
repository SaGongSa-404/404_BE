package com.sagongsa.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class PushDeliveryTransactionIntegrationTest extends PostgreSqlContainerTest {
	@Autowired JdbcTemplate jdbc;
	@Autowired NotificationPublisher publisher;
	@Autowired TransactionTemplate transactions;
	@MockitoBean FcmMessageSender sender;
	private UUID userId;
	private final AtomicInteger sent = new AtomicInteger();

	@BeforeEach
	void seed() {
		jdbc.execute("truncate table users cascade");
		userId = UUID.randomUUID();
		sent.set(0);
		jdbc.update("insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', now(), now())", userId);
		jdbc.update("insert into device_push_tokens (id, user_id, platform, push_token, is_active, created_at, updated_at) values (?, ?, 'ANDROID', 'invalid-test-token', true, now(), now())", UUID.randomUUID(), userId);
		when(sender.send(any())).thenAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?", Integer.class, userId)).isEqualTo(1);
			sent.incrementAndGet();
			return FcmSendResult.invalid();
		});
	}

	@Test
	void sendsAfterCommitWithoutTransactionAndCommitsTokenDeactivation() {
		transactions.executeWithoutResult(status -> {
			publisher.publish(request());
			assertThat(sent).hasValue(0);
		});
		assertThat(sent).hasValue(1);
		assertThat(jdbc.queryForObject("select is_active from device_push_tokens where push_token = 'invalid-test-token'", Boolean.class)).isFalse();
		publisher.publish(request());
		assertThat(sent).hasValue(1);
	}

	@Test
	void rollbackDoesNotSendPushOrDeactivateToken() {
		transactions.executeWithoutResult(status -> {
			publisher.publish(request());
			status.setRollbackOnly();
		});
		assertThat(sent).hasValue(0);
		assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?", Integer.class, userId)).isZero();
		assertThat(jdbc.queryForObject("select is_active from device_push_tokens where push_token = 'invalid-test-token'", Boolean.class)).isTrue();
	}

	private NotificationPublishRequest request() {
		return new NotificationPublishRequest(userId, "REGRET_CHECK_READY", "제목", "본문", null, null, null, null, "transaction-test", null);
	}
}
