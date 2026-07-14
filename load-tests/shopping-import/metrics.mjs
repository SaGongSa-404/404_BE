export function percentile(values, percentage) {
  if (values.length === 0) {
    return null;
  }
  const sorted = [...values].sort((left, right) => left - right);
  const index = Math.max(0, Math.ceil((percentage / 100) * sorted.length) - 1);
  return round(sorted[index]);
}

export function summarizeDurations(values) {
  if (values.length === 0) {
    return { count: 0, minMs: null, p50Ms: null, p95Ms: null, p99Ms: null, maxMs: null, avgMs: null };
  }
  const total = values.reduce((sum, value) => sum + value, 0);
  return {
    count: values.length,
    minMs: round(Math.min(...values)),
    p50Ms: percentile(values, 50),
    p95Ms: percentile(values, 95),
    p99Ms: percentile(values, 99),
    maxMs: round(Math.max(...values)),
    avgMs: round(total / values.length)
  };
}

export function durationBetween(start, end) {
  if (!start || !end) {
    return null;
  }
  const startMs = Date.parse(start);
  const endMs = Date.parse(end);
  return Number.isFinite(startMs) && Number.isFinite(endMs) ? Math.max(0, endMs - startMs) : null;
}

export function errorRate(successes, failures) {
  const total = successes + failures;
  return total === 0 ? 0 : round(failures / total, 4);
}

function round(value, digits = 2) {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}
