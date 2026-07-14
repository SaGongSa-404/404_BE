import test from "node:test";
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { createServer } from "node:http";
import { mkdtemp, readFile, readdir, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import { fileURLToPath } from "node:url";

const execFileAsync = promisify(execFile);
const directory = path.dirname(fileURLToPath(import.meta.url));

test("async scenario records queue metrics without persisting tokens", async (context) => {
  const jobs = new Map();
  let sequence = 0;
  const server = createServer((request, response) => {
    response.setHeader("Content-Type", "application/json");
    if (request.method === "GET" && request.url === "/api/auth/me") {
      response.end('{"id":"load-test-user"}');
      return;
    }
    if (request.method === "POST" && request.url === "/api/v1/items/import-jobs") {
      const jobId = `00000000-0000-0000-0000-${String(++sequence).padStart(12, "0")}`;
      const submittedAt = new Date().toISOString();
      jobs.set(jobId, submittedAt);
      response.statusCode = 202;
      response.end(JSON.stringify({ jobId, status: "PENDING", submittedAt }));
      return;
    }
    const match = request.url?.match(/^\/api\/v1\/items\/import-jobs\/(.+)$/);
    if (request.method === "GET" && match && jobs.has(match[1])) {
      const submittedAt = jobs.get(match[1]);
      const startedAt = new Date(Date.parse(submittedAt) + 10).toISOString();
      const completedAt = new Date(Date.parse(submittedAt) + 20).toISOString();
      response.end(JSON.stringify({
        jobId: match[1],
        status: "SUCCEEDED",
        result: {
          retrievalStatus: "SUCCESS",
          item: {
            title: "검증 상품",
            listedPrice: 125000,
            currencyCode: "KRW",
            imageUrl: "https://images.shop.test/product.jpg"
          }
        },
        attemptCount: 1,
        submittedAt,
        startedAt,
        completedAt
      }));
      return;
    }
    response.statusCode = 404;
    response.end('{"message":"not found"}');
  });

  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(() => server.close());
  const port = server.address().port;
  const tempDirectory = await mkdtemp(path.join(tmpdir(), "wigul-load-test-"));
  const urlFile = path.join(tempDirectory, "urls.txt");
  const tokenFile = path.join(tempDirectory, "reviewer.token");
  const expectedResultsFile = path.join(tempDirectory, "expected-results.json");
  await writeFile(
    urlFile,
    "https://shop.test/products/reviewer-load-test\n",
    "utf8"
  );
  await writeFile(tokenFile, "single-reviewer-access-token\n", { encoding: "utf8", mode: 0o600 });
  await writeFile(expectedResultsFile, JSON.stringify([{
    url: "https://shop.test/products/reviewer-load-test",
    title: "검증 상품",
    listedPrice: 125000,
    currencyCode: "KRW",
    imageUrl: "https://images.shop.test/product.jpg"
  }]), "utf8");

  await execFileAsync(process.execPath, ["run.mjs"], {
    cwd: directory,
    env: {
      ...process.env,
      BASE_URL: `http://127.0.0.1:${port}`,
      CONFIRM_QA_LOAD_TEST: "YES",
      ACCESS_TOKEN_FILE: tokenFile,
      SINGLE_USER_MODE: "YES",
      EXPAND_SINGLE_USER_URLS: "YES",
      EXPECTED_MAX_ACTIVE_PER_USER: "10",
      URL_FILE: urlFile,
      EXPECTED_RESULTS_FILE: expectedResultsFile,
      RESULT_DIR: tempDirectory,
      SCENARIO: "baseline",
      IMPORT_MODE: "async",
      DURATION_SECONDS: "1",
      RAMP_UP_SECONDS: "1",
      POLL_INTERVAL_MS: "10",
      JOB_TIMEOUT_MS: "1000",
      REQUEST_TIMEOUT_MS: "1000"
    },
    timeout: 15_000
  });

  const reportFile = (await readdir(tempDirectory)).find((name) => name.endsWith("-baseline-async.json"));
  assert.ok(reportFile);
  const rawReport = await readFile(path.join(tempDirectory, reportFile), "utf8");
  const report = JSON.parse(rawReport);
  assert.equal(report.summary.import.accepted, 10);
  assert.equal(report.summary.import.succeeded, 10);
  assert.equal(report.summary.import.failed, 0);
  assert.equal(report.summary.import.queueWait.count, 10);
  assert.ok(report.summary.general.successes > 0);
  assert.equal(rawReport.includes("single-reviewer-access-token"), false);
  assert.equal(report.run.authenticationMode, "single-user");
  assert.equal(report.run.tokenSource, "file");
  assert.equal(report.run.expandedSingleUserUrls, true);
  assert.equal(report.run.correctnessOracle, "exact-title-price-currency-imageUrl");
  assert.deepEqual(report.summary.import.correctnessFailureFields, {});
  assert.deepEqual(report.summary.import.correctnessMismatches, []);
});

test("successful jobs fail the run when product fields do not exactly match the source oracle", async (context) => {
  const server = createServer((request, response) => {
    response.setHeader("Content-Type", "application/json");
    if (request.method === "POST" && request.url === "/api/v1/items/import-link") {
      response.end(JSON.stringify({
        retrievalStatus: "SUCCESS",
        item: { title: "다른 상품", listedPrice: 1, currencyCode: "KRW", imageUrl: "https://images.shop.test/wrong.jpg" }
      }));
      return;
    }
    response.statusCode = 404;
    response.end('{"message":"not found"}');
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(() => server.close());

  const tempDirectory = await mkdtemp(path.join(tmpdir(), "wigul-load-test-mismatch-"));
  const urlFile = path.join(tempDirectory, "urls.txt");
  const expectedResultsFile = path.join(tempDirectory, "expected-results.json");
  await writeFile(urlFile, "https://shop.test/products/1\n", "utf8");
  await writeFile(expectedResultsFile, JSON.stringify([{
    url: "https://shop.test/products/1",
    title: "원본 상품",
    listedPrice: 125000,
    currencyCode: "KRW",
    imageUrl: "https://images.shop.test/product.jpg"
  }]), "utf8");

  await assert.rejects(execFileAsync(process.execPath, ["run.mjs"], {
    cwd: directory,
    env: {
      ...process.env,
      BASE_URL: `http://127.0.0.1:${server.address().port}`,
      CONFIRM_QA_LOAD_TEST: "YES",
      ACCESS_TOKEN: "test-token",
      URL_FILE: urlFile,
      EXPECTED_RESULTS_FILE: expectedResultsFile,
      RESULT_DIR: tempDirectory,
      SCENARIO: "baseline",
      IMPORT_MODE: "sync",
      DURATION_SECONDS: "1",
      RAMP_UP_SECONDS: "1",
      REQUEST_TIMEOUT_MS: "1000"
    },
    timeout: 15_000
  }), (error) => error.code === 2);

  const reportFile = (await readdir(tempDirectory)).find((name) => name.endsWith("-baseline-sync.json"));
  const report = JSON.parse(await readFile(path.join(tempDirectory, reportFile), "utf8"));
  assert.equal(report.summary.import.succeeded, 0);
  assert.equal(report.summary.import.failed, 10);
  assert.deepEqual(report.summary.import.correctnessFailureFields, {
    title: 10,
    listedPrice: 10,
    imageUrl: 10
  });
  assert.equal(report.summary.import.correctnessMismatches.length, 10);
});

test("single-user mode rejects an active-job limit below the scenario demand", async () => {
  const tempDirectory = await mkdtemp(path.join(tmpdir(), "wigul-load-test-limit-"));
  const urlFile = path.join(tempDirectory, "urls.txt");
  const tokenFile = path.join(tempDirectory, "reviewer.token");
  const expectedResultsFile = path.join(tempDirectory, "expected-results.json");
  await writeFile(urlFile, "https://shop.test/products/1\n", "utf8");
  await writeFile(tokenFile, "single-reviewer-access-token\n", { encoding: "utf8", mode: 0o600 });
  await writeFile(expectedResultsFile, JSON.stringify([{
    url: "https://shop.test/products/1",
    title: "상품",
    listedPrice: 1,
    currencyCode: "KRW",
    imageUrl: "https://images.shop.test/1.jpg"
  }]), "utf8");

  await assert.rejects(
    execFileAsync(process.execPath, ["run.mjs"], {
      cwd: directory,
      env: {
        ...process.env,
        BASE_URL: "http://127.0.0.1:9",
        CONFIRM_QA_LOAD_TEST: "YES",
        ACCESS_TOKEN_FILE: tokenFile,
        SINGLE_USER_MODE: "YES",
        EXPECTED_MAX_ACTIVE_PER_USER: "3",
        URL_FILE: urlFile,
        EXPECTED_RESULTS_FILE: expectedResultsFile,
        SCENARIO: "baseline",
        IMPORT_MODE: "async"
      },
      timeout: 5_000
    }),
    (error) => error.stderr.includes("EXPECTED_MAX_ACTIVE_PER_USER>=10")
  );
});

test("single-user mode rejects URL reuse that would deduplicate active jobs", async () => {
  const tempDirectory = await mkdtemp(path.join(tmpdir(), "wigul-load-test-urls-"));
  const urlFile = path.join(tempDirectory, "urls.txt");
  const tokenFile = path.join(tempDirectory, "reviewer.token");
  const expectedResultsFile = path.join(tempDirectory, "expected-results.json");
  await writeFile(urlFile, "https://shop.test/products/1\n", "utf8");
  await writeFile(tokenFile, "single-reviewer-access-token\n", { encoding: "utf8", mode: 0o600 });
  await writeFile(expectedResultsFile, JSON.stringify([{
    url: "https://shop.test/products/1",
    title: "상품",
    listedPrice: 1,
    currencyCode: "KRW",
    imageUrl: "https://images.shop.test/1.jpg"
  }]), "utf8");

  await assert.rejects(
    execFileAsync(process.execPath, ["run.mjs"], {
      cwd: directory,
      env: {
        ...process.env,
        BASE_URL: "http://127.0.0.1:9",
        CONFIRM_QA_LOAD_TEST: "YES",
        ACCESS_TOKEN_FILE: tokenFile,
        SINGLE_USER_MODE: "YES",
        EXPECTED_MAX_ACTIVE_PER_USER: "10",
        URL_FILE: urlFile,
        EXPECTED_RESULTS_FILE: expectedResultsFile,
        SCENARIO: "baseline",
        IMPORT_MODE: "async"
      },
      timeout: 5_000
    }),
    (error) => error.stderr.includes("at least 10 distinct URLs")
  );
});
