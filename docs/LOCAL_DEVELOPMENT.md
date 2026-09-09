# 위굴 로컬 개발 안내

[프로젝트 README](../README.md)

## 빠른 실행

Git, Docker Engine, Docker Compose v2가 필요합니다. 컨테이너 실행에는 로컬 JDK가 필요하지 않습니다.

```bash
git clone https://github.com/SaGongSa-404/404_BE.git
cd 404_BE
docker compose up --build
```

| 대상 | 주소 |
| --- | --- |
| Backend | http://localhost:8080 |
| 상태 확인 | http://localhost:8080/health |
| PostgreSQL | localhost:5432 |

다른 터미널에서 상태를 확인합니다. PowerShell에서는 `curl` 대신 `curl.exe`를 사용하세요.

```bash
curl http://localhost:8080/health
```

정상 응답은 HTTP 200이며 JSON의 `status`는 `UP`입니다. 이는 HTTP 서버 확인용이며 외부 로그인·푸시·크롤링 연동 성공을 보장하지 않습니다.

소셜 로그인에는 공급자 설정이 필요합니다. [로그인 설정](./SOCIAL_LOGIN_SETUP_AND_POLICY.md)을 참고하세요. FCM은 기본적으로 비활성화됩니다.

### 로컬 Gradle 실행

JDK 21과 PostgreSQL이 필요합니다. Compose DB를 사용한다면 다음과 같이 실행합니다.

```bash
docker compose up -d postgres
SPRING_DATASOURCE_PASSWORD=postgres ./gradlew bootRun
```

Windows PowerShell:

```powershell
docker compose up -d postgres
$env:SPRING_DATASOURCE_PASSWORD = "postgres"
.\gradlew.bat bootRun
```

Compose로 앱을 이미 실행했다면 로컬 앱을 시작하기 전에 `docker compose stop app`으로 8080 포트를 비워주세요. DB 기본값과 전체 설정은 [application.yml](../src/main/resources/application.yml), 컨테이너 설정은 [docker-compose.yml](../docker-compose.yml)에 있습니다.

## 테스트

JDK 21과 실행 중인 Docker Engine이 필요합니다. Testcontainers 기반 테스트는 테스트용 DB 컨테이너를 사용합니다.

```bash
./gradlew test jacocoTestReport
```

PowerShell에서는 `.\gradlew.bat test jacocoTestReport`를 실행합니다.

- HTML: `build/reports/jacoco/test/html/index.html`
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`
- [API 회귀 테스트 범위](./prs/NF-38-SERVICE_API_REGRESSION_TEST_SCOPE.md)

## 환경변수

FCM 서비스 계정 JSON은 저장소 밖에 두고 경로로 주입합니다.

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `APP_PUSH_FCM_ENABLED` | `false` | 실제 FCM 발송 활성화 |
| `APP_PUSH_FCM_CREDENTIALS_LOCATION` | 비어 있음 | 예: `file:/opt/wigul/secrets/firebase-adminsdk.json` |
| `APP_ADMIN_NOTIFICATION_TOKEN` | 비어 있음 | 관리자 알림 API의 `X-Admin-Token` 검증 |
| `SHOPPING_IMPORT_JOB_WORKER_ENABLED` | `true` | 작업 worker 활성화 |
| `SHOPPING_IMPORT_JOB_MAX_QUEUE_SIZE` | `100` | 전체 활성 작업 상한 |
| `SHOPPING_IMPORT_JOB_MAX_ACTIVE_PER_USER` | `1` | 사용자별 활성 작업 상한 |
| `SHOPPING_IMPORT_JOB_MAX_ATTEMPTS` | `2` | 최대 claim 횟수 |
| `SHOPPING_IMPORT_JOB_FIXED_DELAY_MS` | `500` | 작업 확인 간격 |
| `SHOPPING_IMPORT_JOB_RECOVERY_DELAY_MS` | `60000` | 중단 작업 복구 확인 간격 |
| `SHOPPING_IMPORT_JOB_CLEANUP_DELAY_MS` | `3600000` | 완료 작업 정리 간격 |
| `SHOPPING_IMPORT_JOB_STALE_TIMEOUT` | `PT5M` | 중단 작업 판정 시간 |
| `SHOPPING_IMPORT_JOB_RETENTION` | `P7D` | 완료·실패 결과 보관기간 |
