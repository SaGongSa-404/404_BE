import test from "node:test";
import assert from "node:assert/strict";
import { durationBetween, errorRate, percentile, summarizeDurations } from "./metrics.mjs";

test("percentile uses nearest-rank semantics", () => {
  assert.equal(percentile([40, 10, 30, 20], 50), 20);
  assert.equal(percentile([40, 10, 30, 20], 95), 40);
  assert.equal(percentile([], 95), null);
});

test("duration summary contains stable percentiles", () => {
  assert.deepEqual(summarizeDurations([10, 20, 30, 40]), {
    count: 4,
    minMs: 10,
    p50Ms: 20,
    p95Ms: 40,
    p99Ms: 40,
    maxMs: 40,
    avgMs: 25
  });
});

test("timestamp duration and error rate are safe", () => {
  assert.equal(durationBetween("2026-07-14T00:00:00Z", "2026-07-14T00:00:02Z"), 2000);
  assert.equal(durationBetween(null, "2026-07-14T00:00:02Z"), null);
  assert.equal(errorRate(9, 1), 0.1);
  assert.equal(errorRate(0, 0), 0);
});
