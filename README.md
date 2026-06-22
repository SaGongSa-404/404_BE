# 위굴 Backend

위굴 MVP 도메인과 DB 스키마 기준선을 검증하기 위한 Spring Boot 백엔드입니다.
초기 기준선에서는 API 기능 구현보다 Flyway 마이그레이션, JPA 엔티티, enum, 스키마 검증 테스트를 중심으로 둡니다.

## Stack

- Java 21
- Spring Boot 3.5.11
- Spring Data JPA
- Flyway
- PostgreSQL
- Testcontainers
- Gradle Wrapper

## Run

테스트를 실행하면 Flyway 마이그레이션과 JPA 스키마 검증이 함께 수행됩니다.
기본 실행 설정과 테스트 모두 PostgreSQL을 기준으로 합니다.

Docker Engine이 실행 중이면 PostgreSQL과 애플리케이션을 Compose로 함께 실행할 수 있습니다.

```bash
docker compose up --build
```

애플리케이션은 `http://localhost:8080`에서 실행되고, PostgreSQL은 로컬 `5432` 포트로 노출됩니다.
로컬 Gradle 실행은 별도 PostgreSQL이 `localhost:5432`에서 실행 중이어야 합니다.

```bash
./gradlew test
```

## Environment

FCM 푸시 발송은 기본값에서 비활성화됩니다.
QA/운영 서버에서 활성화하려면 Firebase 서비스 계정 JSON을 레포 밖에 두고 환경변수로 경로만 주입합니다.

| Variable | Default | Note |
| --- | --- | --- |
| `APP_PUSH_FCM_ENABLED` | `false` | `true`일 때 실제 FCM sender를 사용합니다. |
| `APP_PUSH_FCM_CREDENTIALS_LOCATION` | empty | 예: `file:/opt/wigul/secrets/firebase-adminsdk.json` |
| `APP_ADMIN_NOTIFICATION_TOKEN` | empty | 관리자 공지/점검 알림 API의 `X-Admin-Token` 검증에 사용합니다. |

상세 운영 절차는 `docs/FCM_PUSH_OPERATIONS.md`를 기준으로 합니다.

## Coverage

JaCoCo 커버리지 리포트는 테스트 실행 후 생성됩니다.
HTML 리포트는 브라우저로 확인하고, XML 리포트는 CI/외부 도구 연동에 사용할 수 있습니다.

```bash
./gradlew test jacocoTestReport
```

- HTML: `build/reports/jacoco/test/html/index.html`
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`

## Notes

- 기준 문서: `docs/DOMAIN_SCHEMA_REVISED_FROM_PLANNING_2026_04_16.md`
- DB 기준선: `src/main/resources/db/migration/V1__domain_schema_baseline.sql`
- 스키마 확인 테스트: `src/test/java/com/sagongsa/backend/domain/DomainSchemaSmokeTest.java`
