#!/usr/bin/env bash
set -euo pipefail

INTERVAL_SECONDS="${INTERVAL_SECONDS:-5}"
DURATION_SECONDS="${DURATION_SECONDS:-180}"
OUTPUT_FILE="${OUTPUT_FILE:-server-metrics.csv}"
SERVICE_NAME="${SERVICE_NAME:-wigul-backend}"
STOP_FILE="${STOP_FILE:-}"

if ! [[ "$INTERVAL_SECONDS" =~ ^[1-9][0-9]*$ && "$DURATION_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "INTERVAL_SECONDS and DURATION_SECONDS must be positive integers" >&2
  exit 1
fi

PID="$(systemctl show "$SERVICE_NAME" -p MainPID --value)"
if [[ -z "$PID" || "$PID" == "0" || ! -r "/proc/$PID/status" ]]; then
  echo "Cannot read MainPID for $SERVICE_NAME: $PID" >&2
  exit 1
fi

clock_ticks="$(getconf CLK_TCK)"
root_device="$(findmnt -no SOURCE / 2>/dev/null | sed 's#^/dev/##')"

system_cpu_snapshot() {
  awk '/^cpu / { idle=$5+$6; total=0; for (i=2; i<=NF; i++) total+=$i; print total, idle; exit }' /proc/stat
}

process_ticks() {
  local pid="$1"
  [[ -r "/proc/$pid/stat" ]] && awk '{ print $14 + $15 }' "/proc/$pid/stat" || echo 0
}

process_io() {
  local pid="$1" key="$2"
  [[ -r "/proc/$pid/io" ]] && awk -v key="$key" '$1 == key":" { print $2; found=1 } END { if (!found) print 0 }' "/proc/$pid/io" || echo 0
}

disk_io_snapshot() {
  awk -v device="$root_device" '$3 == device { print $6 * 512, $10 * 512; found=1; exit }
    END { if (!found) print "0 0" }' /proc/diskstats
}

network_snapshot() {
  awk -F'[: ]+' '$2 != "lo" && NF >= 11 { rx += $3; tx += $11 } END { print rx + 0, tx + 0 }' /proc/net/dev
}

pressure_avg10() {
  local resource="$1"
  [[ -r "/proc/pressure/$resource" ]] || { echo 0; return; }
  awk '/^some / { for (i=1; i<=NF; i++) if ($i ~ /^avg10=/) { split($i, value, "="); print value[2]; exit } }' "/proc/pressure/$resource"
}

rate_per_second() {
  local current="$1" previous="$2" elapsed_ms="$3"
  awk -v current="$current" -v previous="$previous" -v elapsed="$elapsed_ms" \
    'BEGIN { delta=current-previous; if (delta < 0 || elapsed <= 0) delta=0; printf "%.2f", delta * 1000 / elapsed }'
}

cpu_percent() {
  local current="$1" previous="$2" elapsed_ms="$3"
  awk -v current="$current" -v previous="$previous" -v elapsed="$elapsed_ms" -v hz="$clock_ticks" \
    'BEGIN { delta=current-previous; if (delta < 0 || elapsed <= 0) delta=0; printf "%.2f", delta / hz * 100000 / elapsed }'
}

database_counts() {
  local result=""
  if command -v psql >/dev/null 2>&1; then
    if [[ -n "${SPRING_DATASOURCE_URL:-}" && -n "${SPRING_DATASOURCE_USERNAME:-}" ]]; then
      local database_url="${SPRING_DATASOURCE_URL#jdbc:}"
      result="$(PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:-}" psql -X -Atq "$database_url" \
        -U "$SPRING_DATASOURCE_USERNAME" -c \
        "select count(*) filter (where status='PENDING'),
                count(*) filter (where status='RUNNING'),
                (select count(*) from pg_stat_activity where datname=current_database() and state='active'),
                (select count(*) from pg_stat_activity where datname=current_database() and state='idle'),
                round(pg_database_size(current_database()) / 1024.0 / 1024.0, 2),
                count(*)
           from shopping_import_jobs" 2>/dev/null || true)"
    elif [[ -n "${PGHOST:-}" && -n "${PGDATABASE:-}" && -n "${PGUSER:-}" ]]; then
      result="$(psql -X -Atq -c \
        "select count(*) filter (where status='PENDING'),
                count(*) filter (where status='RUNNING'),
                (select count(*) from pg_stat_activity where datname=current_database() and state='active'),
                (select count(*) from pg_stat_activity where datname=current_database() and state='idle'),
                round(pg_database_size(current_database()) / 1024.0 / 1024.0, 2),
                count(*)
           from shopping_import_jobs" 2>/dev/null || true)"
    fi
  fi
  [[ -n "$result" ]] && echo "$result" || echo "|||||"
}

header="timestamp,service_pid,service_restarts,system_cpu_pct,load_1m,load_5m,load_15m"
header+=",memory_total_mb,memory_used_mb,memory_available_mb,swap_used_mb"
header+=",disk_total_mb,disk_used_mb,disk_available_mb,disk_used_pct,disk_read_bps,disk_write_bps"
header+=",network_rx_bps,network_tx_bps,cpu_pressure_avg10,memory_pressure_avg10,io_pressure_avg10"
header+=",java_cpu_pct,java_rss_mb,java_threads,java_fds,java_read_bps,java_write_bps"
header+=",chromium_processes,chromium_cpu_pct,chromium_rss_mb"
header+=",queue_pending,queue_running,db_active_connections,db_idle_connections,db_size_mb,shopping_jobs_total"
echo "$header" > "$OUTPUT_FILE"

read -r previous_system_total previous_system_idle < <(system_cpu_snapshot)
previous_java_ticks="$(process_ticks "$PID")"
previous_java_read="$(process_io "$PID" read_bytes)"
previous_java_write="$(process_io "$PID" write_bytes)"
read -r previous_disk_read previous_disk_write < <(disk_io_snapshot)
read -r previous_network_rx previous_network_tx < <(network_snapshot)
previous_browser_ticks=0
while read -r initial_browser_pid; do
  [[ -n "$initial_browser_pid" ]] || continue
  previous_browser_ticks=$((previous_browser_ticks + $(process_ticks "$initial_browser_pid")))
done < <(ps -eo pid=,comm= | awk '$2 ~ /^(chromium|chrome|chrome-headless)/ { print $1 }')
previous_time_ms="$(date +%s%3N)"
deadline=$((SECONDS + DURATION_SECONDS))
sample_index=0

while ((SECONDS <= deadline)); do
  [[ -n "$STOP_FILE" && -e "$STOP_FILE" ]] && break
  current_time_ms="$(date +%s%3N)"
  elapsed_ms=$((current_time_ms - previous_time_ms))
  ((elapsed_ms > 0)) || elapsed_ms=1
  timestamp="$(date --iso-8601=seconds)"

  read -r system_total system_idle < <(system_cpu_snapshot)
  system_delta=$((system_total - previous_system_total))
  idle_delta=$((system_idle - previous_system_idle))
  system_cpu="$(awk -v total="$system_delta" -v idle="$idle_delta" \
    'BEGIN { if (total <= 0) print "0.00"; else printf "%.2f", (total-idle) * 100 / total }')"
  read -r load_1m load_5m load_15m _ < /proc/loadavg

  memory_total_kb="$(awk '/^MemTotal:/ { print $2 }' /proc/meminfo)"
  memory_available_kb="$(awk '/^MemAvailable:/ { print $2 }' /proc/meminfo)"
  swap_total_kb="$(awk '/^SwapTotal:/ { print $2 }' /proc/meminfo)"
  swap_free_kb="$(awk '/^SwapFree:/ { print $2 }' /proc/meminfo)"
  memory_total_mb=$((memory_total_kb / 1024))
  memory_available_mb=$((memory_available_kb / 1024))
  memory_used_mb=$(((memory_total_kb - memory_available_kb) / 1024))
  swap_used_mb=$(((swap_total_kb - swap_free_kb) / 1024))

  read -r disk_total_kb disk_used_kb disk_available_kb disk_used_pct < <(
    df -Pk / | awk 'NR==2 { gsub(/%/, "", $5); print $2, $3, $4, $5 }'
  )
  read -r disk_read disk_write < <(disk_io_snapshot)
  disk_read_bps="$(rate_per_second "$disk_read" "$previous_disk_read" "$elapsed_ms")"
  disk_write_bps="$(rate_per_second "$disk_write" "$previous_disk_write" "$elapsed_ms")"
  read -r network_rx network_tx < <(network_snapshot)
  network_rx_bps="$(rate_per_second "$network_rx" "$previous_network_rx" "$elapsed_ms")"
  network_tx_bps="$(rate_per_second "$network_tx" "$previous_network_tx" "$elapsed_ms")"

  java_ticks="$(process_ticks "$PID")"
  java_cpu="$(cpu_percent "$java_ticks" "$previous_java_ticks" "$elapsed_ms")"
  java_rss_kb="$(awk '/^VmRSS:/ { print $2 }' "/proc/$PID/status")"
  java_threads="$(awk '/^Threads:/ { print $2 }' "/proc/$PID/status")"
  java_fds="$(find "/proc/$PID/fd" -mindepth 1 -maxdepth 1 2>/dev/null | wc -l)"
  java_read="$(process_io "$PID" read_bytes)"
  java_write="$(process_io "$PID" write_bytes)"
  java_read_bps="$(rate_per_second "$java_read" "$previous_java_read" "$elapsed_ms")"
  java_write_bps="$(rate_per_second "$java_write" "$previous_java_write" "$elapsed_ms")"

  browser_pids="$(ps -eo pid=,comm= | awk '$2 ~ /^(chromium|chrome|chrome-headless)/ { print $1 }')"
  chromium_processes=0
  chromium_ticks=0
  chromium_rss_kb=0
  while read -r browser_pid; do
    [[ -n "$browser_pid" && -r "/proc/$browser_pid/status" ]] || continue
    chromium_processes=$((chromium_processes + 1))
    chromium_ticks=$((chromium_ticks + $(process_ticks "$browser_pid")))
    browser_rss="$(awk '/^VmRSS:/ { print $2 }' "/proc/$browser_pid/status")"
    chromium_rss_kb=$((chromium_rss_kb + ${browser_rss:-0}))
  done <<< "$browser_pids"
  chromium_cpu="$(cpu_percent "$chromium_ticks" "$previous_browser_ticks" "$elapsed_ms")"

  if ((sample_index == 0)); then
    system_cpu="0.00"
    disk_read_bps="0.00"
    disk_write_bps="0.00"
    network_rx_bps="0.00"
    network_tx_bps="0.00"
    java_cpu="0.00"
    java_read_bps="0.00"
    java_write_bps="0.00"
    chromium_cpu="0.00"
  fi

  IFS='|' read -r queue_pending queue_running db_active db_idle db_size_mb shopping_jobs_total \
    <<< "$(database_counts)"
  service_restarts="$(systemctl show "$SERVICE_NAME" -p NRestarts --value)"

  row="$timestamp,$PID,$service_restarts,$system_cpu,$load_1m,$load_5m,$load_15m"
  row+=",$memory_total_mb,$memory_used_mb,$memory_available_mb,$swap_used_mb"
  row+=",$((disk_total_kb / 1024)),$((disk_used_kb / 1024)),$((disk_available_kb / 1024)),$disk_used_pct,$disk_read_bps,$disk_write_bps"
  row+=",$network_rx_bps,$network_tx_bps,$(pressure_avg10 cpu),$(pressure_avg10 memory),$(pressure_avg10 io)"
  row+=",$java_cpu,$((java_rss_kb / 1024)),$java_threads,$java_fds,$java_read_bps,$java_write_bps"
  row+=",$chromium_processes,$chromium_cpu,$((chromium_rss_kb / 1024))"
  row+=",$queue_pending,$queue_running,$db_active,$db_idle,$db_size_mb,$shopping_jobs_total"
  echo "$row" >> "$OUTPUT_FILE"

  previous_system_total="$system_total"
  previous_system_idle="$system_idle"
  previous_java_ticks="$java_ticks"
  previous_java_read="$java_read"
  previous_java_write="$java_write"
  previous_disk_read="$disk_read"
  previous_disk_write="$disk_write"
  previous_network_rx="$network_rx"
  previous_network_tx="$network_tx"
  previous_browser_ticks="$chromium_ticks"
  previous_time_ms="$current_time_ms"
  sample_index=$((sample_index + 1))

  sleep "$INTERVAL_SECONDS"
done

echo "METRICS_FILE=$OUTPUT_FILE"
