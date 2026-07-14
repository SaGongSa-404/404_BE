#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--help" ]]; then
  echo "Usage: SSH_TARGET=user@host [OUTPUT_FILE=\$HOME/.secrets/wigul-nf84-qa-reviewer.token] $0"
  exit 0
fi

SSH_TARGET="${SSH_TARGET:-koreaworldclass@34.66.55.165}"
OUTPUT_FILE="${OUTPUT_FILE:-$HOME/.secrets/wigul-nf84-qa-reviewer.token}"

umask 077
mkdir -p "$(dirname "$OUTPUT_FILE")"

if ! response="$(
  ssh -o BatchMode=yes "$SSH_TARGET" 'bash -s' <<'REMOTE'
set -euo pipefail

env_file="$HOME/404_BE/.env.systemd"
if [[ ! -r "$env_file" ]]; then
  echo "Backend environment file is not readable: $env_file" >&2
  exit 1
fi

set -a
. "$env_file"
set +a
reviewer_secret="${APP_REVIEWER_TOKEN_SECRET:-}"
if (( ${#reviewer_secret} < 32 )); then
  echo "Reviewer secret is unexpectedly short" >&2
  exit 1
fi

printf 'header = "X-Reviewer-Token: %s"\n' "$reviewer_secret" \
  | curl -fsS --config - -X POST http://127.0.0.1:8080/api/auth/reviewer-token
REMOTE
)"; then
  echo "Failed to issue the QA reviewer access token" >&2
  exit 1
fi

token="$(node -e '
let raw = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => raw += chunk);
process.stdin.on("end", () => {
  const value = JSON.parse(raw);
  if (typeof value.accessToken !== "string" || value.accessToken.length < 32) process.exit(2);
  process.stdout.write(value.accessToken);
});
' <<<"$response")"

printf '%s\n' "$token" > "$OUTPUT_FILE"
chmod 600 "$OUTPUT_FILE"
echo "TOKEN_FILE=$OUTPUT_FILE"
echo "TOKEN_LENGTH=${#token}"
