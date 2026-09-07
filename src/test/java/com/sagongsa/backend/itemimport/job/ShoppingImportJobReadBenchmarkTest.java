package com.sagongsa.backend.itemimport.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "app.shopping.import.job-worker.enabled=false")
class ShoppingImportJobReadBenchmarkTest extends PostgreSqlContainerTest {
    @Autowired ShoppingImportJobService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_BACKEND_BENCHMARK", matches = "true")
    void compareOwnedPendingJobPolling() throws Exception {
        UUID user = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        jdbc.update("insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', now(), now())", user);
        jdbc.update("insert into shopping_import_jobs (id, user_id, status, request_json, request_hash, attempt_count, created_at, updated_at) values (?, ?, 'PENDING', '{}'::jsonb, ?, 0, now(), now())", job, user, "b".repeat(64));
        StringBuilder csv = new StringBuilder("round,operation,samples,p50_ms,p95_ms\n");
        for (int round = 1; round <= 4; round++) {
            for (int i = 0; i < 100; i++) service.get(user, job);
            long[] elapsed = new long[200];
            for (int i = 0; i < elapsed.length; i++) {
                long start = System.nanoTime();
                var result = service.get(user, job);
                elapsed[i] = System.nanoTime() - start;
                assertThat(result.status()).isEqualTo(ShoppingImportJobStatus.PENDING);
            }
            Arrays.sort(elapsed);
            csv.append(String.format(Locale.ROOT, "%d,owned_pending_job,200,%.4f,%.4f%n", round, elapsed[99] / 1e6, elapsed[189] / 1e6));
        }
        Path output = Path.of("build/reports/job-read-benchmark.csv");
        Files.createDirectories(output.getParent());
        Files.writeString(output, csv);
    }
}
