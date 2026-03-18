#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cd "${REPO_ROOT}"

./scripts/dev/up-local.sh

echo "Local infra is up."
echo "Next:"
echo "1. Run ./scripts/dev/run-api.sh to apply migrations."
echo "2. Run ./scripts/dev/seed-local.sh to load demo data."
echo "3. Run ./scripts/dev/run-worker.sh in a separate terminal."
