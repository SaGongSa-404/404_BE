#!/usr/bin/env bash
set -euo pipefail

SPRING_PROFILES_ACTIVE=local ./gradlew :apps:api:bootRun

