# 쇼핑 링크 브라우저 수집 운영 메모

## 기본 정책

- `app.shopping.import.browser-fetch.enabled=true`가 기본값이다.
- 브라우저 수집이 켜져 있으면 정적 수집이 403/408/429/5xx로 실패할 때 `BrowserPageFetcher`가 Playwright Chromium을 지연 생성하고 재사용한다.
- 요청마다 새 브라우저를 만들지 않고, 요청 단위로 격리된 `BrowserContext`만 생성 후 닫는다.
- 애플리케이션 종료 시 브라우저와 Playwright 런타임을 닫는다.
- Playwright Java 객체 접근은 동기화하며 비동기 작업 worker는 전용 단일 scheduler thread를 사용한다.

## 비동기 작업 큐

- 기존 `POST /api/v1/items/import-link` 동기 API는 FE 호환을 위해 유지한다.
- 신규 `POST /api/v1/items/import-jobs`는 `202 Accepted`, `Location`, `jobId`를 반환한다.
- `GET /api/v1/items/import-jobs/{jobId}`로 `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED` 상태와 결과를 조회한다.
- 작업은 PostgreSQL `shopping_import_jobs`에 저장하며 사용자 본인의 작업만 조회할 수 있다.
- 전체 queue 기본 상한은 100개, 사용자별 활성 작업 기본 상한은 3개다. 초과 요청은 `429`로 거절한다.
- 동일 사용자의 진행 중 동일 요청은 새 작업을 만들지 않고 기존 `jobId`를 반환한다.
- worker 기본 동시성은 1이다. Playwright 처리량을 높이기 전에 독립 인스턴스와 QA 부하 테스트가 선행되어야 한다.
- 5분 이상 `RUNNING`인 작업은 재시도하고 최대 2회 claim 이후에는 `FAILED`로 종료한다.
- 완료/실패 작업은 기본 7일 보관 후 배치 삭제한다.

설계 결정과 재검토 조건은 `docs/decisions/ADR-001-shopping-import-async-job-queue.md`를 따른다.

## 보안 가드

- 최초 입력 URL과 렌더링 후 최종 URL은 모두 public host인지 검증한다.
- 렌더링 중 발생하는 `http`/`https` 하위 요청도 같은 public host 검증을 통과해야 한다.
- private, loopback, link-local, site-local, multicast 주소로 향하는 요청이 감지되면 요청을 중단하고 `400 Bad Request`로 처리한다.
- `data:`, `blob:`처럼 네트워크 접근이 아닌 브라우저 내부 URL은 SSRF 대상이 아니므로 그대로 둔다.

## 배포 전 확인

- 런타임 이미지에 Playwright Chromium 실행에 필요한 브라우저 바이너리와 OS 의존성이 포함되어야 한다.
- 이미지 빌드 단계에서 Playwright CLI로 Chromium을 미리 설치해 첫 요청 지연을 피한다.
- 브라우저 수집은 정적 HTML 수집보다 비용이 크므로, 운영 활성화 전 응답 시간과 실패율을 별도 샘플로 확인한다.

## 검증 포인트

- 플래그를 끄면 `JsoupPageFetcher`만 선택된다.
- 기본값 또는 플래그를 켜면 `JsoupPageFetcher` 이후 `BrowserPageFetcher` 폴백이 선택된다.
- 브라우저 수집 중 private network 하위 요청이 발생하면 수집이 실패해야 한다.
- 신규 작업 제출은 크롤링 완료를 기다리지 않고 `202`를 반환해야 한다.
- queue 포화, 사용자별 상한, 중복 요청, stale 복구와 보관기간 삭제가 설정값을 따라야 한다.
