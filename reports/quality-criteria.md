# Quality Criteria

## Runbook

- Basic link-set smoke test:
  - `.\reports\run-content-links-eval.ps1`
- Accuracy check against a markdown verification set:
  - `.\reports\run-quality-batch-check.ps1`

## Recommended Thresholds

- Overall category accuracy: 80%+
- High-confidence categories:
  - `요리`
  - `행사·전시`
  - `루틴·습관`
  - `운동`
- Each high-confidence category should reach 85%+
- Unsupported/error rate: under 5%

## Wrong-Answer Tolerance

- Beta:
  - overall mismatch under 20%
  - no single high-confidence category under 75%
- Wider rollout:
  - overall mismatch under 12%
  - no single high-confidence category under 85%
- Full production confidence:
  - overall mismatch under 8%
  - all high-confidence categories 90%+

## Operational Review

- Review recent `FAILED`, `SKIPPED`, `REPORTED` AI events daily
- Re-run the quality batch check after changing:
  - scraping rules
  - heuristic rules
  - AI prompt/model
