# job 등록·조회 저장 경계 후속 구현

후속 원인 검증: [동일 JVM 교차 비교 결과](JOB_POLLING_CAUSE_CHECK_2026-09-08.md). 기존 큰 지연 증가는 재현되지 않아 해당 성능 보류를 해제했다. 아래 초기 측정과 보류 판단은 당시 기록으로 보존한다.

기준은 PR #260의 병합 커밋 `f565eea`다. 이번 변경은 소셜 내부 전체 리팩토링을 포함하지 않는다.

## 실제 구조

- `ShoppingImportJobService`: 등록 TransactionTemplate, 중복/active leader/cache/신규 순서, 용량 판정, JSON 직렬화와 결과 개인화, HTTP 오류.
- `ShoppingImportSubmissionRepository`: 기존 advisory lock ID, 등록·재사용 SQL. MANDATORY로 등록 트랜잭션에 참여한다. 별도 커밋 없음.
- `ShoppingImportJobQueries`: id와 user_id로 행을 조회해 원시 결과를 반환한다. 쓰기 트랜잭션 프록시와 분리했다. 원래처럼 조회 자체에 새 트랜잭션을 열지 않는다.
- worker의 claim·attempt fencing·완료·복구는 기존 `ShoppingImportJobRepository`에 유지한다.

DB 스키마, HTTP 필드, 캐시 TTL, 용량 제한 수치, 기존 판정 순서를 변경하지 않았다. 결과 역직렬화는 행을 읽은 뒤 서비스에서 수행하므로 조회 커넥션 반환 뒤 응답 가공이 이어진다.

## 검증과 미완료 조건

최종 로컬 `test jacocoTestReport bootJar` 성공: 719개, 실패·오류 0, 건너뜀 11(기존 9 + 선택 벤치마크 2). 컴파일 타입 291개, 참조 752개, 순환 0. Chromium에서 구조 문서 탐색·흐름·검색·퀴즈·모바일 표시와 파일 참조를 확인했다.

기존 import 통합 테스트의 소유자 제한, 큐 용량, 중복/공유/캐시/복구 경로를 실행했다. 추가 테스트는 MANDATORY의 무트랜잭션 호출 거부와 등록 롤백을 확인한다. 구조 문서의 흐름·영향 범위·퀴즈도 갱신했다.

성능 판정은 **보류**다. 같은 pending job을 서비스→로컬 PostgreSQL 경로에서 100회 준비 호출 후 200회씩 4라운드 측정했다. HTTP, 실제 수집, 동시 부하는 포함하지 않는다. 첫 실행은 변경→기준, 두 번째는 기준→변경 순서다. 원시 결과를 모두 보존했다.

- [최초 기준](performance/JOB_READ_baseline_2026-09-08.csv) / [최초 변경](performance/JOB_READ_refactored_2026-09-08.csv)
- [재측정 기준](performance/JOB_READ_baseline_repeat_2026-09-08.csv) / [재측정 변경](performance/JOB_READ_refactored_repeat_2026-09-08.csv)
- [읽기 경계 별도 분리 후](performance/JOB_READ_split_queries_2026-09-08.csv)

최초 p95 차이는 -0.012~+0.021ms였지만 재측정에서 변경 p95가 +0.032~+0.107ms 높았다. 읽기 경계 분리 후에도 직전 기준보다 +0.037~+0.075ms 높았다. 기준 자체도 실행마다 변했다. 이것만으로 코드가 원인이라고 단정할 수는 없지만, 사용자의 성능 비악화 조건을 충족했다고 판단할 수도 없다. 프록시가 원인이라는 가설도 입증되지 않았다.

따라서 Draft PR으로 공유하고 병합·배포는 보류한다. 다음 검증은 충분한 준비 호출과 동일 프로세스 교차 측정, 실제 완료 결과 fixture 확대를 통해 작은 차이와 환경 변동을 구분하는 것이다. 이 기록은 성능 기준 완화나 배포 승인을 뜻하지 않는다.
