#!/usr/bin/env bash
set -euo pipefail

action="${1:-}"
if [[ "$action" != "apply" && "$action" != "restore" ]]; then
  echo "Usage: SSH_TARGET=user@host $0 apply|restore" >&2
  exit 2
fi

SSH_TARGET="${SSH_TARGET:-koreaworldclass@34.66.55.165}"

ssh -o BatchMode=yes "$SSH_TARGET" bash -s -- "$action" <<'REMOTE'
set -euo pipefail

action="$1"
env_file="$HOME/404_BE/.env.systemd"
backup_file="${env_file}.nf84-single-user-backup"
key="SHOPPING_IMPORT_JOB_MAX_ACTIVE_PER_USER"
concurrency_key="SHOPPING_IMPORT_JOB_CONCURRENCY"

if [[ ! -f "$env_file" ]]; then
  echo "Environment file not found: $env_file" >&2
  exit 1
fi

if [[ "$action" == "apply" ]]; then
  if [[ -e "$backup_file" ]]; then
    echo "Backup already exists; restore it before applying again: $backup_file" >&2
    exit 1
  fi
  cp -p "$env_file" "$backup_file"
  chmod 600 "$backup_file"
  temp_file="$(mktemp "${env_file}.XXXXXX")"
  trap 'rm -f "$temp_file"' EXIT
  awk -v key="$key" -v concurrency_key="$concurrency_key" '
    BEGIN { replaced = 0; concurrency_replaced = 0 }
    index($0, key "=") == 1 { print key "=101"; replaced = 1; next }
    index($0, concurrency_key "=") == 1 { print concurrency_key "=2"; concurrency_replaced = 1; next }
    { print }
    END {
      if (!replaced) print key "=101"
      if (!concurrency_replaced) print concurrency_key "=2"
    }
  ' "$env_file" > "$temp_file"
  chmod 600 "$temp_file"
  mv "$temp_file" "$env_file"
  trap - EXIT
  echo "QA_SINGLE_USER_LIMIT=101"
  echo "QA_WORKER_CONCURRENCY=2"
else
  if [[ ! -f "$backup_file" ]]; then
    echo "Backup not found; refusing an unsafe restore: $backup_file" >&2
    exit 1
  fi
  mv "$backup_file" "$env_file"
  chmod 600 "$env_file"
  restored="$(sed -n "s/^${key}=//p" "$env_file" | tail -n 1)"
  restored_concurrency="$(sed -n "s/^${concurrency_key}=//p" "$env_file" | tail -n 1)"
  echo "QA_SINGLE_USER_LIMIT_RESTORED=${restored:-default-3}"
  echo "QA_WORKER_CONCURRENCY_RESTORED=${restored_concurrency:-default-1}"
fi
REMOTE

previous_run_id="$(gh run list --repo SaGongSa-404/404_BE --workflow deploy-qa.yml --event workflow_dispatch --limit 1 --json databaseId --jq '.[0].databaseId // empty')"
if [[ "$action" == "apply" ]]; then
  deploy_ref="${QA_DEPLOY_APPLY_REF:-${GITHUB_REF_NAME:-develop}}"
else
  deploy_ref="${QA_DEPLOY_RESTORE_REF:-develop}"
fi
gh workflow run deploy-qa.yml --repo SaGongSa-404/404_BE --ref "$deploy_ref"
echo "QA_DEPLOY_REQUESTED=true"
echo "QA_DEPLOY_REF=$deploy_ref"

new_run_id=""
for _ in $(seq 1 30); do
  candidate="$(gh run list --repo SaGongSa-404/404_BE --workflow deploy-qa.yml --event workflow_dispatch --limit 1 --json databaseId --jq '.[0].databaseId // empty')"
  if [[ -n "$candidate" && "$candidate" != "$previous_run_id" ]]; then
    new_run_id="$candidate"
    break
  fi
  sleep 2
done

if [[ -z "$new_run_id" ]]; then
  echo "Could not discover the requested QA deploy run" >&2
  exit 1
fi

echo "QA_DEPLOY_RUN_ID=$new_run_id"
gh run watch "$new_run_id" --repo SaGongSa-404/404_BE --exit-status
echo "QA_DEPLOY_SUCCEEDED=true"
