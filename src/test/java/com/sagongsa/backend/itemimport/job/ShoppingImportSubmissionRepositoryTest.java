package com.sagongsa.backend.itemimport.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "app.shopping.import.job-worker.enabled=false")
class ShoppingImportSubmissionRepositoryTest extends PostgreSqlContainerTest {
    @Autowired ShoppingImportSubmissionRepository repository;
    @Autowired ShoppingImportJobQueries queries;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void admissionLockCannotEscapeTheSubmissionTransaction() {
        assertThatThrownBy(() -> repository.lockAndFindExisting(UUID.randomUUID(), "hash"))
            .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> repository.activeLeaders())
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void insertedJobRollsBackWithItsOwnerTransaction() {
        UUID user = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        jdbc.update("insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', now(), now())", user);
        transactions.executeWithoutResult(status -> {
            repository.lockAndFindExisting(user, "a".repeat(64));
            repository.insertLeader(job, user, "{}", "a".repeat(64), null, OffsetDateTime.now());
            assertThat(queries.findOwned(user, job)).isNotNull();
            assertThat(queries.findOwned(UUID.randomUUID(), job)).isNull();
            status.setRollbackOnly();
        });
        assertThat(queries.findOwned(user, job)).isNull();
    }
}
