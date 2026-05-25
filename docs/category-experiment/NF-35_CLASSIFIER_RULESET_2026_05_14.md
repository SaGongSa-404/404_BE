# NF-35 카테고리 실험 분류 규칙

버전: `nf35-keyword-rules-2026-05-14`

## 대상 카테고리

- `FASHION`
- `BEAUTY`
- `DIGITAL`
- `LIVING`
- `FOOD`
- `HOBBY`
- `SUBSCRIPTION`
- `ETC`

## 공통 방식

- 페이지를 Playwright Chromium으로 열고 `title`, `og:title`, `description`, `bodyPreview`, URL 텍스트를 추출한다.
- 카테고리별 키워드가 포함된 필드에 가중치를 더한다.
- 점수가 가장 높은 카테고리를 결과로 사용한다.
- 모든 카테고리 점수가 0이면 `ETC`로 분류한다.
- 키워드 원본은 `docs/category-experiment/nf35-category-experiment/visible-browser-category-analysis.mjs`와 `docs/category-experiment/nf35-category-experiment/product-only-category-analysis.mjs`의 `CATEGORY_KEYWORDS` 상수다.

## 검색/카테고리 URL 포함 측정

스크립트: `docs/category-experiment/nf35-category-experiment/visible-browser-category-analysis.mjs`

- `content-only`
  - `title`: 6
  - `og:title`: 6
  - `description`: 4
  - `bodyPreview`: 1
- `url-assisted`
  - `title`: 6
  - `og:title`: 6
  - `description`: 4
  - 입력 URL 텍스트: 6
  - 최종 URL 텍스트: 4
  - `bodyPreview`: 1

## 상품 상세 URL 기준 측정

스크립트: `docs/category-experiment/nf35-category-experiment/product-only-category-analysis.mjs`

- 검색/카테고리 URL은 상품 상세 URL을 찾는 데만 사용한다.
- 최종 분류 평가는 확보된 상품 상세 URL만 대상으로 한다.
- `content-only`
  - `title`: 6
  - `og:title`: 6
  - `description`: 4
  - `bodyPreview`: 1
- `url-assisted`
  - `title`: 6
  - `og:title`: 6
  - `description`: 4
  - 상품 상세 입력 URL 텍스트: 2
  - 상품 상세 최종 URL 텍스트: 2
  - `bodyPreview`: 1

## 해석 주의

- 검색 URL 포함 결과는 검색어가 카테고리 힌트로 작용할 수 있으므로 상품 상세 기준 결과와 분리해서 본다.
- 운영 판단 기준은 상품 상세 URL 확보율 `58/70`과 확보된 상품 상세 URL 기준 정확도 `47/58`을 함께 본다.
