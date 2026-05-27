# NF-38 QA Regression Test Scope

## Context

- Tracking Key: `NF-38`
- Jira: https://parkjaehong.atlassian.net/browse/NF-38
- GitHub Issue: #65
- Branch: `test/NF-38-qa-regression`

This PR is narrowed to unit tests that can land independently on top of `develop`.
Integration and MVP-flow regression tests should be added in later stacked PRs after their prerequisite feature PRs land.

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

This PR should remain unit-test-only:

- No runtime behavior changes
- No DB migration changes
- No CI pipeline changes unless test execution time requires a later split
- No live external shopping-site calls in automated tests
- No API/integration/MVP flow tests in this PR

## Expected File Scope

New test files:

- `src/test/java/com/sagongsa/backend/auth/CurrentUserIdArgumentResolverTest.java`
- `src/test/java/com/sagongsa/backend/decision/DecisionServiceUnitTest.java`
- `src/test/java/com/sagongsa/backend/wishlist/WishlistCursorTest.java`

Existing unit test files likely to change:

- `src/test/java/com/sagongsa/backend/itemimport/item/ShoppingLinkImportServiceTest.java`

Expected size:

- 4 files
- 18 to 22 unit test cases
- Around 350 to 500 added lines

## Test Case Groups

### TC1. Auth Contract Unit Tests

Purpose: lock down the security boundary between authenticated principals and trusted test headers.

Cases:

- JWT principal is used before `X-User-Id` when both are present.
- malformed trusted header is rejected when no principal exists.
- explicitly enabled trusted header in prod keeps returning BAD_REQUEST for malformed or missing header input.
- JWT subject is used when the explicit `userId` claim is missing.
- non-prod trusted-header fallback is allowed when no principal exists.
- prod without principal/header fails with the expected auth status.

### TC2. URL Safety Unit Tests

Purpose: keep shopping import URL validation explicit without adding external network calls.

Cases:

- non-http schemes are rejected.
- URL userinfo is rejected for SHARE and DIRECT_INPUT paths.

### TC3. Wishlist Cursor Unit Tests

Purpose: keep list pagination cursor behavior explicit without a database.

Cases:

- encoded cursors include `createdAt` and `id` tie-breaker.
- current cursor format parses with tie-breaker.
- legacy `createdAt`-only cursor remains readable.
- blank and malformed cursor input fail with a bad request exception when parsed directly.

### TC4. Decision Request Validation Unit Tests

Purpose: catch decision input mistakes before database access.

Cases:

- duplicated self-check question code is rejected on complete and update paths.
- unknown self-check question code is rejected on complete and update paths.
- `rationaleText` and `changeReason` enforce the 1000 character limit.

## Deferred Stacked PR Scope

The following groups should move to stacked PRs after their target production PRs land:

- wishlist API pagination and response contract tests
- decision input validation and reminder worker integration tests
- home summary, budget, and DB-trigger integration tests
- MVP flow tests such as import preview -> wishlist -> decision -> home summary
- social vote count and mypage integration tests

## Merge Plan

1. Rebase this branch onto the latest `origin/develop`.
2. Implement only the scoped unit tests.
3. Run targeted unit tests first, then `./gradlew.bat test`.
4. Move the PR out of draft only after local test results are recorded in the PR body.
5. Open stacked follow-up PRs for integration/MVP regression groups.
