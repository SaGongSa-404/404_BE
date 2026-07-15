import { readFile, writeFile } from "node:fs/promises";
import process from "node:process";
import { fileURLToPath } from "node:url";

const METRICS = {
  capacity: {
    peakSystemCpuPct: ["system_cpu_pct", "max"],
    averageSystemCpuPct: ["system_cpu_pct", "avg"],
    peakLoad1m: ["load_1m", "max"],
    peakMemoryUsedMb: ["memory_used_mb", "max"],
    averageMemoryUsedMb: ["memory_used_mb", "avg"],
    minimumMemoryAvailableMb: ["memory_available_mb", "min"],
    peakSwapUsedMb: ["swap_used_mb", "max"],
    peakDiskUsedPct: ["disk_used_pct", "max"],
    minimumDiskAvailableMb: ["disk_available_mb", "min"],
    peakCpuPressureAvg10: ["cpu_pressure_avg10", "max"],
    peakMemoryPressureAvg10: ["memory_pressure_avg10", "max"]
  },
  io: {
    peakDiskReadBps: ["disk_read_bps", "max"],
    averageDiskReadBps: ["disk_read_bps", "avg"],
    peakDiskWriteBps: ["disk_write_bps", "max"],
    averageDiskWriteBps: ["disk_write_bps", "avg"],
    peakNetworkRxBps: ["network_rx_bps", "max"],
    averageNetworkRxBps: ["network_rx_bps", "avg"],
    peakNetworkTxBps: ["network_tx_bps", "max"],
    averageNetworkTxBps: ["network_tx_bps", "avg"],
    peakIoPressureAvg10: ["io_pressure_avg10", "max"]
  },
  java: {
    peakCpuPct: ["java_cpu_pct", "max"],
    averageCpuPct: ["java_cpu_pct", "avg"],
    peakRssMb: ["java_rss_mb", "max"],
    peakThreads: ["java_threads", "max"],
    peakOpenFds: ["java_fds", "max"],
    peakReadBps: ["java_read_bps", "max"],
    peakWriteBps: ["java_write_bps", "max"]
  },
  chromium: {
    peakProcesses: ["chromium_processes", "max"],
    peakCpuPct: ["chromium_cpu_pct", "max"],
    averageCpuPct: ["chromium_cpu_pct", "avg"],
    peakRssMb: ["chromium_rss_mb", "max"]
  },
  queueAndDatabase: {
    peakPendingJobs: ["queue_pending", "max"],
    peakRunningJobs: ["queue_running", "max"],
    peakActiveConnections: ["db_active_connections", "max"],
    peakIdleConnections: ["db_idle_connections", "max"],
    peakDatabaseSizeMb: ["db_size_mb", "max"],
    peakStoredShoppingJobs: ["shopping_jobs_total", "max"]
  }
};

export function parseCsv(content) {
  const lines = content.trim().split(/\r?\n/).filter(Boolean);
  if (lines.length < 2) throw new Error("Server metrics CSV requires a header and at least one sample");
  const headers = lines[0].split(",");
  return lines.slice(1).map((line) => Object.fromEntries(
    line.split(",").map((value, index) => [headers[index], value])
  ));
}

export function summarizeServerMetrics(rows) {
  const startedAt = rows[0].timestamp;
  const completedAt = rows.at(-1).timestamp;
  const summary = {
    schemaVersion: 1,
    samples: rows.length,
    startedAt,
    completedAt,
    elapsedSeconds: Math.max(0, (Date.parse(completedAt) - Date.parse(startedAt)) / 1000),
    service: {
      pid: numberOrNull(rows.at(-1).service_pid),
      restartCountStart: numberOrNull(rows[0].service_restarts),
      restartCountEnd: numberOrNull(rows.at(-1).service_restarts),
      restartedDuringRun: numberOrNull(rows[0].service_restarts) !== numberOrNull(rows.at(-1).service_restarts)
    },
    storageChange: {
      diskUsedStartMb: numberOrNull(rows[0].disk_used_mb),
      diskUsedEndMb: numberOrNull(rows.at(-1).disk_used_mb),
      diskUsedDeltaMb: difference(rows[0].disk_used_mb, rows.at(-1).disk_used_mb),
      databaseSizeStartMb: numberOrNull(rows[0].db_size_mb),
      databaseSizeEndMb: numberOrNull(rows.at(-1).db_size_mb),
      databaseSizeDeltaMb: difference(rows[0].db_size_mb, rows.at(-1).db_size_mb),
      shoppingJobsStart: numberOrNull(rows[0].shopping_jobs_total),
      shoppingJobsEnd: numberOrNull(rows.at(-1).shopping_jobs_total),
      shoppingJobsDelta: difference(rows[0].shopping_jobs_total, rows.at(-1).shopping_jobs_total)
    }
  };

  for (const [section, definitions] of Object.entries(METRICS)) {
    summary[section] = {};
    for (const [outputName, [column, operation]] of Object.entries(definitions)) {
      const values = rows.map((row) => Number(row[column])).filter(Number.isFinite);
      summary[section][outputName] = aggregate(values, operation);
    }
  }
  return summary;
}

function aggregate(values, operation) {
  if (values.length === 0) return null;
  if (operation === "min") return Math.min(...values);
  if (operation === "max") return Math.max(...values);
  return Math.round(values.reduce((sum, value) => sum + value, 0) / values.length * 100) / 100;
}

function numberOrNull(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function difference(start, end) {
  const startNumber = numberOrNull(start);
  const endNumber = numberOrNull(end);
  if (startNumber === null || endNumber === null) return null;
  return Math.round((endNumber - startNumber) * 100) / 100;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const inputFile = process.argv[2];
  const outputFile = process.argv[3];
  if (!inputFile || !outputFile) {
    throw new Error("Usage: node summarize-server-metrics.mjs <server-metrics.csv> <server-summary.json>");
  }
  const rows = parseCsv(await readFile(inputFile, "utf8"));
  const summary = summarizeServerMetrics(rows);
  await writeFile(outputFile, `${JSON.stringify(summary, null, 2)}\n`, "utf8");
  console.log(`SERVER_SUMMARY_FILE=${outputFile}`);
}
