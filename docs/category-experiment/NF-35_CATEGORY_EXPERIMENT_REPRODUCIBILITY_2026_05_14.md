# NF-35 카테고리 실험 재현 자료

이 문서는 `2026-05-14`에 작성한 쇼핑 링크 카테고리 실험을 후속 PR에서 같은 입력 기준으로 다시 측정하기 위한 기준이다.

## 샘플 입력

- 전체 입력 목록: `docs/category-experiment/NF-35_SAMPLE_INPUTS_2026_05_14.csv`
- 원본 스크립트 내 샘플 배열: `docs/category-experiment/nf35-category-experiment/category-crawl-site10-test.mjs`
- 구성: 쿠팡 제외 7개 사이트, 사이트별 10개, 총 70개
- 평가 라벨: `FASHION`, `BEAUTY`, `DIGITAL`, `LIVING`, `FOOD`, `HOBBY`, `SUBSCRIPTION`, `ETC`

## 실행 환경

- Node.js: 로컬 실행 환경
- 브라우저: Playwright Chromium
- 실행 방식: visible/headed, 순차 실행
- 의존성 위치: `docs/category-experiment/nf35-category-experiment/package-lock.json`
- 분류 규칙 버전: `nf35-keyword-rules-2026-05-14`
- 분류 규칙 문서: `docs/category-experiment/NF-35_CLASSIFIER_RULESET_2026_05_14.md`

## 실행 커맨드

PowerShell 기준:

```powershell
cd docs/category-experiment/nf35-category-experiment
npm ci

$env:HEADLESS = "false"
$env:RENDER_WAIT_MS = "1000"
npm run visible

$env:HEADLESS = "false"
$env:RENDER_WAIT_MS = "1200"
npm run product-only
```

## 원시 결과 파일

- 검색/카테고리 URL 포함 raw result: `docs/category-experiment/nf35-category-experiment/results/visible-browser-category-analysis-2026-05-14.json`
- 검색/카테고리 URL 포함 요약: `docs/category-experiment/nf35-category-experiment/results/visible-browser-category-analysis-2026-05-14.md`
- 상품 상세 URL 기준 raw result: `docs/category-experiment/nf35-category-experiment/results/product-only-category-analysis-2026-05-14.json`
- 상품 상세 URL 기준 요약: `docs/category-experiment/nf35-category-experiment/results/product-only-category-analysis-2026-05-14.md`

## 기준 결과

| 기준 | 입력 | 평가 샘플 | 수집 성공 | 분류 정확도 |
|---|---:|---:|---:|---:|
| 검색/카테고리 URL 포함 | 70 | 70 | 70/70 (100.0%) | 65/70 (92.9%) |
| 실제 상품 상세 URL만 | 70 | 58 | 58/58 (100.0%) | 47/58 (81.0%) |

상품 상세 URL 기준에서 평가 샘플이 58개인 이유는 전체 후보 70개 중 12개에서 상품 상세 URL을 확보하지 못했기 때문이다. 따라서 `58/58`은 전체 70개 기준이 아니라, 확보된 상품 상세 URL 58개 기준의 추출 성공률이다.

## 후속 PR 비교 방법

후속 구현 PR에서 결과가 바뀌었는지 보려면 같은 CSV 입력과 같은 분류 규칙 버전으로 재측정한다. 비교할 때는 아래 항목을 분리해서 기록한다.

- 전체 입력 70개 중 상품 상세 URL 확보 수
- 확보된 상품 상세 URL 기준 추출 성공 수
- `content-only` 정확도
- `url-assisted` 정확도
- 오답 샘플 목록
- 상품 상세 URL 미확보 목록
