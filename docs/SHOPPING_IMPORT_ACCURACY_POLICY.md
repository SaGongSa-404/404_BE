# 쇼핑 링크 가져오기 정확성 보장 방안

## 결론

임의의 외부 상품 URL에서 항상 값을 가져오는 것과, 가져온 값이 항상 정확한 것을 동시에 100% 보장할 수는 없다. 외부 페이지는 변경·차단·개인화될 수 있고 같은 페이지 안에서도 상품 데이터와 공유 카드 데이터가 서로 다를 수 있기 때문이다.

대신 **성공으로 반환한 결과의 정확도 100%**는 목표로 삼을 수 있다. 제목·가격·통화·이미지를 신뢰 가능한 상품 데이터에서 모두 검증한 경우에만 성공시키고, 하나라도 검증할 수 없으면 `UNVERIFIED` 또는 실패로 종료하는 fail-closed 정책을 사용한다. 이 정책은 성공률이나 커버리지보다 잘못된 자동 저장을 방지하는 것을 우선한다.

## 보장할 계약

### `SUCCESS_VERIFIED`

다음 조건을 모두 만족할 때만 성공으로 처리한다.

- 정규화한 최종 URL과 추출 데이터의 상품 식별자가 같은 상품을 가리킨다.
- 제목, 가격, 통화, 대표 이미지가 모두 존재한다.
- 각 필드에 출처와 원본 경로가 기록된다.
- 상품용 구조화 데이터 또는 사이트의 상품 API/상태값에서 검증된다.
- 충돌하는 후보가 있으면 도메인 규칙으로 어느 값이 상품값인지 입증된다.
- 이미지 URL은 접근 가능하고 실제 이미지 응답을 반환한다.

### `UNVERIFIED` 또는 실패

다음 경우에는 값을 추측해 성공시키지 않는다.

- 필수 필드가 하나라도 없다.
- 상품 데이터와 Open Graph 값이 충돌하지만 도메인 규칙이 없다.
- 통화나 소수 가격을 현재 데이터 모델로 정확히 표현할 수 없다.
- 로그인, 봇 차단, 지역·옵션 선택 때문에 현재 상품값을 확정할 수 없다.
- 서로 다른 상품의 redirect 또는 shell 페이지로 판단된다.

`PARTIAL` 결과는 화면에 참고값으로 보여줄 수는 있지만 자동 저장 성공이나 부하 테스트 기능 성공으로 집계하지 않는다.

## 현재 구현의 정확성 문제

| 영역 | 현재 동작 | 문제 |
| --- | --- | --- |
| 제목 | `og:title`을 JSON-LD 상품명보다 우선 | 사이트명·설명이 붙은 공유 제목 선택 |
| 이미지 | `og:image`를 JSON-LD 상품 이미지보다 우선 | 공유 카드용 crop/cover 이미지 선택 |
| 가격 | 여러 후보 중 먼저 파싱되는 정수 선택 | 할인·원가 구분과 소수 가격 손실 가능 |
| 통화 | 공유 링크 가져오기를 사실상 KRW로 처리 | USD 등 외화 결과가 틀림 |
| 성공 판정 | 제목과 가격/이미지 중 하나만 있어도 `PARTIAL` | 검증되지 않은 값도 job `SUCCEEDED` 가능 |
| 브라우저 fallback | HTTP 오류나 일부 오류 shell에서만 실행 | HTTP 200이지만 데이터가 누락·충돌한 페이지를 놓침 |
| Playwright | 전역 동기화 | worker 수를 늘려도 브라우저 경로는 직렬 처리 |

## 추출 우선순위

공통 `first non-blank` 방식 대신 필드 후보와 출처를 수집한 뒤 도메인별 검증기로 확정한다.

권장 신뢰 순서는 다음과 같다.

1. 사이트의 동일 상품용 first-party API 응답
2. 페이지 내 상품 상태 JSON
3. `Product` JSON-LD의 `name`, `image`, `offers.price`, `offers.priceCurrency`
4. 상품 상세 DOM
5. Open Graph/Twitter 공유 메타데이터

[Google의 Product 구조화 데이터 문서](https://developers.google.com/search/docs/appearance/structured-data/product-snippet?hl=en)는 상품명과 이미지, `Offer.price`, `priceCurrency`를 상품 데이터로 정의하며 소수 가격도 예시로 든다. [Schema.org Product](https://schema.org/Product)와 [Offer](https://schema.org/Offer)도 상품 이미지·이름·가격·ISO 4217 통화의 의미를 명시한다. 반면 [Open Graph Protocol](https://ogp.me/)의 제목과 이미지는 소셜 그래프에서 객체를 표현하기 위한 값이므로 상품 원본 필드와 같다고 가정하지 않는다.

## 승인 도메인별 전략

| 도메인 | 우선 데이터 | 필수 보완 |
| --- | --- | --- |
| 무신사 | 페이지의 상품 상태 JSON 또는 상품 API | `goodsNm`, 판매가, 상품 썸네일을 같은 상품 ID로 검증 |
| 당근 | `Product` JSON-LD 또는 상품 API | 공유 cover 대신 상품 `image`, 가격·통화를 함께 검증 |
| 번개장터 | `Product` JSON-LD 또는 상품 API | 글로벌 USD 소수 가격과 `priceCurrency` 지원 |

도메인 adapter가 지원하지 않는 새 페이지 구조는 generic selector로 임의 성공시키지 않고 `UNVERIFIED`로 보낸다.

## 데이터 모델과 코드 변경

### 필드별 후보와 출처

개념적으로 다음 정보를 보존한다.

```text
ExtractedField<T>
- value
- source: SITE_API | EMBEDDED_STATE | JSON_LD | DOM | OPEN_GRAPH
- sourcePath
- confidence
- validationResult
```

최종 결과에는 각 필드의 provenance를 함께 저장해 오탐 원인을 재현할 수 있어야 한다.

### 가격과 통화

현재 `Integer listedPrice`만으로는 `8.81 USD`를 정확히 표현할 수 없다. 다음 중 하나가 필요하다.

- 권장: `BigDecimal amount`와 ISO 4217 `currencyCode`
- 대안: 통화별 최소 단위 정수와 currency exponent

3일 안에 API·DB 호환 마이그레이션이 어렵다면, 외화·소수 가격은 당분간 성공시키지 않고 `UNVERIFIED`로 처리해야 정확도 약속을 지킬 수 있다.

### 브라우저 fallback

HTTP 오류뿐 아니라 다음 조건에서도 브라우저 수집으로 전환한다.

- 필수 상품 필드 누락
- 상품 데이터와 공유 메타데이터 충돌
- 동일 상품 ID 검증 실패
- 렌더링 이후에만 상품 API가 호출되는 페이지

Playwright는 [network event와 응답 대기](https://playwright.dev/java/docs/network)로 first-party 상품 API 응답을 수집하고, 필요할 때 [페이지 컨텍스트 평가](https://playwright.dev/java/docs/evaluating)로 렌더링된 상태값을 읽을 수 있다. 브라우저 병렬 실행을 도입할 경우 요청 격리는 [BrowserContext](https://playwright.dev/java/docs/browser-contexts) 단위로 검증한다.

## 검증 체계

### 고정 contract fixture

CI에서는 외부 사이트의 현재 상태에 의존하지 않도록 승인 도메인 응답을 fixture로 보관하고 다음 사례를 테스트한다.

- 정상가와 할인가가 함께 있는 상품
- 품절 상품
- 옵션별 가격이 다른 상품
- KRW 정수 가격과 USD 소수 가격
- 공유 제목에 사이트명이 붙는 상품
- 공유 cover와 상품 이미지가 다른 상품
- redirect, 차단 페이지, HTTP 200 오류 shell
- 필수 필드 누락과 상충 데이터

### live canary

실사이트 변화 감지는 CI의 합격 조건과 분리한 정기 canary로 수행한다. 승인된 고정 URL의 기대값을 버전 관리하고, 변경 감지 시 adapter를 검토한 뒤 기대값을 갱신한다.

### 정확성 지표

- `verified_success_precision = 정확한 SUCCESS_VERIFIED / 전체 SUCCESS_VERIFIED`
- 목표: `100%`
- `verified_coverage = SUCCESS_VERIFIED / 전체 요청`
- 오답을 성공시키지 않기 위해 precision과 coverage를 분리한다.
- 부하 테스트도 job 완료가 아니라 제목·가격·통화·이미지가 모두 기대값과 일치해야 기능 성공으로 집계한다.

## 3일 이내 실행안

### 1일차

- `SUCCESS_VERIFIED`와 `UNVERIFIED` 판정 추가
- 필드 후보·provenance 모델 추가
- 무신사·당근·번개장터 도메인 adapter 구현
- JSON-LD/상품 상태를 Open Graph보다 우선하도록 변경

### 2일차

- 필수 필드 누락·충돌 시 browser fallback 추가
- 외화·소수 가격을 지원하거나 안전하게 `UNVERIFIED` 처리
- 도메인별 fixture와 회귀 테스트 추가

### 3일차

- 승인 URL live canary 실행
- 정확성 oracle을 적용한 worker 1·2·3 재측정
- Playwright 최악 조건의 CPU/RSS와 완료율 확인
- 결과를 근거로 worker 기본값과 ADR 재검토

## 완료 기준

- 승인 세 도메인의 지원 fixture에서 `SUCCESS_VERIFIED` 오답 0건
- 필수 필드 누락·충돌·미지원 통화가 성공으로 반환되지 않음
- 실사이트 기대값과 제목·가격·통화·이미지가 모두 일치
- Playwright 사용 여부와 필드별 출처를 로그/결과로 추적 가능
- 엄격한 기능 검증을 포함한 100건 부하 테스트 결과 확보

이 기준은 모든 요청의 성공률 100%를 약속하지 않는다. **성공이라고 반환한 요청에는 검증되지 않은 상품값이 없다는 것**을 보장하는 기준이다.
