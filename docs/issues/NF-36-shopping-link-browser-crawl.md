제목: `[feat] NF-36 상품 링크 수집을 브라우저 렌더링 기반 방식으로 개선`

## 관련 키 / 참고 링크
- 트래킹 키: `NF-36`
- 관련 PR / 문서:
  - Jira: https://parkjaehong.atlassian.net/browse/NF-36
  - GitHub Issue: #51
  - 기여: `PHJ2000`
  - 실험 리포트: `docs/CATEGORY_VISIBLE_BROWSER_ANALYSIS_2026_05_14.html`
  - 검색/카테고리 URL 포함 결과: `docs/CATEGORY_VISIBLE_BROWSER_ANALYSIS_2026_05_14.md`
  - 실제 상품 상세 URL 기준 결과: `docs/CATEGORY_PRODUCT_ONLY_ANALYSIS_2026_05_14.md`
  - 기획팀 공유 문안: `docs/CATEGORY_AUTO_CLASSIFICATION_PLANNING_REPLY_2026_05_14.txt`

## 목적 (Why)
### 무엇을 추가/변경하나?
- 상품 링크 수집 방식을 dev 기준의 정적 HTML 수집 방식에서 브라우저 렌더링 기반 수집 방식으로 개선합니다.
- 상품명, 설명, 본문 텍스트, 이미지, 가격 후보를 실제 브라우저에서 렌더링된 결과 기준으로 추출합니다.
- 카테고리 자동분류는 수집된 상품명/설명/본문 키워드를 기준으로 1차 추천값을 제공합니다.
- 검색/카테고리 URL 포함 결과와 실제 상품 상세 URL 기준 결과를 분리해 기록합니다.

### 왜 필요한가? (User value / Problem)
- 현재 dev 기준의 `JsoupPageFetcher`는 정적 HTML 응답을 기준으로 메타데이터를 추출합니다.
- 일부 쇼핑몰은 상품 정보가 클라이언트 렌더링 이후에 노출되거나, 정적 응답만으로는 상품명/본문/이미지 후보가 충분히 잡히지 않습니다.
- 유저가 쇼핑 링크를 저장할 때 카테고리가 자동으로 추천되려면, 실제 브라우저에서 보이는 상품 정보 기준으로 수집 안정성을 높여야 합니다.
- 자동분류는 확정값이 아니라 유저가 수정할 수 있는 추천값으로 제공하는 방향이므로, 상품 상세 기준의 보수적인 정확도 수치가 필요합니다.

### Out of scope / Non-goals (필요 시)
> 혼란을 방지하기 위해 명시적으로 제외하는 항목
- ❌ 프론트 카테고리 선택 UI 구현
- ❌ 카테고리 체계 자체 변경
- ❌ 머신러닝 기반 분류 모델 도입
- ❌ 검색 URL 포함 결과를 실제 상품 상세 기준 정확도로 간주
- ❌ 쿠팡 크롤링 대응

### 비즈니스/기술적 배경
- 기획 방향은 카테고리를 자동으로 1차 선택해두고, 유저가 필요하면 수정할 수 있는 구조입니다.
- 브라우저 기반 수집 실험에서 검색/카테고리 URL 포함 기준은 70개 중 70개 수집 성공, 분류 정확도 92.9%였습니다.
- 실제 상품 상세 URL만 기준으로 보면 58개 중 58개 수집 성공, 분류 정확도 81.0%였습니다.
- 실제 상품 저장 상황에 더 가까운 기준은 상품 상세 URL 기준이므로, 운영 판단에는 81.0%를 보수적인 기준으로 사용합니다.
- 현재 기획 카테고리는 패션, 뷰티, 라이프, 디지털, 기타 5개 대분류이므로, 실험에 사용한 8개 분류보다 운영 난이도는 낮습니다.

---

<details><summary>전체 설계 (선택사항)</summary>

## 전체 설계 (How)

### 핵심 아이디어/접근법
- 상품 링크 수집 단계에서 실제 브라우저 컨텍스트로 페이지를 열고, 렌더링 완료 후 DOM/meta/body/image/price 후보를 추출합니다.
- 검색/카테고리 URL은 상품 상세 URL을 찾는 데 사용할 수 있지만, 최종 정확도 측정은 실제 상품 상세 URL 기준으로 분리합니다.
- 카테고리 분류는 도메인 성향보다 상품명/설명/본문 키워드를 우선 신호로 사용합니다.
- 분류 근거가 약한 경우에는 `ETC` 또는 낮은 신뢰도 상태로 처리해 유저 수정 가능성을 전제로 둡니다.

### 주요 컴포넌트/모듈
- `ShoppingLinkImportService`
- `PageFetcher`
- `JsoupPageFetcher` 대체 또는 브라우저 기반 구현체
- `ItemCategory`
- `ShoppingLinkImportServiceTest`
- 카테고리/크롤링 검증 스크립트

### 데이터 흐름/상태 변화
```text
상품 URL 입력
  -> URL 정규화
  -> 브라우저 렌더링 기반 페이지 수집
  -> title/meta/body/image/price 후보 추출
  -> 상품명/설명/본문 키워드 기반 카테고리 추천
  -> 저장 후보 응답에 추천 카테고리와 신뢰도 포함
  -> 프론트에서 기본 선택값으로 표시 후 유저 수정 가능
```

### 아키텍처 다이어그램 (선택)
```text
POST /api/v1/items/import-link
        |
        v
ShoppingLinkImportService
        |
        v
Browser-based PageFetcher
        |
        v
Rendered HTML / DOM / Meta / Body
        |
        v
Metadata Extraction + Category Recommendation
```

---

</details>

## PR 분할 전략 (리뷰어가 부담을 느끼질 않도록)
- [ ] Single PR
- [x] Stacked PR (의존성 체인)
- [ ] Parallel Change (Expand-Migrate-Contract)
- [ ] Branch by Abstraction

### PR 계획
1. PR #1: 브라우저 기반 수집 방식과 카테고리 자동분류 정확도 검증 리포트 추가
2. PR #2: 브라우저 렌더링 기반 `PageFetcher` 구현 및 설정 기반 활성화
3. PR #3: 카테고리 분류 보강과 회귀 테스트 추가

### 머지 기준
- 상품 상세 URL 기준 수집 성공률과 분류 정확도 수치가 PR 본문에 포함되어야 합니다.
- 기존 URL 정규화, private host 차단, redirect 제한 등 보안/검증 흐름이 유지되어야 합니다.
- 자동분류 결과는 확정값이 아니라 유저 수정 가능한 추천값이라는 전제가 유지되어야 합니다.
- 브라우저 기반 수집은 설정값으로 켜고 끌 수 있어야 합니다.

---

## 전체 Acceptance Criteria (AC)
- [ ] AC1: 상품 링크 수집이 브라우저 렌더링 기반 방식으로 동작한다.
- [ ] AC2: 상품명, 설명, 본문 키워드 기반으로 카테고리 추천값을 반환한다.
- [ ] AC3: 검색/카테고리 URL 포함 결과와 실제 상품 상세 URL 기준 결과가 문서로 분리되어 있다.
- [ ] AC4: 실제 상품 상세 URL 기준 수집 성공률 100.0%, 분류 정확도 81.0% 결과가 PR 증빙에 포함되어 있다.
- [ ] AC5: 기존 `ShoppingLinkImportService` 단위 테스트가 통과하고, 카테고리 분류 회귀 테스트가 추가되어 있다.

---

## 영향 범위
- [ ] ⚠️ Breaking change (무엇이 깨지는지 / 마이그레이션 전략: )
- [ ] API 스펙 변경 (요약: )
- [ ] DB 스키마 변경 (마이그레이션: )
- [x] 설정/환경변수 변경 (항목: 브라우저 기반 수집 활성화 설정)
- [x] 라이브러리 추가/업그레이드
- [x] 캐시/큐/외부 연동 영향
- [ ] UX/UI 변경

---

<details><summary>롤아웃/관측</summary>

## 롤아웃/관측 (배포 전략)
### Feature Flag / 단계적 배포
- [ ] 사용 안함
- [x] 사용함 (Flag명/기본값/롤아웃: `shopping.import.browser-fetch.enabled`, 기본값 false, 운영 전 샘플 재측정 후 활성화)

### 성공 지표/메트릭
- 상품 링크 수집 성공률
- `SUCCESS` / `PARTIAL` / 실패 응답 비율
- 카테고리 추천값이 `ETC`로 떨어지는 비율
- 상품 상세 URL 기준 분류 정확도 재측정 결과

### 모니터링/알림
- 확인할 대시보드/지표:
  - `/api/v1/items/import-link` 응답 상태
  - 외부 쇼핑몰 요청 실패율
  - 브라우저 렌더링 수집 시간
- 에러 키워드:
  - `Failed to fetch shopping page`
  - `Unable to extract shopping metadata`
  - `Shopping page returned`
  - `timeout`

</details>

---

## 리스크 & 롤백
### 리스크
- 브라우저 렌더링 방식은 정적 HTML 수집보다 실행 시간이 길어질 수 있습니다.
- 쇼핑몰 DOM 구조나 차단 정책이 바뀌면 특정 사이트의 수집 품질이 달라질 수 있습니다.
- 검색 URL은 검색어가 분류 힌트로 작용하므로 실제 상품 상세 기준 정확도와 섞어 해석하면 수치가 과대평가될 수 있습니다.
- 브라우저 런타임 의존성이 추가되므로 로컬/CI/배포 환경에서 실행 가능 여부를 확인해야 합니다.

### 롤백 전략
- [x] 각 PR은 독립적으로 revert 가능
- [x] Feature flag로 비활성화 가능
- [ ] 데이터 롤백 필요 (절차: )

---

## 의존성
- 선행 이슈/PR:
  - 기존 상품 링크 import 기본 흐름
- 블로킹 이슈:
  - 브라우저 런타임을 배포 환경에서 사용할 수 있는지 확인 필요

---

<details><summary>증빙/참고 자료</summary>

## 증빙/참고 자료
### 설계/기획
- 요구사항 문서:
  - `docs/CATEGORY_AUTO_CLASSIFICATION_PLANNING_REPLY_2026_05_14.txt`
- 관련 리포트:
  - `docs/CATEGORY_VISIBLE_BROWSER_ANALYSIS_2026_05_14.html`
  - `docs/CATEGORY_VISIBLE_BROWSER_ANALYSIS_2026_05_14.md`
  - `docs/CATEGORY_PRODUCT_ONLY_ANALYSIS_2026_05_14.html`
  - `docs/CATEGORY_PRODUCT_ONLY_ANALYSIS_2026_05_14.md`

### 실험 결과
- 검색/카테고리 URL 포함:
  - 샘플: 70개
  - 수집 성공: 70/70 (100.0%)
  - 분류 정확도: 65/70 (92.9%)
- 실제 상품 상세 URL만:
  - 샘플: 58개
  - 수집 성공: 58/58 (100.0%)
  - 분류 정확도: 47/58 (81.0%)
- 실험 방식:
  - Playwright Chromium visible/headed
  - 순차 실행
  - 렌더 대기 적용
  - 검색/카테고리 URL과 실제 상품 상세 URL 기준 분리

### 구현 완료 후 (전체 PR 머지 시)
- 스크린샷/영상:
- 로그/메트릭:
- 배포 결과:

</details>
