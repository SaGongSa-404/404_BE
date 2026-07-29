package com.sagongsa.backend.itemimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.itemimport.item.FetchedPage;
import com.sagongsa.backend.itemimport.item.PageFetcher;
import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobWorker;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
	"app.notification.reminder-worker.enabled=false",
	"app.notification.trigger-worker.enabled=false",
	"app.shopping.import.job-worker.enabled=false",
	"app.shopping.import.sync-bridge.enabled=true",
	"app.shopping.import.sync-bridge.wait-timeout=PT3S",
	"app.shopping.import.sync-bridge.poll-interval=PT0.02S",
	"app.shopping.import.job-worker.max-queue-size=3",
	"app.shopping.import.job-worker.max-active-per-user=2",
	"app.shopping.import.job-worker.max-attempts=2",
	"app.shopping.import.job-worker.stale-timeout=PT5M"
})
@AutoConfigureMockMvc
class ShoppingImportJobApiIntegrationTest extends PostgreSqlContainerTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String JOBS_PATH = "/api/v1/items/import-jobs";
	private static final String SHOP_URL = "https://shop.example.com/products/100";
	private static final String MUSINSA_URL = "https://www.musinsa.com/products/6632593?pid=first";
	private static final String MUSINSA_TRACKING_VARIANT =
		"https://www.musinsa.com/products/6632593?shortlink=second";
	private static final String MUSINSA_FETCH_URL = "https://www.musinsa.com/products/6632593";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ShoppingImportJobWorker worker;

	@Autowired
	private FakeJobPageFetcher pageFetcher;

	@Autowired
	private ShoppingImportProperties properties;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
		pageFetcher.reset();
		properties.getSharedCrawl().setCoalescingEnabled(true);
		properties.getSharedCrawl().setCacheEnabled(true);
	}

	@Test
	void submitsProcessesAndReturnsAsyncImportResult() throws Exception {
		UUID userId = createUser();
		pageFetcher.stub(SHOP_URL, productHtml());

		String acceptedBody = submit(userId, SHOP_URL)
			.andExpect(status().isAccepted())
			.andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith(JOBS_PATH + "/")))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID jobId = UUID.fromString(objectMapper.readTree(acceptedBody).get("jobId").asText());

		mockMvc.perform(get(JOBS_PATH + "/" + jobId).header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.result").doesNotExist());

		assertThat(worker.processNextJob()).isTrue();

		mockMvc.perform(get(JOBS_PATH + "/" + jobId).header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.attemptCount").value(1))
			.andExpect(jsonPath("$.result.retrievalStatus").value("SUCCESS"))
			.andExpect(jsonPath("$.result.item.title").value("Noise Canceling Headphones"))
			.andExpect(jsonPath("$.result.item.listedPrice").value(129000))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	void keepsSynchronousContractWhileProcessingThroughQueue() throws Exception {
		UUID userId = createUser();
		pageFetcher.stub(SHOP_URL, productHtml());

		try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
			Future<org.springframework.test.web.servlet.ResultActions> response = executor.submit(() ->
				mockMvc.perform(post("/api/v1/items/import-link")
					.header(USER_ID_HEADER, userId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson(SHOP_URL)))
			);

			awaitPendingJob();
			assertThat(worker.processNextJob()).isTrue();

			response.get(3, TimeUnit.SECONDS)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.retrievalStatus").value("SUCCESS"))
				.andExpect(jsonPath("$.item.title").value("Noise Canceling Headphones"));
		}

		assertThat(pageFetcher.fetchCount(SHOP_URL)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from shopping_import_jobs where status = 'SUCCEEDED'",
			Integer.class
		)).isEqualTo(1);
	}

	@Test
	void concurrentWorkersClaimDifferentJobsExactlyOnce() throws Exception {
		UUID userId = createUser();
		String firstUrl = "https://shop.example.com/products/201";
		String secondUrl = "https://shop.example.com/products/202";
		pageFetcher.stub(firstUrl, productHtml());
		pageFetcher.stub(secondUrl, productHtml());
		pageFetcher.waitForConcurrentFetches(2);
		submit(userId, firstUrl).andExpect(status().isAccepted());
		submit(userId, secondUrl).andExpect(status().isAccepted());

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<Boolean> first = executor.submit(worker::processNextJob);
			Future<Boolean> second = executor.submit(worker::processNextJob);

			assertThat(first.get(3, TimeUnit.SECONDS)).isTrue();
			assertThat(second.get(3, TimeUnit.SECONDS)).isTrue();
		}

		Map<String, Integer> statusCounts = jdbcTemplate.query(
			"select status, count(*) as count from shopping_import_jobs group by status",
			rs -> {
				Map<String, Integer> counts = new ConcurrentHashMap<>();
				while (rs.next()) {
					counts.put(rs.getString("status"), rs.getInt("count"));
				}
				return counts;
			}
		);
		Integer maxAttemptCount = jdbcTemplate.queryForObject(
			"select max(attempt_count) from shopping_import_jobs",
			Integer.class
		);
		assertThat(statusCounts).containsEntry("SUCCEEDED", 2).hasSize(1);
		assertThat(maxAttemptCount).isEqualTo(1);
		assertThat(pageFetcher.fetchCount(firstUrl)).isEqualTo(1);
		assertThat(pageFetcher.fetchCount(secondUrl)).isEqualTo(1);
	}

	@Test
	void hidesJobFromAnotherUser() throws Exception {
		UUID ownerId = createUser();
		UUID otherUserId = createUser();
		String acceptedBody = submit(ownerId, SHOP_URL)
			.andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID jobId = UUID.fromString(objectMapper.readTree(acceptedBody).get("jobId").asText());

		mockMvc.perform(get(JOBS_PATH + "/" + jobId).header(USER_ID_HEADER, otherUserId))
			.andExpect(status().isNotFound());
	}

	@Test
	void rejectsSubmissionWhenBoundedQueueIsFull() throws Exception {
		UUID userId = createUser();

		submit(userId, "https://shop.example.com/products/1").andExpect(status().isAccepted());
		submit(userId, "https://shop.example.com/products/2").andExpect(status().isAccepted());
		submit(userId, "https://shop.example.com/products/3")
			.andExpect(status().isTooManyRequests());
	}

	@Test
	void appliesGlobalQueueCapacityToLeadersAcrossDifferentUsers() throws Exception {
		for (int index = 1; index <= 3; index++) {
			submit(createUser(), "https://shop.example.com/products/queue-" + index)
				.andExpect(status().isAccepted());
		}

		submit(createUser(), "https://shop.example.com/products/queue-4")
			.andExpect(status().isTooManyRequests());
	}

	@Test
	void acceptsFollowerWithoutConsumingAnotherGlobalQueueSlot() throws Exception {
		UUID leaderUserId = createUser();
		UUID followerUserId = createUser();
		submittedJobId(leaderUserId, MUSINSA_URL, "PENDING");
		submit(createUser(), "https://shop.example.com/products/queue-2")
			.andExpect(status().isAccepted());
		submit(createUser(), "https://shop.example.com/products/queue-3")
			.andExpect(status().isAccepted());

		submittedJobId(followerUserId, MUSINSA_TRACKING_VARIANT, "PENDING");

		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from shopping_import_jobs where leader_job_id is null",
			Integer.class
		)).isEqualTo(3);
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from shopping_import_jobs where leader_job_id is not null",
			Integer.class
		)).isEqualTo(1);
	}

	@Test
	void reportsOnlyPendingLeaderAsClaimable() throws Exception {
		assertThat(worker.hasClaimableJob()).isFalse();
		UUID jobId = submittedJobId(createUser(), SHOP_URL, "PENDING");

		assertThat(worker.hasClaimableJob()).isTrue();

		jdbcTemplate.update(
			"update shopping_import_jobs set status = 'RUNNING', started_at = now(), updated_at = now() where id = ?",
			jobId
		);

		assertThat(worker.hasClaimableJob()).isFalse();
	}

	@Test
	void reusesActiveJobForDuplicateRequest() throws Exception {
		UUID userId = createUser();

		String firstBody = submit(userId, SHOP_URL)
			.andExpect(status().isAccepted())
			.andReturn().getResponse().getContentAsString();
		String secondBody = submit(userId, SHOP_URL)
			.andExpect(status().isAccepted())
			.andReturn().getResponse().getContentAsString();

		assertThat(objectMapper.readTree(secondBody).get("jobId").asText())
			.isEqualTo(objectMapper.readTree(firstBody).get("jobId").asText());
		Integer count = jdbcTemplate.queryForObject("select count(*) from shopping_import_jobs", Integer.class);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void coalescesSameProductAcrossUsersAndKeepsUserJobsSeparate() throws Exception {
		UUID firstUserId = createUser();
		UUID secondUserId = createUser();
		pageFetcher.stub(MUSINSA_FETCH_URL, productHtml());

		UUID firstJobId = submittedJobId(firstUserId, MUSINSA_URL, "PENDING");
		UUID secondJobId = submittedJobId(secondUserId, MUSINSA_TRACKING_VARIANT, "PENDING");

		assertThat(secondJobId).isNotEqualTo(firstJobId);
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from shopping_import_jobs where leader_job_id is null",
			Integer.class
		)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"select leader_job_id from shopping_import_jobs where id = ?",
			UUID.class,
			secondJobId
		)).isEqualTo(firstJobId);

		assertThat(worker.processNextJob()).isTrue();
		assertThat(worker.processNextJob()).isFalse();
		assertThat(pageFetcher.fetchCount(MUSINSA_FETCH_URL)).isEqualTo(1);

		mockMvc.perform(get(JOBS_PATH + "/" + secondJobId).header(USER_ID_HEADER, secondUserId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.attemptCount").value(0))
			.andExpect(jsonPath("$.result.item.originalUrl").value(MUSINSA_TRACKING_VARIANT));
		mockMvc.perform(get(JOBS_PATH + "/" + secondJobId).header(USER_ID_HEADER, firstUserId))
			.andExpect(status().isNotFound());
	}

	@Test
	void returnsFreshSuccessCacheWithoutAnotherCrawl() throws Exception {
		UUID firstUserId = createUser();
		UUID secondUserId = createUser();
		pageFetcher.stub(MUSINSA_FETCH_URL, productHtml());

		submittedJobId(firstUserId, MUSINSA_URL, "PENDING");
		assertThat(worker.processNextJob()).isTrue();

		UUID cachedJobId = submittedJobId(secondUserId, MUSINSA_TRACKING_VARIANT, "SUCCEEDED");

		assertThat(worker.processNextJob()).isFalse();
		assertThat(pageFetcher.totalFetchCount()).isEqualTo(1);
		mockMvc.perform(get(JOBS_PATH + "/" + cachedJobId).header(USER_ID_HEADER, secondUserId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.item.originalUrl").value(MUSINSA_TRACKING_VARIANT));
	}

	@Test
	void propagatesLeaderFailureAndReusesShortFailureCache() throws Exception {
		UUID firstUserId = createUser();
		UUID secondUserId = createUser();
		UUID thirdUserId = createUser();
		pageFetcher.fail(
			MUSINSA_FETCH_URL,
			new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Shopping page returned 403")
		);

		UUID firstJobId = submittedJobId(firstUserId, MUSINSA_URL, "PENDING");
		UUID secondJobId = submittedJobId(secondUserId, MUSINSA_TRACKING_VARIANT, "PENDING");
		assertThat(worker.processNextJob()).isTrue();

		assertFailedJob(firstUserId, firstJobId);
		assertFailedJob(secondUserId, secondJobId);

		UUID cachedFailureJobId = submittedJobId(thirdUserId, MUSINSA_TRACKING_VARIANT, "FAILED");
		assertFailedJob(thirdUserId, cachedFailureJobId);
		assertThat(worker.processNextJob()).isFalse();
		assertThat(pageFetcher.totalFetchCount()).isEqualTo(1);
	}

	@Test
	void crawlsAgainAfterSuccessCacheExpires() throws Exception {
		UUID firstUserId = createUser();
		UUID secondUserId = createUser();
		pageFetcher.stub(MUSINSA_FETCH_URL, productHtml());

		submittedJobId(firstUserId, MUSINSA_URL, "PENDING");
		assertThat(worker.processNextJob()).isTrue();
		jdbcTemplate.update(
			"update shopping_import_jobs set completed_at = ? where leader_job_id is null",
			OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(6)
		);

		submittedJobId(secondUserId, MUSINSA_TRACKING_VARIANT, "PENDING");
		assertThat(worker.processNextJob()).isTrue();
		assertThat(pageFetcher.totalFetchCount()).isEqualTo(2);
	}

	@Test
	void crawlsAgainAfterFailureCacheExpires() throws Exception {
		UUID firstUserId = createUser();
		UUID secondUserId = createUser();
		pageFetcher.fail(
			MUSINSA_FETCH_URL,
			new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Shopping page returned 429")
		);

		submittedJobId(firstUserId, MUSINSA_URL, "PENDING");
		assertThat(worker.processNextJob()).isTrue();
		jdbcTemplate.update(
			"update shopping_import_jobs set completed_at = ? where leader_job_id is null",
			OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(31)
		);

		submittedJobId(secondUserId, MUSINSA_TRACKING_VARIANT, "PENDING");
		assertThat(worker.processNextJob()).isTrue();
		assertThat(pageFetcher.totalFetchCount()).isEqualTo(2);
	}

	@Test
	void disablesCoalescingWithoutDisablingTerminalCache() throws Exception {
		properties.getSharedCrawl().setCoalescingEnabled(false);
		UUID firstUserId = createUser();
		UUID secondUserId = createUser();
		pageFetcher.stub(MUSINSA_FETCH_URL, productHtml());

		submittedJobId(firstUserId, MUSINSA_URL, "PENDING");
		submittedJobId(secondUserId, MUSINSA_TRACKING_VARIANT, "PENDING");

		assertThat(worker.processNextJob()).isTrue();
		assertThat(worker.processNextJob()).isTrue();
		assertThat(pageFetcher.totalFetchCount()).isEqualTo(2);
	}

	@Test
	void disablesTerminalCacheWithoutDisablingActiveCoalescing() throws Exception {
		properties.getSharedCrawl().setCacheEnabled(false);
		UUID firstUserId = createUser();
		UUID secondUserId = createUser();
		UUID thirdUserId = createUser();
		pageFetcher.stub(MUSINSA_FETCH_URL, productHtml());

		submittedJobId(firstUserId, MUSINSA_URL, "PENDING");
		submittedJobId(secondUserId, MUSINSA_TRACKING_VARIANT, "PENDING");
		assertThat(worker.processNextJob()).isTrue();
		assertThat(pageFetcher.totalFetchCount()).isEqualTo(1);

		submittedJobId(thirdUserId, MUSINSA_TRACKING_VARIANT, "PENDING");
		assertThat(worker.processNextJob()).isTrue();
		assertThat(pageFetcher.totalFetchCount()).isEqualTo(2);
	}

	@Test
	void storesSafeFailureWithoutExposingInternalException() throws Exception {
		UUID userId = createUser();
		pageFetcher.fail(SHOP_URL, new IllegalStateException("upstream-secret-detail"));
		String acceptedBody = submit(userId, SHOP_URL)
			.andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID jobId = UUID.fromString(objectMapper.readTree(acceptedBody).get("jobId").asText());

		assertThat(worker.processNextJob()).isTrue();

		String resultBody = mockMvc.perform(get(JOBS_PATH + "/" + jobId).header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.error.code").value("IMPORT_FAILED"))
			.andExpect(jsonPath("$.error.message").value("쇼핑 링크 정보를 가져오지 못했습니다."))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(resultBody).doesNotContain("upstream-secret-detail");
	}

	@Test
	void recoversStaleRunningJobAndRetriesIt() throws Exception {
		UUID userId = createUser();
		UUID jobId = UUID.randomUUID();
		pageFetcher.stub(SHOP_URL, productHtml());
		OffsetDateTime old = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
		JsonNode request = objectMapper.readTree(requestJson(SHOP_URL));
		jdbcTemplate.update(
			"""
			insert into shopping_import_jobs (
				id, user_id, status, request_json, request_hash, attempt_count, created_at, started_at, updated_at
			) values (?, ?, 'RUNNING', cast(? as jsonb), ?, 1, ?, ?, ?)
			""",
			jobId,
			userId,
			request.toString(),
			"0".repeat(64),
			old,
			old,
			old
		);

		assertThat(worker.recoverStaleJobs()).isEqualTo(1);
		assertThat(worker.processNextJob()).isTrue();

		mockMvc.perform(get(JOBS_PATH + "/" + jobId).header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.attemptCount").value(2));
	}

	@Test
	void failsStaleJobAfterRetryLimitIsExhausted() throws Exception {
		UUID userId = createUser();
		UUID jobId = insertStaleRunningJob(userId, 2, "1".repeat(64));

		assertThat(worker.recoverStaleJobs()).isEqualTo(1);
		assertThat(worker.processNextJob()).isFalse();

		mockMvc.perform(get(JOBS_PATH + "/" + jobId).header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.attemptCount").value(2))
			.andExpect(jsonPath("$.error.code").value("WORKER_INTERRUPTED"));
	}

	@Test
	void propagatesExhaustedStaleLeaderFailureToFollower() throws Exception {
		UUID leaderUserId = createUser();
		UUID followerUserId = createUser();
		UUID leaderJobId = submittedJobId(leaderUserId, MUSINSA_URL, "PENDING");
		UUID followerJobId = submittedJobId(followerUserId, MUSINSA_TRACKING_VARIANT, "PENDING");
		OffsetDateTime old = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
		jdbcTemplate.update(
			"update shopping_import_jobs set status = 'RUNNING', attempt_count = 2, started_at = ?, updated_at = ?",
			old,
			old
		);

		assertThat(worker.recoverStaleJobs()).isEqualTo(1);
		assertThat(worker.processNextJob()).isFalse();

		for (Map.Entry<UUID, UUID> job : Map.of(
			leaderJobId, leaderUserId,
			followerJobId, followerUserId
		).entrySet()) {
			mockMvc.perform(get(JOBS_PATH + "/" + job.getKey()).header(USER_ID_HEADER, job.getValue()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.error.code").value("WORKER_INTERRUPTED"));
		}
	}

	@Test
	void cleansUpTerminalJobsAfterRetentionPeriod() {
		UUID userId = createUser();
		UUID jobId = UUID.randomUUID();
		OffsetDateTime old = OffsetDateTime.now(ZoneOffset.UTC).minusDays(8);
		jdbcTemplate.update(
			"""
			insert into shopping_import_jobs (
				id, user_id, status, request_json, request_hash, result_json, attempt_count,
				created_at, started_at, completed_at, updated_at
			) values (?, ?, 'SUCCEEDED', cast(? as jsonb), ?, cast(? as jsonb), 1, ?, ?, ?, ?)
			""",
			jobId,
			userId,
			requestJson(SHOP_URL),
			"2".repeat(64),
			"{}",
			old,
			old,
			old,
			old
		);

		assertThat(worker.cleanupExpiredJobs()).isEqualTo(1);
		Integer remaining = jdbcTemplate.queryForObject(
			"select count(*) from shopping_import_jobs where id = ?",
			Integer.class,
			jobId
		);
		assertThat(remaining).isZero();
	}

	private UUID insertStaleRunningJob(UUID userId, int attemptCount, String requestHash) {
		UUID jobId = UUID.randomUUID();
		OffsetDateTime old = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
		jdbcTemplate.update(
			"""
			insert into shopping_import_jobs (
				id, user_id, status, request_json, request_hash, attempt_count, created_at, started_at, updated_at
			) values (?, ?, 'RUNNING', cast(? as jsonb), ?, ?, ?, ?, ?)
			""",
			jobId,
			userId,
			requestJson(SHOP_URL),
			requestHash,
			attemptCount,
			old,
			old,
			old
		);
		return jobId;
	}

	private org.springframework.test.web.servlet.ResultActions submit(UUID userId, String url) throws Exception {
		return mockMvc.perform(post(JOBS_PATH)
			.header(USER_ID_HEADER, userId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestJson(url)));
	}

	private UUID submittedJobId(UUID userId, String url, String expectedStatus) throws Exception {
		String body = submit(userId, url)
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value(expectedStatus))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return UUID.fromString(objectMapper.readTree(body).get("jobId").asText());
	}

	private void assertFailedJob(UUID userId, UUID jobId) throws Exception {
		mockMvc.perform(get(JOBS_PATH + "/" + jobId).header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.error.code").value("SHOPPING_PAGE_UNAVAILABLE"));
	}

	private String requestJson(String url) {
		return """
			{
				"inputSource": "SHARE",
				"url": "%s"
			}
			""".formatted(url);
	}

	private UUID createUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', 'COMPLETED', ?, ?)",
			userId,
			now,
			now
		);
		return userId;
	}

	private void awaitPendingJob() throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (System.nanoTime() < deadline) {
			Integer count = jdbcTemplate.queryForObject(
				"select count(*) from shopping_import_jobs where status = 'PENDING'",
				Integer.class
			);
			if (count != null && count > 0) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError("Synchronous import did not enqueue a job");
	}

	private String productHtml() {
		return """
			<html>
			<head>
			  <meta property="og:title" content="Noise Canceling Headphones" />
			  <meta property="og:image" content="https://cdn.example.com/headphones.jpg" />
			  <meta property="product:price:amount" content="129000" />
			</head>
			</html>
			""";
	}

	@TestConfiguration
	static class TestPageFetcherConfig {

		@Bean
		@Primary
		FakeJobPageFetcher fakeJobPageFetcher() {
			return new FakeJobPageFetcher();
		}
	}

	static final class FakeJobPageFetcher implements PageFetcher {

		private final Map<String, FetchedPage> pages = new ConcurrentHashMap<>();
		private final Map<String, RuntimeException> failures = new ConcurrentHashMap<>();
		private final Map<String, AtomicInteger> fetchCounts = new ConcurrentHashMap<>();
		private volatile CyclicBarrier fetchBarrier;

		void reset() {
			pages.clear();
			failures.clear();
			fetchCounts.clear();
			fetchBarrier = null;
		}

		void stub(String url, String body) {
			URI uri = URI.create(url);
			pages.put(url, new FetchedPage(uri, uri, 200, "text/html", body));
		}

		void fail(String url, RuntimeException failure) {
			failures.put(url, failure);
		}

		void waitForConcurrentFetches(int count) {
			fetchBarrier = new CyclicBarrier(count);
		}

		int fetchCount(String url) {
			AtomicInteger count = fetchCounts.get(url);
			return count == null ? 0 : count.get();
		}

		int totalFetchCount() {
			return fetchCounts.values().stream().mapToInt(AtomicInteger::get).sum();
		}

		@Override
		public FetchedPage fetch(URI uri) {
			fetchCounts.computeIfAbsent(uri.toString(), ignored -> new AtomicInteger()).incrementAndGet();
			CyclicBarrier barrier = fetchBarrier;
			if (barrier != null) {
				try {
					barrier.await(2, TimeUnit.SECONDS);
				} catch (Exception exception) {
					throw new AssertionError("Workers did not fetch concurrently", exception);
				}
			}
			RuntimeException failure = failures.get(uri.toString());
			if (failure != null) {
				throw failure;
			}
			FetchedPage page = pages.get(uri.toString());
			if (page == null) {
				throw new AssertionError("No stubbed page for " + uri);
			}
			return page;
		}
	}
}
