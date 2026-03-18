# 404_BE

## Architecture Draft

- ERD: [docs/domain/erd.md](/mnt/c/Users/ParkJaeHong/Desktop/404/404_BE/docs/domain/erd.md)
- API endpoint catalog: [docs/api/mobile-v1-endpoints.md](/mnt/c/Users/ParkJaeHong/Desktop/404/404_BE/docs/api/mobile-v1-endpoints.md)
- OpenAPI draft: [contracts/openapi/mobile-v1.yaml](/mnt/c/Users/ParkJaeHong/Desktop/404/404_BE/contracts/openapi/mobile-v1.yaml)

## Implementation Scaffold

- Gradle multi-module root: [settings.gradle.kts](/mnt/c/Users/ParkJaeHong/Desktop/404/404_BE/settings.gradle.kts)
- API app entrypoint: [ApiApplication.java](/mnt/c/Users/ParkJaeHong/Desktop/404/404_BE/apps/api/src/main/java/com/fourohfour/backend/api/bootstrap/ApiApplication.java)
- Worker app entrypoint: [WorkerApplication.java](/mnt/c/Users/ParkJaeHong/Desktop/404/404_BE/apps/worker/src/main/java/com/fourohfour/backend/worker/bootstrap/WorkerApplication.java)
- Flyway migrations: [db/migrations](/mnt/c/Users/ParkJaeHong/Desktop/404/404_BE/db/migrations)

## Local Run

1. Copy `.env.example` values into your shell environment if you want to override defaults.
2. Start local infra with `./scripts/dev/up-local.sh`.
3. Load demo data with `./scripts/dev/seed-local.sh`.
4. Run API with `./scripts/dev/run-api.sh`.
5. Run worker with `./scripts/dev/run-worker.sh`.

Quick bootstrap:

- `./scripts/dev/bootstrap-local.sh`

Health checks:

- API health: `http://localhost:8080/internal/health`
- Actuator health: `http://localhost:8080/actuator/health`
