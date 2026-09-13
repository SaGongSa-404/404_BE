import test from "node:test";
import assert from "node:assert/strict";
import { parseCsv, summarizeServerMetrics } from "./summarize-server-metrics.mjs";

test("server metrics summary reports capacity and process peaks", () => {
  const rows = parseCsv([
    "timestamp,service_pid,service_restarts,system_cpu_pct,load_1m,memory_used_mb,memory_available_mb,swap_used_mb,disk_used_mb,disk_used_pct,disk_available_mb,disk_read_bps,disk_write_bps,network_rx_bps,network_tx_bps,io_pressure_avg10,java_cpu_pct,java_rss_mb,java_threads,java_fds,java_read_bps,java_write_bps,chromium_processes,chromium_cpu_pct,chromium_rss_mb,queue_pending,queue_running,db_active_connections,db_idle_connections,db_size_mb,shopping_jobs_total",
    "2026-07-15T10:00:00+09:00,10,0,20,0.5,1000,2900,50,12000,64,6800,100,200,300,400,0.1,30,600,50,100,10,20,5,40,350,20,3,2,8,120,10",
    "2026-07-15T10:00:05+09:00,10,0,80,2.5,1800,2100,80,12020,65,6600,500,900,700,800,1.2,90,800,70,140,30,50,8,120,700,70,3,4,12,130,80"
  ].join("\n"));

  const summary = summarizeServerMetrics(rows);
  assert.equal(summary.samples, 2);
  assert.equal(summary.elapsedSeconds, 5);
  assert.equal(summary.service.restartedDuringRun, false);
  assert.equal(summary.capacity.peakSystemCpuPct, 80);
  assert.equal(summary.capacity.minimumMemoryAvailableMb, 2100);
  assert.equal(summary.java.averageCpuPct, 60);
  assert.equal(summary.chromium.peakRssMb, 700);
  assert.equal(summary.queueAndDatabase.peakPendingJobs, 70);
  assert.equal(summary.storageChange.diskUsedDeltaMb, 20);
  assert.equal(summary.storageChange.databaseSizeDeltaMb, 10);
  assert.equal(summary.storageChange.shoppingJobsDelta, 70);
});
