# 구매 비교 / 실제 구매 상태 API

기획 추적: https://github.com/SaGongSa-404/404_BE/issues/263

## 이번 구현

기존 상품 담기 API로 저장한 상품을 두 개 비교하고, 별도 구매 상태/메모를 기록한다. 실제 구매 확인에만 월 예산 지출을 반영한다. 새 구매 기록에는 가짜 설문 답변이나 합리성 평가를 생성하지 않는다.

- GET `/api/v1/purchase-items/availability`: `enabled`, `hasRecords`.
- GET `/api/v1/purchase-items?records=false&offset=0&limit=50`: 고민 중 상품. `records=true`는 신규 구매/거절 및 기존 결정 기록. 응답 `items`, `nextOffset` (마지막 페이지 null). offset 0..100000, limit 1..100.
- GET `/api/v1/purchase-items/{id}`: 본인 상품의 비교 정보/상태/메모/실제 금액/날짜/revision.
- PUT `/api/v1/purchase-items/{id}`: 상태와 메모를 함께 대체한다.

```json
{
  "mutationId": "8464a22f-6739-4b57-849a-67801938d386",
  "expectedRevision": 0,
  "status": "PURCHASED",
  "note": "발이 편해서 선택",
  "actualPrice": 85000,
  "purchasedOn": "2026-09-08"
}
```

상태: CONSIDERING / PURCHASED / DECLINED. note는 선택(최대 500자). PURCHASED에는 원화 결제 금액(0 이상 int 범위)과 구매 날짜가 필요하다. 다른 상태에는 금액/날짜가 null이어야 한다. 미래 날짜는 사용자 시간대 기준 거절한다. 해당 월의 예산이 없고 이전 예산에서 생성할 수도 없는 경우 409이며 임의 예산을 만들지 않는다.

## 일관성과 기존 앱 호환

- 상품의 물리 삭제(계정 탈퇴/QA 초기화)는 소유된 새 기록과 재시도 JSON을 FK cascade로 함께 삭제한다. 예산 삭제보다 상품 삭제가 먼저인 기존 탈퇴 순서를 지원한다.
- 신규 V25 테이블은 `purchase_outcomes`, `purchase_outcome_mutations`. 기존 결정 테이블 제약과 응답 계약은 유지한다.
- 새 상태의 saved_items 호환 표시는 CONSIDERING→SAVED, PURCHASED→GO, DECLINED→STOP이다. 설문 결정 레코드는 만들지 않는다.
- 기존 설문 결정은 LEGACY_GO/LEGACY_STOP으로 표시하고 새 API 변경을 거절한다. 기존 소비 기록에서 수정한다. 기존 GO를 실제 결제로 소급 확인하지 않는다.
- 새 상태가 한 번 기록된 상품은 기존 v1 결정 완료 API에서 409로 거절한다. 양쪽 모두 같은 saved_items 행 잠금을 사용해 교차 중복 지출을 방지한다.
- 사용자 행(FOR NO KEY UPDATE로 기존 경로의 FK 검사 허용)→상품 행→정렬된 예산 행 순서로 새 쓰기를 직렬화한다. expectedRevision 충돌은 409. 같은 상품의 같은 mutationId/동일 JSON 재시도는 원래 응답을 반환한다. 다른 JSON에 키 재사용 시 409.
- 구매 정정은 기존 월에서 이전 금액을 빼고 새 월에 새 금액을 더한다. 총액 범위를 검사하며 저장 실패 시 전체 트랜잭션을 되돌린다. 재시도 후 FE는 상세를 다시 읽어 더 최신 revision이 있으면 표시한다.
- 고민 중 복귀 시 동일 URL의 SAVED 상품이 있으면 409. 기존 삭제는 구매/거절 상태에 적용되지 않는다.
- 모든 접근은 본인 상품 및 ACTIVE/온보딩 완료 사용자로 제한한다. 다른 사용자의 상품은 404.

## 첫 업데이트 범위와 제약

비교 화면은 기존 상품을 실시간 조회하며 비교방 테이블은 만들지 않는다. 목록은 offset 페이지 방식이라 다른 기기에서 목록 변경 중에는 페이지 이동 시 누락/중복이 가능하고 새로고침으로 다시 조회한다.

월별 지출·구매 건수·카테고리 금액에는 새 구매 기록도 합산한다. 합리성 통계·캐릭터 평가·7일 회고 및 기존 소비 상세 목록은 기존 결정 기록 기준을 유지한다. **새 구매 기록은 새 비교·기록 화면에서 확인하며 설문 평가/회고에 자동 편입하지 않는다.** 새 구매 기록에 대한 회고 연결은 별도 계약이 필요하므로 이번 구현에 포함하지 않는다. 첫 업데이트의 범위를 비교·구매 상태·금액 정정으로 제한한 결과다. 해당 차이는 GA 전 실기기 검토 대상이며 기존 통계를 모든 구매의 통계로 표시해서는 안 된다.

## 활성화와 출시

현재 기본값 OFF. 서버 배포나 Play 업로드는 이 PR 작업에 포함되지 않는다.

- `PURCHASE_COMPARISON_ENABLED=true`: 새 쓰기 활성화.
- `PURCHASE_COMPARISON_ALLOWED_USER_IDS=<UUID,...>`: 내부 테스트 계정 허용. 비어 있으면 활성화 상태에서 모든 적격 계정 허용.
- read API는 OFF에서도 본인 기록 조회를 유지한다. FE는 hasRecords 사용자에게 읽기 진입점을 유지한다.
- 기존 앱 호환 BE를 먼저 배포한 후 본인 기기에서 새 FE를 내부 테스트하고 작은 범위로 GA. 정식 versionCode는 Play Console의 기존 최대값 확인 후 지정한다.
- 운영 데이터가 생긴 뒤 V25 테이블을 삭제하는 rollback은 금지. 기능을 끄고 읽기를 유지하면서 후속 수정한다. 기존 서버 버전으로 돌아가는 경우 새 클라이언트 API 지원과 상태 접근을 다시 검증한다.

## 검증 명령

```sh
./gradlew test jacocoTestReport bootJar --console=plain
```

실기기 화면, Play Console, 실제 서버 배포/FCM 수신은 로컬 테스트 결과와 별도다.
