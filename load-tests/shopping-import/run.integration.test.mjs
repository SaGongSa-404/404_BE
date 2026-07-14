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
        result: { retrievalStatus: "SUCCESS" },
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
  await writeFile(
    urlFile,
    Array.from({ length: 10 }, (_, index) => `https://shop.test/products/${index + 1}`).join("\n"),
    "utf8"
  );

  await execFileAsync(process.execPath, ["run.mjs"], {
    cwd: directory,
    env: {
      ...process.env,
      BASE_URL: `http://127.0.0.1:${port}`,
      CONFIRM_QA_LOAD_TEST: "YES",
      ACCESS_TOKENS: "token-a,token-b,token-c,token-d",
      URL_FILE: urlFile,
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
  assert.equal(rawReport.includes("token-a"), false);
});
