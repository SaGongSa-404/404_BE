# NF-38 Service/API Regression Test Scope

## Context

- Tracking Key: `NF-38`
- GitHub Issue: #65
- Base PR: #66 `[test] NF-38 QA 대비 단위 테스트 보강`
- Branch: `test/NF-38-service-api-regression`

This PR is the next stacked slice after the unit-test-only PR #66.
It keeps runtime behavior unchanged and adds service/controller/API regression coverage.

## Scope

- Add MockMvc integration coverage for `POST /api/v1/items/import-link`.
- Verify import preview API response fields that the frontend passes to wishlist save.
- Keep external shopping-page access stubbed with a fake `PageFetcher`.
- Extend wishlist cursor API coverage for blank and malformed cursor parameters.
- Extend decision update API coverage for unknown self-check `questionCode`.

## Covered Cases

- SHARE import returns extracted item, source metadata, save request, and normalization warnings.
- DIRECT_INPUT import without URL returns null URL fields and manual metadata.
- SHARE import rejects URL userinfo with `400`.
- SHARE import maps failed shopping-page fetch to `502`.
- Wishlist list treats blank cursor as first page.
- Wishlist list rejects malformed cursor with `400`.
- Decision update rejects unknown self-check question code with `400`.

## Not Covered

- Full MVP end-to-end flow from import to home summary.
- Reminder worker and notification read/reflection flow.
- External live shopping-site crawling.
- Runtime code changes, migrations, or CI pipeline changes.

## Validation

```powershell
.\gradlew.bat test --tests "com.sagongsa.backend.itemimport.ItemImportApiIntegrationTest" --tests "com.sagongsa.backend.wishlist.WishlistApiIntegrationTest" --tests "com.sagongsa.backend.decision.DecisionApiIntegrationTest"
```

- Passed locally on 2026-05-27.
