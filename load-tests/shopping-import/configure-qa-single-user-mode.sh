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
  awk -v key="$key" '
    BEGIN { replaced = 0 }
    index($0, key "=") == 1 { print key "=101"; replaced = 1; next }
    { print }
    END { if (!replaced) print key "=101" }
  ' "$env_file" > "$temp_file"
  chmod 600 "$temp_file"
  mv "$temp_file" "$env_file"
  trap - EXIT
  echo "QA_SINGLE_USER_LIMIT=101"
else
  if [[ ! -f "$backup_file" ]]; then
    echo "Backup not found; refusing an unsafe restore: $backup_file" >&2
    exit 1
  fi
  mv "$backup_file" "$env_file"
  chmod 600 "$env_file"
  restored="$(sed -n "s/^${key}=//p" "$env_file" | tail -n 1)"
  echo "QA_SINGLE_USER_LIMIT_RESTORED=${restored:-default-3}"
fi
REMOTE

previous_run_id="$(gh run list --repo SaGongSa-404/404_BE --workflow deploy-qa.yml --event workflow_dispatch --limit 1 --json databaseId --jq '.[0].databaseId // empty')"
gh workflow run deploy-qa.yml --repo SaGongSa-404/404_BE --ref develop
echo "QA_DEPLOY_REQUESTED=true"

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
