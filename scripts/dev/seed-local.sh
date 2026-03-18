#!/usr/bin/env bash
set -euo pipefail

docker compose exec -T postgres psql -U postgres -d 404 < db/seeds/demo.sql

