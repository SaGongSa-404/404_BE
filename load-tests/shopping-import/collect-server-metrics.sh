#!/usr/bin/env bash
set -euo pipefail

INTERVAL_SECONDS="${INTERVAL_SECONDS:-5}"
DURATION_SECONDS="${DURATION_SECONDS:-180}"
OUTPUT_FILE="${OUTPUT_FILE:-server-metrics.csv}"
SERVICE_NAME="${SERVICE_NAME:-wigul-backend}"

if ! [[ "$INTERVAL_SECONDS" =~ ^[1-9][0-9]*$ && "$DURATION_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "INTERVAL_SECONDS and DURATION_SECONDS must be positive integers" >&2
  exit 1
fi

PID="$(systemctl show "$SERVICE_NAME" -p MainPID --value)"
if [[ -z "$PID" || "$PID" == "0" || ! -r "/proc/$PID/status" ]]; then
  echo "Cannot read MainPID for $SERVICE_NAME: $PID" >&2
  exit 1
fi

echo "timestamp,service_pid,java_cpu_pct,java_rss_kb,chromium_cpu_pct,chromium_rss_kb,queue_pending,queue_running" > "$OUTPUT_FILE"

samples=$((DURATION_SECONDS / INTERVAL_SECONDS + 1))
for ((sample = 0; sample < samples; sample++)); do
  timestamp="$(date --iso-8601=seconds)"
  java_cpu="$(ps -p "$PID" -o %cpu= | xargs)"
  java_rss="$(ps -p "$PID" -o rss= | xargs)"
  # Chromium is expected to disappear between crawl jobs. `ps` returns 1 when
  # no matching process exists, which must not abort the collector under
  # `set -euo pipefail`.
  chromium_processes="$(ps -C chromium,chrome -o %cpu=,rss= 2>/dev/null || true)"
  chromium_cpu="$(awk '{sum += $1} END {printf "%.2f", sum + 0}' <<< "$chromium_processes")"
  chromium_rss="$(awk '{sum += $2} END {printf "%d", sum + 0}' <<< "$chromium_processes")"
  queue_pending=""
  queue_running=""

  if command -v psql >/dev/null 2>&1 && [[ -n "${PGHOST:-}" && -n "${PGDATABASE:-}" && -n "${PGUSER:-}" ]]; then
    counts="$(psql -Atqc "select count(*) filter (where status='PENDING'), count(*) filter (where status='RUNNING') from shopping_import_jobs" 2>/dev/null || true)"
    queue_pending="${counts%%|*}"
    queue_running="${counts##*|}"
  fi

  echo "$timestamp,$PID,$java_cpu,$java_rss,$chromium_cpu,$chromium_rss,$queue_pending,$queue_running" >> "$OUTPUT_FILE"
  if ((sample + 1 < samples)); then
    sleep "$INTERVAL_SECONDS"
  fi
done

echo "METRICS_FILE=$OUTPUT_FILE"
