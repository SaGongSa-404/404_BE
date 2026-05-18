# NF-38 QA Regression Test Scope

## Context

- Tracking Key: `NF-38`
- Jira: https://parkjaehong.atlassian.net/browse/NF-38
- GitHub Issue: #65
- Branch: `test/NF-38-qa-regression`

This draft test PR is prepared before the related feature and bugfix PRs are merged.
The actual test implementation should be rebased onto `develop` after the prerequisite PRs land.

## Checked Conventions

Issue and PR templates checked:

- `.github/ISSUE_TEMPLATE/test.md`
- `.github/PULL_REQUEST_TEMPLATE/test.md`
- `.github/pull_request_template.md`

Nearby issues checked:

- #41 `[bug] NF-29 인증/OAuth 보안 계약 보완`
- #42 `[bug] NF-30 쇼핑 import preview와 wishlist 저장 계약 정합성 보완`
- #43 `[bug] NF-31 구매 결정 결과와 리마인더 워커 안정성 보완`
- #44 `[bug] NF-32 홈 요약 조회와 DB trigger 테스트 안정화`
- #51 `[feat] NF-35 상품 링크 수집을 브라우저 렌더링 기반 방식으로 개선`
- #53 `[feat] NF-36 예산 소진 상태 응답 추가`
- #63 `[feat] 예산 소진 상태 및 말풍선 노출 플래그 지원`

Nearby PRs checked:

- #46 `[bug] NF-29 인증/OAuth 보안 계약 보완`
- #47 `[bug] NF-30 쇼핑 import preview와 wishlist 저장 계약 정합성 보완`
- #48 `[bug] NF-31 구매 결정 결과와 리마인더 워커 안정성 보완`
- #49 `[bug] NF-32 홈 요약 조회와 DB trigger 테스트 안정화`
- #54 `[feat] NF-35 브라우저 렌더링 기반 상품 링크 수집 검증 리포트 추가`
- #55 `[feat] NF-35 브라우저 렌더링 기반 상품 링크 수집 구현`
- #56 `[feat] NF-35 카테고리 자동분류 우선순위 보강`
- #57 `[feat] NF-36 예산 소진 상태 응답 추가`
- #64 `[feat]NF-37 예산 소진 상태 및 말풍선 노출 플래그 추가`

## Target Scope

The follow-up QA regression PR should remain test-only:

- No runtime behavior changes
- No DB migration changes
- No CI pipeline changes unless test execution time requires a later split
- No live external shopping-site calls in automated tests

## Expected File Scope

New test files:

- `src/test/java/com/sagongsa/backend/auth/CurrentUserIdArgumentResolverTest.java`
- `src/test/java/com/sagongsa/backend/itemimport/item/ShoppingUrlSafetyTest.java`

Existing test files likely to change:

- `src/test/java/com/sagongsa/backend/auth/AuthApiControllerTest.java`
- `src/test/java/com/sagongsa/backend/itemimport/item/ShoppingLinkImportServiceTest.java`
- `src/test/java/com/sagongsa/backend/wishlist/WishlistApiIntegrationTest.java`
- `src/test/java/com/sagongsa/backend/decision/DecisionApiIntegrationTest.java`
- `src/test/java/com/sagongsa/backend/notification/ReminderNotificationWorkerIntegrationTest.java`
- `src/test/java/com/sagongsa/backend/home/HomeSummaryIntegrationTest.java`
- `src/test/java/com/sagongsa/backend/domain/DomainSchemaSmokeTest.java`
- `src/test/java/com/sagongsa/backend/mvp/MvpBackendGapIntegrationTest.java`

Expected size:

- 8 to 10 files
- 18 to 28 test cases
- Around 600 to 900 added lines

## Test Case Groups

### TC1. Auth Contract

Purpose: lock down the security boundary between authenticated principals and trusted test headers.

Candidate cases:

- JWT principal is used before `X-User-Id` when both are present.
- malformed trusted header is rejected when no principal exists.
- `/api/auth/me` keeps provider raw attributes out of the response.
- refresh token rotation rejects an already-consumed token.

### TC2. Import Preview and Wishlist Contract

Purpose: verify that preview output can be saved and list pagination has predictable API boundaries.

Candidate cases:

- DIRECT_INPUT preview without URL can be saved through wishlist API.
- URL userinfo is rejected consistently.
- wishlist list rejects `limit=0`, `limit=51`, and malformed cursor.
- list response remains summary-only and does not expose raw metadata.

### TC3. Decision and Reminder Contract

Purpose: make QA-visible decision input rules and reminder behavior explicit.

Candidate cases:

- invalid or duplicated self-check question code is rejected on create/update.
- `rationaleText` and `changeReason` enforce max length.
- GO to STOP cancels scheduled reminder.
- disabled or inactive users do not receive due reminder notifications.

### TC4. Home, Budget, and Domain Integrity

Purpose: verify home summary and DB-trigger behavior around month and budget boundaries.

Candidate cases:

- current-month rational choice uses user timezone boundaries.
- zero budget is not treated as exhausted.
- overspent budget clamps remaining amount at zero.
- self-check response set deletion is blocked after decision summary exists.
- post vote update/delete keeps feed post vote counts in sync.

### TC5. MVP QA Flow

Purpose: provide one or two high-signal flows that answer QA questions about the integrated app path.

Candidate cases:

- import preview -> wishlist save -> deliberation -> decision GO -> budget exhausted -> home summary.
- due reminder -> notification list/read -> reflection creation.
- decision update -> budget/home state recalculation.

## Merge Plan

1. Keep this PR as draft while #46, #47, #48, #49, #54, #55, #56, #57, and #64 are still open.
2. After the prerequisite PRs merge, rebase this branch onto the latest `origin/develop`.
3. Implement the scoped tests.
4. Run targeted suites first, then `./gradlew.bat test`.
5. Move the PR out of draft only after local test results are recorded in the PR body.
