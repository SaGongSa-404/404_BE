import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { durationBetween, errorRate, summarizeDurations } from "./metrics.mjs";

const SCENARIOS = {
  baseline: { totalVus: 100, crawlVus: 10, crawlRequests: 10, generalVus: 90 },
  peak: { totalVus: 100, crawlVus: 20, crawlRequests: 20, generalVus: 80 },
  backpressure: { totalVus: 100, crawlVus: 100, crawlRequests: 101, generalVus: 0 }
};
const TERMINAL = new Set(["SUCCEEDED", "FAILED"]);
const SAFE_HOSTS = new Set(["34-66-55-165.sslip.io", "localhost", "127.0.0.1"]);
const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

const args = new Set(process.argv.slice(2));
const dryRun = args.has("--dry-run");
const scenarioName = env("SCENARIO", "baseline");
const importMode = env("IMPORT_MODE", "async");
const baseUrl = env("BASE_URL", "https://34-66-55-165.sslip.io").replace(/\/$/, "");
const durationSeconds = positiveInt("DURATION_SECONDS", 60);
const rampUpSeconds = positiveInt("RAMP_UP_SECONDS", scenarioName === "backpressure" ? 1 : 30);
const pollIntervalMs = positiveInt("POLL_INTERVAL_MS", 1000);
const jobTimeoutMs = positiveInt("JOB_TIMEOUT_MS", 90_000);
const requestTimeoutMs = positiveInt("REQUEST_TIMEOUT_MS", 40_000);
const generalEndpoint = env("GENERAL_ENDPOINT", "/api/auth/me");
const urlFile = path.resolve(env("URL_FILE", "urls.example.txt"));
const outputDir = path.resolve(env("RESULT_DIR", "results"));
const tokens = parseTokens(process.env.ACCESS_TOKENS || process.env.ACCESS_TOKEN || "");
const scenario = SCENARIOS[scenarioName];

validateConfig();
const urls = await readUrls(urlFile);
validateExecutionInputs(urls);
const plan = sanitizedPlan();

if (dryRun) {
  console.log(JSON.stringify({ dryRun: true, ...plan, urlCount: urls.length, tokenCount: tokens.length }, null, 2));
  process.exit(0);
}

const metrics = createMetrics();
const startedAt = new Date();
console.log(`[NF-84] start scenario=${scenarioName} mode=${importMode} totalVus=${scenario.totalVus}`);

const generalPromise = runGeneralTraffic();
await sleep(Math.min(2_000, rampUpSeconds * 1000));
const crawlPromise = runCrawlTraffic();
await Promise.all([generalPromise, crawlPromise]);

const completedAt = new Date();
const report = buildReport(startedAt, completedAt);
await mkdir(outputDir, { recursive: true });
const fileName = `${fileTimestamp(startedAt)}-${scenarioName}-${importMode}.json`;
const outputPath = path.join(outputDir, fileName);
await writeFile(outputPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
console.log(JSON.stringify(report.summary, null, 2));
console.log(`[NF-84] result=${outputPath}`);

if (report.summary.general.errorRate > 0.01 || metrics.unexpectedErrors.length > 0) {
  process.exitCode = 2;
}

async function runGeneralTraffic() {
  const deadline = Date.now() + durationSeconds * 1000;
  const workers = Array.from({ length: scenario.generalVus }, (_, index) => (async () => {
    await sleep(rampDelay(index, scenario.generalVus));
    while (Date.now() < deadline) {
      const token = tokens[index % tokens.length];
      const sample = await request("GET", generalEndpoint, token);
      metrics.general.durations.push(sample.durationMs);
      sample.ok ? metrics.general.successes++ : metrics.general.failures++;
      if (!sample.ok) {
        metrics.statuses[sample.status] = (metrics.statuses[sample.status] || 0) + 1;
      }
      await sleep(1000);
    }
  })());
  await Promise.all(workers);
}

async function runCrawlTraffic() {
  const workers = Array.from({ length: scenario.crawlRequests }, (_, index) => (async () => {
    await sleep(rampDelay(index, scenario.crawlRequests));
    const token = tokens[index % tokens.length];
    const url = urls[index % urls.length];
    if (importMode === "sync") {
      await runSyncImport(token, url);
    } else {
      await runAsyncImport(token, url);
    }
  })());
  await Promise.all(workers);
}

async function runSyncImport(token, url) {
  const sample = await request("POST", "/api/v1/items/import-link", token, importPayload(url));
  metrics.import.submitDurations.push(sample.durationMs);
  metrics.statuses[sample.status] = (metrics.statuses[sample.status] || 0) + 1;
  if (sample.status === 200) {
    metrics.import.succeeded++;
  } else {
    metrics.import.failed++;
    metrics.unexpectedErrors.push({ phase: "sync", status: sample.status, message: sample.error });
  }
}

async function runAsyncImport(token, url) {
  const submitted = await request("POST", "/api/v1/items/import-jobs", token, importPayload(url));
  metrics.import.submitDurations.push(submitted.durationMs);
  metrics.statuses[submitted.status] = (metrics.statuses[submitted.status] || 0) + 1;
  if (submitted.status === 429) {
    metrics.import.backpressure++;
    return;
  }
  if (submitted.status !== 202 || !submitted.body?.jobId) {
    metrics.import.failed++;
    metrics.unexpectedErrors.push({ phase: "submit", status: submitted.status, message: submitted.error });
    return;
  }

  metrics.import.accepted++;
  metrics.import.jobIds.add(submitted.body.jobId);
  const deadline = Date.now() + jobTimeoutMs;
  while (Date.now() < deadline) {
    await sleep(pollIntervalMs);
    const polled = await request("GET", `/api/v1/items/import-jobs/${submitted.body.jobId}`, token);
    metrics.import.pollDurations.push(polled.durationMs);
    if (polled.status !== 200 || !polled.body?.status) {
      metrics.import.failed++;
      metrics.unexpectedErrors.push({ phase: "poll", status: polled.status, message: polled.error });
      return;
    }
    if (!TERMINAL.has(polled.body.status)) {
      continue;
    }
    captureJobDurations(polled.body);
    if (polled.body.status === "SUCCEEDED") {
      metrics.import.succeeded++;
    } else {
      metrics.import.failed++;
      const code = polled.body.error?.code || "UNKNOWN";
      metrics.failureCodes[code] = (metrics.failureCodes[code] || 0) + 1;
    }
    return;
  }
  metrics.import.timedOut++;
  metrics.import.failed++;
}

function captureJobDurations(job) {
  const queueWait = durationBetween(job.submittedAt, job.startedAt);
  const processing = durationBetween(job.startedAt, job.completedAt);
  const total = durationBetween(job.submittedAt, job.completedAt);
  if (queueWait !== null) metrics.import.queueWaitDurations.push(queueWait);
  if (processing !== null) metrics.import.processingDurations.push(processing);
  if (total !== null) metrics.import.totalDurations.push(total);
}

async function request(method, endpoint, token, body) {
  const start = performance.now();
  try {
    const response = await fetch(`${baseUrl}${endpoint}`, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        ...(body ? { "Content-Type": "application/json" } : {})
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(requestTimeoutMs)
    });
    const text = await response.text();
    let parsed = null;
    try {
      parsed = text ? JSON.parse(text) : null;
    } catch {
      parsed = null;
    }
    return {
      ok: response.ok,
      status: response.status,
      durationMs: performance.now() - start,
      body: parsed,
      error: response.ok ? null : parsed?.message || `HTTP ${response.status}`
    };
  } catch (error) {
    return { ok: false, status: 0, durationMs: performance.now() - start, body: null, error: error.name };
  }
}

function buildReport(started, completed) {
  return {
    schemaVersion: 1,
    run: {
      ...plan,
      startedAt: started.toISOString(),
      completedAt: completed.toISOString(),
      elapsedMs: completed - started,
      tokenCount: tokens.length,
      urlCount: urls.length
    },
    summary: {
      general: {
        ...summarizeDurations(metrics.general.durations),
        successes: metrics.general.successes,
        failures: metrics.general.failures,
        errorRate: errorRate(metrics.general.successes, metrics.general.failures)
      },
      import: {
        accepted: metrics.import.accepted,
        uniqueJobs: metrics.import.jobIds.size,
        succeeded: metrics.import.succeeded,
        failed: metrics.import.failed,
        timedOut: metrics.import.timedOut,
        backpressure429: metrics.import.backpressure,
        submit: summarizeDurations(metrics.import.submitDurations),
        poll: summarizeDurations(metrics.import.pollDurations),
        queueWait: summarizeDurations(metrics.import.queueWaitDurations),
        processing: summarizeDurations(metrics.import.processingDurations),
        total: summarizeDurations(metrics.import.totalDurations)
      },
      responseStatuses: metrics.statuses,
      failureCodes: metrics.failureCodes,
      unexpectedErrors: metrics.unexpectedErrors.slice(0, 20)
    }
  };
}

function createMetrics() {
  return {
    general: { durations: [], successes: 0, failures: 0 },
    import: {
      accepted: 0,
      succeeded: 0,
      failed: 0,
      timedOut: 0,
      backpressure: 0,
      jobIds: new Set(),
      submitDurations: [],
      pollDurations: [],
      queueWaitDurations: [],
      processingDurations: [],
      totalDurations: []
    },
    statuses: {},
    failureCodes: {},
    unexpectedErrors: []
  };
}

function sanitizedPlan() {
  return {
    baseUrl,
    scenario: scenarioName,
    importMode,
    totalVus: scenario.totalVus,
    generalVus: scenario.generalVus,
    crawlVus: scenario.crawlVus,
    crawlRequests: scenario.crawlRequests,
    durationSeconds,
    rampUpSeconds,
    pollIntervalMs,
    jobTimeoutMs,
    generalEndpoint
  };
}

function validateConfig() {
  if (!scenario) throw new Error(`SCENARIO must be one of: ${Object.keys(SCENARIOS).join(", ")}`);
  if (!new Set(["sync", "async"]).has(importMode)) throw new Error("IMPORT_MODE must be sync or async");
  const parsedUrl = new URL(baseUrl);
  if (!SAFE_HOSTS.has(parsedUrl.hostname) && process.env.ALLOW_LOAD_TEST_HOST !== "YES") {
    throw new Error(`Refusing unapproved load-test host: ${parsedUrl.hostname}`);
  }
  if (!dryRun && process.env.CONFIRM_QA_LOAD_TEST !== "YES") {
    throw new Error("Set CONFIRM_QA_LOAD_TEST=YES after confirming the QA target");
  }
  if (!dryRun && tokens.length === 0) throw new Error("ACCESS_TOKEN or ACCESS_TOKENS is required");
  if (!dryRun && scenario.crawlRequests > 20 && process.env.CONFIRM_EXTERNAL_TRAFFIC !== "YES") {
    throw new Error("More than 20 crawl requests requires CONFIRM_EXTERNAL_TRAFFIC=YES");
  }
}

function validateExecutionInputs(values) {
  if (dryRun) {
    return;
  }
  if (values.some((value) => new URL(value).hostname === "example.com")) {
    throw new Error("Replace urls.example.txt placeholders with a QA-approved URL_FILE");
  }
  if (importMode === "async") {
    const minimumUsers = Math.ceil(scenario.crawlRequests / 3);
    if (tokens.length < minimumUsers) {
      throw new Error(
        `Async ${scenarioName} requires at least ${minimumUsers} distinct-user tokens `
        + "to avoid measuring only the per-user active-job limit"
      );
    }
  }
}

async function readUrls(file) {
  const content = await readFile(file, "utf8");
  const values = content.split(/\r?\n/).map((value) => value.trim())
    .filter((value) => value && !value.startsWith("#"));
  if (values.length === 0) throw new Error(`No URLs found in ${file}`);
  values.forEach((value) => new URL(value));
  return values;
}

function parseTokens(value) {
  return value.split(/[\r\n,]+/).map((token) => token.trim()).filter(Boolean);
}

function importPayload(url) {
  return { inputSource: "SHARE", url, title: null, brandName: null, price: null, imageUrl: null };
}

function rampDelay(index, count) {
  return count <= 1 ? 0 : Math.round((index / (count - 1)) * rampUpSeconds * 1000);
}

function env(name, fallback) {
  return process.env[name]?.trim() || fallback;
}

function positiveInt(name, fallback) {
  const value = Number.parseInt(env(name, String(fallback)), 10);
  if (!Number.isInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer`);
  return value;
}

function fileTimestamp(date) {
  return date.toISOString().replace(/[:.]/g, "-");
}
