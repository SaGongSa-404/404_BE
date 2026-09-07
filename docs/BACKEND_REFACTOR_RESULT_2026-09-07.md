# 백엔드 책임 경계 리팩토링 — 구현 결과

2차 구현은 [후속 결과](BACKEND_REFACTOR_CONTINUATION_2026-09-07.md)를 참조한다. 아래 검증 수치는 1차 시점 기록이며 책임 경계는 최신 구현을 반영했다.

## 기준과 범위

- 기준: `origin/develop@a4a66e0456113f7a5d397400496ca5d90a74cc14`.
- 구현 브랜치: `refactor/backend-boundaries-20260907`.
- 구현 작업 트리: `/tmp/404-be-refactor-20260907`. 원래 작업 폴더의 기존 변경은 유지했다.
- 단일 Spring Boot 배포, PostgreSQL, 기존 JPA/JDBC 병용 구조를 유지한다. 새 인프라·라이브러리·스키마 마이그레이션은 없다.
- 로컬 구현과 검증 결과이며 원격 PR·CI·병합·배포 완료를 뜻하지 않는다.

## 실제 책임 경계

| 영역 | 변경 전 | 실제 구현 |
|---|---|---|
| 결정 | `DecisionService`에 HTTP DTO·정책·SQL·트랜잭션 집중 | `DecisionService`는 호환 어댑터. `DecisionCommands`가 트랜잭션, `DecisionQueries`가 조회, `DecisionPolicy`가 계산, `DecisionJdbcRepository`가 저장 접근 담당 |
| 예산 | 결정 서비스에 지출 SQL 포함 | `BudgetLedger`가 잠금과 지출 증감 담당. `MANDATORY`로 호출자의 결정 트랜잭션에 참여 |
| 내부 결정 호출 | `ConsumptionService`가 결정 HTTP DTO 사용 | `ChangeDecisionCommand`와 `DecisionOutcome` 사용. 웹 변환은 `DecisionWebMapper` |
| 크롤링 | 하나의 서비스에서 URL·HTML·JSON·정책 처리 | `ShoppingUrlNormalizer`, `ShoppingPageExtractor`, `ProductJsonMetadataReader`, `ShoppingProductPolicy`, 공통 값/문자열 도구 분리 |
| 작업 실행 | worker가 실행과 상태 SQL 함께 소유 | worker는 외부 수집 조정, `ShoppingImportJobRepository`는 claim·완료·복구·정리 및 follower 전파 |
| 마이페이지 | 프로필·통계·목록·탈퇴 집중 | `ProfileCommands`, `MypageStatsQueries`, `SocialActivityQueries`, `AccountWithdrawalService`로 분리 |
| 홈·위시 | 조회와 변경 혼합 | `HomeSummaryQueries`/`HomeBubbleCommands`, `WishlistQueries`/`WishlistPolicy` 분리. 변경 SQL은 `WishlistJdbcRepository`, 트랜잭션은 `WishlistService`에 유지 |
| 푸시 | 토큰 SQL과 FCM 호출 혼합 | `PushDeliveryRepository`가 설정·토큰 접근, `PushNotificationService`가 외부 전송 담당 |
| 오류·경계 검사 | 동일 오류 record 중복, 분리 경계 검사 없음 | 공통 `api.ApiErrorResponse`, 새 책임 경계에 대한 `BackendBoundaryTest` 추가 |

## 보존한 계약

HTTP 경로·요청/응답 필드·예외 핸들러의 상태 코드·오류 본문 구조를 유지했다. 결정 생성의 행 잠금·중복 요청 처리·예산 및 마스코트/리마인더 변경은 하나의 트랜잭션으로 유지한다. 결정 변경도 예산 증감과 같은 트랜잭션이다. DB 컬럼과 상태 값은 바꾸지 않았다.

탈퇴 삭제 순서와 소셜 목록의 batch 조회를 보존했다. `MypageQueryCountIntegrationTest`의 내 게시물 5회/투표 게시물 4회 준비 문장 제한이 통과한다. 홈 요약과 일부 통계 조회에는 기존 월 예산 생성 동작이 있으므로 이름에 `Queries`가 붙어도 무조건 부수 효과 없는 읽기라는 뜻은 아니다. 예산 한도 설정은 기존 `ProfileCommands`의 JPA 경로, 결정 지출 원장은 `BudgetLedger`의 JDBC 경로다.

## 의도적으로 바뀐 실패 동작

### 오래된 작업 결과 거부

이전 완료 SQL은 `id`와 `RUNNING`만 비교했다. 첫 실행이 지연된 동안 stale 복구와 두 번째 claim이 일어나면 첫 실행 결과가 새 실행과 follower 상태를 덮어쓸 수 있었다.

지금은 claim 시 받은 `attempt_count`까지 일치해야 완료한다. leader 변경 행이 0이면 follower도 건드리지 않고 오래된 완료 로그를 남긴다. 별도 컬럼은 추가하지 않았다. `ShoppingImportJobOwnershipIntegrationTest`는 실제 PostgreSQL에서 순서를 고정해 이전 구현의 2개 실패를 재현한 뒤 수정 후 통과했다. 외부 crawl 자체를 취소하거나 중복 외부 요청을 완전히 없애는 보장은 아니다.

### 커밋 이후 푸시 경계

`NotificationPublisher`의 dedupe·저장·afterCommit 구조는 유지한다. FCM은 `NOT_SUPPORTED`로 활성 DB 트랜잭션 밖에서 호출한다. 설정/토큰 조회와 잘못된 토큰 비활성화는 `PushDeliveryRepository`의 별도 `REQUIRES_NEW` 트랜잭션을 사용한다. 특히 afterCommit에서 실행되는 토큰 변경이 실제 커밋되도록 한다.

`PushDeliveryTransactionIntegrationTest`는 실제 커밋 이후 전송·활성 트랜잭션 부재·토큰 비활성화 영속화·중복 발행 억제·롤백 시 미전송을 검사한다. FCM sender는 대역이므로 실제 Firebase 전달은 검증하지 않았다. 저장 뒤 프로세스 종료로 푸시가 유실될 수 있는 구간은 남는다. outbox나 자동 재전송은 구현하지 않았다.

## 변경안과 실제 구현 비교

- 예상대로: 큰 서비스 책임 분리, 결정 내부 DTO 분리, 예산 지출 원장 경계, stale 실행 소유권 검사, 푸시 경계 테스트, 아키텍처 가드.
- 구현 중 조정: 패키지를 일괄 `application/domain/infrastructure`로 옮기지 않고 기존 도메인 패키지 안에서 역할을 나눴다. 조회용 JPA join/batch를 인터페이스 체인으로 교체하지 않았다. 기존 쿼리와 계약을 보존하면서 변경 단위를 줄이기 위한 선택이다.
- 이번 구현에서 제외: job submit/get SQL의 별도 저장소 분리, 전체 소셜 도메인 포트 도입, 모든 접근 예외의 일괄 통합(기존 의미가 달라 보존). 기존 접근 정책과 API별 오류 의미를 동일하다고 가정하지 않는다. 2차에서 마이페이지→소셜 공개 조회 경계와 알림 trigger 대상/정책 분리를 추가했다. 나머지 항목은 후속 후보이며 접근 예외를 무조건 합치지는 않는다.
- 새 검증 기반: `Clock` 주입은 결정 흐름과 계정 접근 검사에 적용했다. 전역 시간 추상화 완료는 아니다. 소스 기반 경계 테스트에 더해 2차에서 컴파일된 프로젝트 클래스의 의존성 순환 검사를 추가했다. 런타임 리플렉션 의존성까지 증명하지는 않는다.
- 원래 13개 PR 계획을 13개 원격 PR로 실행한 결과가 아니다. 이번 브랜치는 그중 핵심 책임 분리와 두 실패 경계 보강을 묶은 로컬 구현이다.

## 검증 결과

| 검사 | 결과 |
|---|---|
| 변경 전 전체 테스트 | 668개, 실패 0, 오류 0, 건너뜀 9 |
| 변경 후 `test jacocoTestReport` | 695개, 실패 0, 오류 0, 건너뜀 9 |
| 최종 실행용 JAR | `test jacocoTestReport bootJar` 통과 |
| 이해 문서 브라우저 검사 | Chromium에서 탐색·영향 보기·흐름 이동·검색·테마·깊이 선택·펼침·10문항 퀴즈·파일 참조·390px 화면 폭 검사 통과 |
| 결정 후반 DB 실패 | 결정·예산·위시·마스코트 변경 롤백 검사 통과 |
| 동일 결정 동시 완료 | 두 호출의 결정 ID 동일, 지출 한 번 반영, reminder 한 개 검사 통과 |
| stale job | 수정 전 2개 실패 재현 → 수정 후 통과 |
| 푸시 트랜잭션 | 커밋·롤백·중복·토큰 비활성화 검사 통과 |
| 파싱 및 URL 안전성 | 기존 크롤링 fixture·URL 안전성 회귀 통과 |
| 소셜 조회 비용 | 기존 5/4 준비 문장 제한 통과 |
| 운영 p95·실제 FCM·실제 사이트 수집률 | 측정하지 않음 |

추가 Spring 테스트 컨텍스트 때문에 테스트 DB 연결 한도를 초과하는 실패가 있었다. `src/test/resources/application.yml`의 테스트 전용 Hikari pool을 최대 4, 유휴 최소 0으로 설정한 뒤 전체 테스트가 통과했다. 운영 pool 설정은 바꾸지 않았다. 건너뛴 9개 검사는 통과로 계산하지 않는다.

## 배포 전 주의

1. 최신 develop과 재비교하고 원격 CI·리뷰를 별도로 수행한다.
2. 기존 worker에는 attempt 검사가 없으므로 구버전 worker의 실행을 종료/배출한 뒤 새 버전으로 전환해야 소유권 보호가 온전히 적용된다. 혼합 버전 중에는 구버전의 늦은 쓰기를 새 코드만으로 막을 수 없다.
3. 스키마는 그대로여서 코드 롤백은 가능하지만 롤백하면 stale 결과 보호와 푸시 토큰 커밋 보강도 사라진다. DB 데이터 삭제나 초기화는 필요하지 않다.
4. 실제 트래픽 p95, DB pool 대기, queue 지연, ignored stale 로그, 푸시 오류/비활성 토큰을 관찰한다. afterCommit 콜백의 외부 전송 지연과 추가 DB 연결 수요는 운영에서 확인해야 한다.

상세 실행 흐름과 이해도 점검: [understanding.html](understanding.html#backend-refactor-actual).
