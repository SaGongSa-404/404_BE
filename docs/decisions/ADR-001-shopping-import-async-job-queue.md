# ADR-001: 쇼핑 링크 가져오기에 DB 기반 비동기 작업 큐 사용

- 상태: Accepted
- 결정일: 2026-07-14
- 관련 이슈: #189
- 검증 이슈: #190

## 배경

`POST /api/v1/items/import-link`는 외부 쇼핑 페이지 수집과 선택적 Playwright 렌더링이 끝날 때까지 HTTP 요청 스레드를 점유한다. 브라우저 경로의 기본 제한은 navigation 30초, network-idle 2초, render wait 5초이며 동시 요청이 많으면 다른 API까지 CPU, 메모리, 요청 스레드 고갈의 영향을 받을 수 있다.

서비스는 PostgreSQL을 이미 운영한다. 현재 목표 규모에서 Redis, Kafka 또는 별도 메시지 브로커를 추가하면 운영 비용과 장애 지점이 늘어난다. 또한 Playwright Java 객체는 제한 없는 다중 스레드 호출에 안전하지 않으므로 크롤링 병렬도를 명시적으로 제어해야 한다.

## 결정

1. 기존 동기 API는 호환성을 위해 유지한다.
2. 신규 비동기 작업 생성/조회 API를 추가한다.
3. 작업 상태와 payload/result는 PostgreSQL `shopping_import_jobs`에 저장한다.
4. in-process 전용 worker 하나가 `PENDING` 작업을 FIFO로 claim해 처리한다.
5. worker는 `FOR UPDATE SKIP LOCKED`와 조건부 상태 갱신으로 중복 처리를 방지한다.
6. queue 크기를 제한하고 포화 시 `429 Too Many Requests`를 반환한다.
7. 사용자별 활성 작업 수도 제한하고, 동일 사용자의 진행 중 동일 요청은 기존 job을 반환한다.
8. 오래된 `RUNNING` 작업은 제한 횟수 안에서 재시도하고, 횟수를 소진하면 안전한 오류로 종료한다.
9. 완료 작업은 설정된 보관기간 이후 배치로 삭제한다.
10. Playwright 호출은 동기화해 같은 객체에 대한 동시 접근을 막는다.
11. worker는 별도 scheduler thread를 사용해 기존 알림 scheduler를 막지 않는다.

## 이유

- 요청 스레드와 장시간 크롤링을 분리해 서버 전체 장애 전파를 줄인다.
- 별도 인프라 없이 현재 PostgreSQL을 재사용해 초기 비용을 낮춘다.
- 영속 작업 상태로 프로세스 재시작 후 복구가 가능하다.
- 단일 worker 기본값은 작은 QA 서버의 CPU/메모리 피크와 Playwright 스레드 안전성 위험을 제한한다.
- 기존 동기 API를 유지해 FE가 점진적으로 신규 계약으로 이동할 수 있다.

## 결과와 트레이드오프

### 장점

- 작업 제출은 외부 페이지 응답시간과 분리된다.
- 크롤링 피크 동시성이 제한된다.
- queue 상태와 실패 원인을 사용자별로 조회할 수 있다.
- worker를 비활성화하거나 기존 API로 되돌리는 롤백 경로가 있다.

### 단점

- FE는 `202 + jobId` 이후 상태를 polling하거나 완료 알림을 처리해야 한다.
- 단일 worker에서는 queue 뒤쪽 작업의 대기시간이 길어질 수 있다.
- DB polling과 작업 row 보관 정책이 추가된다.
- 신규 API로 전환하지 않은 트래픽은 기존 동기 부하 특성을 유지한다.

## 고려한 대안

### 기존 API에 `@Async`만 적용

HTTP 스레드는 줄지만 제한 없는 크롤링 실행과 프로세스 재시작 시 작업 유실을 해결하지 못해 제외했다.

### Redis/Kafka/SQS

확장성과 분리도는 높지만 현재 목표 규모에서는 추가 비용, 운영 복잡도와 장애 지점이 과하다. 다중 인스턴스 또는 처리량 요구가 PostgreSQL queue 한계를 넘을 때 재검토한다.

### 서버 사양 증설

피크를 늦출 뿐 동시성 제어, Playwright 스레드 안전성과 장애 격리를 해결하지 못하므로 우선순위에서 제외했다.

## 검증 및 재검토 조건

#190에서 동일 QA 조건으로 동기/비동기 전후 부하를 비교한다. 다음 항목을 측정한다.

재현 가능한 실행기와 서버 지표 수집 절차는 `load-tests/shopping-import`에 둔다. 토큰과 실제 상품 URL은 저장소에 커밋하지 않고, 동일한 승인 URL 세트와 QA 조건을 동기/비동기 실행에 재사용한다.

- 일반 API p50/p95/p99와 오류율
- 작업 제출 응답시간과 queue wait
- job 처리시간, 성공률과 실패 유형
- backend JVM/프로세스 CPU와 RSS
- Chromium CPU와 RSS
- DB connection과 queue 길이
- 재시작 후 stale 작업 복구

다음 조건 중 하나가 발생하면 이 결정을 재검토한다.

- 단일 worker의 대기시간이 제품 목표를 지속적으로 초과한다.
- PostgreSQL queue polling/row lock이 일반 DB 작업에 유의미한 영향을 준다.
- 다중 백엔드 인스턴스 또는 독립 배포가 필요하다.
- Chromium 자원 격리를 위해 crawler 전용 프로세스가 필요하다.

재검토 시 우선 순서는 worker 프로세스 분리, 독립 Playwright 인스턴스 기반 제한 병렬화, 외부 broker 도입 순서로 한다.
