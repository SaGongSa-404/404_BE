package com.sagongsa.backend.itemimport.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "app.shopping.import.job-worker.enabled=false")
class ShoppingImportJobPairedBenchmarkTest extends PostgreSqlContainerTest {
    @Autowired ShoppingImportJobService service;
    @Autowired ShoppingImportJobQueries queries;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_BACKEND_BENCHMARK", matches = "true")
    void interleaveLegacyAndCurrentInTheSameJvm() throws Exception {
        assertThat(AopUtils.isAopProxy(service)).isFalse();
        assertThat(AopUtils.isAopProxy(queries)).isFalse();
        LegacyJobReadPath legacy = new LegacyJobReadPath(jdbc, mapper);
        UUID user = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        UUID completed = UUID.randomUUID();
        jdbc.update("insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', now(), now())", user);
        String request = "{\"inputSource\":\"SHARE\",\"url\":\"https://shop.example/products/1?user=second\",\"brandName\":\"요청자 브랜드\"}";
        String result = """
            {"retrievalStatus":"SUCCESS","item":{"inputSource":"SHARE","originalUrl":"https://shop.example/products/1?user=first","normalizedUrl":"https://shop.example/products/1","title":"성능 비교 상품","brandName":"기준 브랜드","summary":"상품 설명","imageUrl":"https://shop.example/image.png","listedPrice":10000,"currencyCode":"KRW","categoryLockedByUser":false,"status":"SAVED"},"saveRequest":{"inputSource":"SHARE","originalUrl":"https://shop.example/products/1?user=first","normalizedUrl":"https://shop.example/products/1","title":"성능 비교 상품","imageUrl":"https://shop.example/image.png","listedPrice":10000,"currencyCode":"KRW","categoryLockedByUser":false,"sourceDomain":"shop.example","rawTitle":"상품","rawDescription":"설명","rawPriceText":"10,000원","rawPayloadJson":"{}"},"warnings":[]}
            """;
        for (UUID job : new UUID[]{pending, completed}) {
            jdbc.update("insert into shopping_import_jobs (id, user_id, status, request_json, request_hash, result_json, attempt_count, created_at, updated_at, completed_at) values (?, ?, ?, cast(? as jsonb), ?, cast(? as jsonb), 0, now(), now(), ?)",
                job, user, job.equals(pending) ? "PENDING" : "SUCCEEDED", request,
                job.toString().replace("-", "").repeat(2), job.equals(pending) ? null : result,
                job.equals(pending) ? null : java.time.OffsetDateTime.now());
        }
        assertThat(service.get(user, completed)).isEqualTo(legacy.get(user, completed));
        assertThat(service.get(user, completed).result().item().brandName()).isEqualTo("요청자 브랜드");
        StringBuilder csv = new StringBuilder("scenario,block,lane,samples,p50_ms,p95_ms\n");
        compare("pending", () -> legacy.get(user, pending), () -> service.get(user, pending), csv);
        compare("completed", () -> legacy.get(user, completed), () -> service.get(user, completed), csv);
        compare("control_legacy_legacy", () -> legacy.get(user, pending), () -> legacy.get(user, pending), csv);
        Path output = Path.of("build/reports/job-paired-benchmark.csv");
        Files.createDirectories(output.getParent());
        Files.writeString(output, csv);
    }

    private void compare(String name, Supplier<ShoppingImportJobResponse> baseline,
                         Supplier<ShoppingImportJobResponse> candidate, StringBuilder csv) {
        for (int i = 0; i < 2000; i++) {
            baseline.get();
            candidate.get();
        }
        for (int block = 0; block < 20; block++) {
            long[] a = new long[500], b = new long[500];
            for (int i = 0; i < a.length; i++) {
                if ((i + block) % 2 == 0) {
                    a[i] = measure(baseline);
                    b[i] = measure(candidate);
                } else {
                    b[i] = measure(candidate);
                    a[i] = measure(baseline);
                }
            }
            append(csv, name, block, "baseline", a);
            append(csv, name, block, "candidate", b);
        }
    }

    private long measure(Supplier<ShoppingImportJobResponse> operation) {
        long start = System.nanoTime();
        var result = operation.get();
        long elapsed = System.nanoTime() - start;
        assertThat(result).isNotNull();
        return elapsed;
    }

    private void append(StringBuilder csv, String name, int block, String lane, long[] values) {
        Arrays.sort(values);
        csv.append(String.format(Locale.ROOT, "%s,%d,%s,500,%.6f,%.6f%n",
            name, block, lane, values[249] / 1e6, values[474] / 1e6));
    }
}
