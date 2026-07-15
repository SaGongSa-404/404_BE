#!/usr/bin/env bash
set -euo pipefail

if [[ "${CONFIRM_QA_LOAD_TEST:-}" != "YES" || "${CONFIRM_EXTERNAL_TRAFFIC:-}" != "YES" ]]; then
  echo "Set CONFIRM_QA_LOAD_TEST=YES and CONFIRM_EXTERNAL_TRAFFIC=YES before running" >&2
  exit 1
fi
if [[ "${CONFIRM_QA_TEST_USERS:-}" != "YES" ]]; then
  echo "Set CONFIRM_QA_TEST_USERS=YES before creating temporary QA users" >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
token_file="${ACCESS_TOKEN_FILE:-$HOME/.secrets/wigul-nf97-qa-users.tokens}"
result_dir="${RESULT_DIR:-$script_dir/results}"
ssh_target="${SSH_TARGET:-koreaworldclass@34.66.55.165}"
ssh_identity_file="${SSH_IDENTITY_FILE:-$HOME/.ssh/wigul_gha_deploy}"
metrics_interval_seconds="${METRICS_INTERVAL_SECONDS:-5}"
job_timeout_ms="${JOB_TIMEOUT_MS:-900000}"
metrics_duration_seconds="${METRICS_DURATION_SECONDS:-$((job_timeout_ms / 1000 + 120))}"
run_id="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
remote_metrics_file="/tmp/wigul-nf97-${run_id}-server-metrics.csv"
remote_stop_file="/tmp/wigul-nf97-${run_id}.stop"
local_metrics_file="$result_dir/${run_id}-server-metrics.csv"
local_summary_file="$result_dir/${run_id}-server-summary.json"
metrics_pid=""
metrics_started=false

if ! [[ "$metrics_interval_seconds" =~ ^[1-9][0-9]*$ && "$job_timeout_ms" =~ ^[1-9][0-9]*$ \
  && "$metrics_duration_seconds" =~ ^[1-9][0-9]*$ ]]; then
  echo "METRICS_INTERVAL_SECONDS, JOB_TIMEOUT_MS and METRICS_DURATION_SECONDS must be positive integers" >&2
  exit 1
fi
[[ -r "$ssh_identity_file" ]] || { echo "SSH identity file is not readable: $ssh_identity_file" >&2; exit 1; }

ssh_options=(-o BatchMode=yes -o IdentitiesOnly=yes -i "$ssh_identity_file")
mkdir -p "$result_dir"

finish_metrics() {
  if [[ "$metrics_started" != true ]]; then
    return
  fi
  ssh "${ssh_options[@]}" "$ssh_target" "touch '$remote_stop_file'" >/dev/null 2>&1 || true
  if [[ -n "$metrics_pid" ]]; then
    wait "$metrics_pid" || true
  fi
  if scp "${ssh_options[@]}" "$ssh_target:$remote_metrics_file" "$local_metrics_file"; then
    node "$script_dir/summarize-server-metrics.mjs" "$local_metrics_file" "$local_summary_file" || true
  else
    echo "Could not download server metrics: $remote_metrics_file" >&2
  fi
  ssh "${ssh_options[@]}" "$ssh_target" "rm -f '$remote_metrics_file' '$remote_stop_file'" >/dev/null 2>&1 || true
  metrics_started=false
}

cleanup_users() {
  ACCESS_TOKEN_FILE="$token_file" \
  SSH_TARGET="$ssh_target" \
  SSH_IDENTITY_FILE="$ssh_identity_file" \
  "$script_dir/configure-qa-distinct-users.sh" cleanup || \
    echo "Automatic cleanup failed; run configure-qa-distinct-users.sh cleanup after active jobs finish" >&2
}

finalize() {
  status="$?"
  trap - EXIT
  set +e
  finish_metrics
  cleanup_users
  exit "$status"
}
trap finalize EXIT

ACCESS_TOKEN_FILE="$token_file" \
SSH_TARGET="$ssh_target" \
SSH_IDENTITY_FILE="$ssh_identity_file" \
"$script_dir/configure-qa-distinct-users.sh" apply

ssh "${ssh_options[@]}" "$ssh_target" \
  "rm -f '$remote_stop_file'; set -a; source \"\$HOME/404_BE/.env.systemd\"; set +a; \
   INTERVAL_SECONDS='$metrics_interval_seconds' DURATION_SECONDS='$metrics_duration_seconds' \
   OUTPUT_FILE='$remote_metrics_file' STOP_FILE='$remote_stop_file' SERVICE_NAME=wigul-backend bash -s" \
  < "$script_dir/collect-server-metrics.sh" &
metrics_pid="$!"
metrics_started=true
sleep 1
if ! kill -0 "$metrics_pid" 2>/dev/null; then
  wait "$metrics_pid"
  echo "Server metrics collector failed to start" >&2
  exit 1
fi

cd "$script_dir"
VERIFY_PRODUCT_CORRECTNESS=NO \
CONFIRM_PERFORMANCE_ONLY=YES \
ACCESS_TOKEN_FILE="$token_file" \
URL_FILE="${URL_FILE:-$script_dir/qa-product-urls-100.txt}" \
RESULT_DIR="$result_dir" \
SCENARIO=real-users \
IMPORT_MODE=async \
JOB_TIMEOUT_MS="$job_timeout_ms" \
node run.mjs
