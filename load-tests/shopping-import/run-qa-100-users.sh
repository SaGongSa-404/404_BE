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
cleanup() {
  ACCESS_TOKEN_FILE="$token_file" "$script_dir/configure-qa-distinct-users.sh" cleanup || \
    echo "Automatic cleanup failed; run configure-qa-distinct-users.sh cleanup after active jobs finish" >&2
}
trap cleanup EXIT

ACCESS_TOKEN_FILE="$token_file" "$script_dir/configure-qa-distinct-users.sh" apply
cd "$script_dir"
VERIFY_PRODUCT_CORRECTNESS=NO \
CONFIRM_PERFORMANCE_ONLY=YES \
ACCESS_TOKEN_FILE="$token_file" \
URL_FILE="${URL_FILE:-$script_dir/qa-product-urls-100.txt}" \
RESULT_DIR="${RESULT_DIR:-$script_dir/results}" \
SCENARIO=real-users \
IMPORT_MODE=async \
node run.mjs
