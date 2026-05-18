# 브라우저 표시 기반 카테고리 추출/분류 재측정

실행일: 2026-05-14T12:37:16.617Z
브라우저: Playwright Chromium visible/headed
렌더 대기: 1000ms
제외: 무신사 404 2개

## 요약

- 대상 샘플: 70
- 추출 성공: 70/70 (100.0%)
- weighted content-only 정확도: 61/70 (87.1%)
- weighted url-assisted 정확도: 65/70 (92.9%)
- 추출 실패/차단: 0

## 사이트별

| 사이트 | 샘플 | 추출 성공 | content-only | url-assisted | 실패/차단 |
|---|---:|---:|---:|---:|---:|
| 무신사 | 10 | 10/10 | 10/10 | 10/10 | 0 |
| 29CM | 10 | 10/10 | 5/10 | 8/10 | 0 |
| 올리브영 | 10 | 10/10 | 9/10 | 9/10 | 0 |
| 지그재그 | 10 | 10/10 | 8/10 | 9/10 | 0 |
| 에이블리 | 10 | 10/10 | 9/10 | 9/10 | 0 |
| 당근 | 10 | 10/10 | 10/10 | 10/10 | 0 |
| 번개장터 | 10 | 10/10 | 10/10 | 10/10 | 0 |

## 문제 샘플

| 구분 | 사이트 | 유형 | 기대 | HTTP | content-only | url-assisted | 제목 | 비고 |
|---|---|---|---|---:|---|---|---|---|
| 분류 오답 | 29CM | 상품상세 | FASHION | 200 | BEAUTY | BEAUTY | 감도 깊은 취향 셀렉트샵 29CM | 백팩 |
| 분류 오답 | 29CM | 검색 | ETC | 200 | BEAUTY | SUBSCRIPTION | 감도 깊은 취향 셀렉트샵 29CM | 검색: 선물카드 |
| 분류 오답 | 올리브영 | 검색 | DIGITAL | 200 | BEAUTY | BEAUTY | / 올리브영 | 뷰티 디바이스 |
| 분류 오답 | 지그재그 | 검색 | ETC | 200 | FASHION | FASHION | 지그재그 스토어 | 검색: 잡화 |
| 분류 오답 | 에이블리 | 검색 | ETC | 200 | BEAUTY | BEAUTY | 문구 - 에이블리 | 검색: 문구 |

## 실제 상품 상세 기준 비교

첫 질문의 의사결정에는 검색/카테고리 URL 포함 결과도 참고할 수 있지만, 실제 상품 자동분류 정확도라고 말할 때는 상품 상세 URL만 기준으로 보는 편이 더 보수적이다.

| 기준 | 샘플 | 크롤링 성공 | content-only | url-assisted | 해석 |
|---|---:|---:|---:|---:|---|
| 검색/카테고리 URL 포함 | 70 | 70/70 (100.0%) | 61/70 (87.1%) | 65/70 (92.9%) | 사용자가 붙여넣을 수 있는 쇼핑 URL 전반에서 카테고리 힌트를 얼마나 잘 잡는지 보는 용도 |
| 실제 상품 상세 URL만 | 58 | 58/58 (100.0%) | 47/58 (81.0%) | 47/58 (81.0%) | 실제 상품 저장 시 자동분류 품질에 더 가까운 보수적 기준 |

따라서 현재 근거로 답하면 “자동선택 + 유저 수정 가능”은 가능하고, 정확도는 검색 URL 포함 기준으로는 90%대까지 보이지만 실제 상품 상세 기준으로는 81.0%를 기준선으로 잡는 것이 더 안전하다. 현재 5개 카테고리(패션, 뷰티, 라이프, 디지털, 기타)로 합치면 FOOD/HOBBY/SUBSCRIPTION/LIVING이 단순화되므로 실제 운영 정확도는 이 상품 상세 8분류 테스트보다 올라갈 여지가 있다.

## 재현 자료

- 샘플 입력: `docs/category-experiment/NF-35_SAMPLE_INPUTS_2026_05_14.csv`
- 실행 스크립트/커맨드: `docs/category-experiment/NF-35_CATEGORY_EXPERIMENT_REPRODUCIBILITY_2026_05_14.md`
- 분류 규칙 버전: `docs/category-experiment/NF-35_CLASSIFIER_RULESET_2026_05_14.md`
- 검색/카테고리 URL 포함 RAW 결과: `docs/category-experiment/nf35-category-experiment/results/visible-browser-category-analysis-2026-05-14.json`
- 상품 상세 URL 기준 RAW 결과: `docs/category-experiment/nf35-category-experiment/results/product-only-category-analysis-2026-05-14.json`
