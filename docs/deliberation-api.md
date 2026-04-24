# 구매 숙려 화면 API 명세서

## 개요

위시리스트에 상품을 추가한 후 **24시간이 경과**하면 구매 숙려 화면에 접근할 수 있습니다.
사용자는 4가지 충동구매 점검 질문에 Y/N 응답 후 최종 구매 여부(살게요 / 참을게요)를 결정합니다.

---

## 화면 구성

| 섹션 | 내용 |
|------|------|
| 상단 | 상품 정보 (이름, 가격, 이미지, 카테고리) |
| 상단 | 이번 달 예산 / 남은 예산 |
| 중간(스크롤) | 4가지 충동구매 점검 질문 (Y/N) |
| 중간(스크롤) | Yes 2개 이상 → 경고, 1개 이하 → 경고 없음 |
| 하단(스크롤 끝) | 이달 소비 금액 / 비합리적 선택 횟수 / 기회비용 |
| 하단(스크롤 끝) | **참을게요** / **살게요** 버튼 |

---

## 충동구매 점검 질문 (4가지)

| ID | 질문 | Yes의 의미 |
|----|------|-----------|
| 1 | 오늘 갑자기 갖고 싶어진 건가요? | 충동적 욕구 |
| 2 | 비슷한 물건을 이미 갖고 있나요? | 중복 구매 |
| 3 | 구매하지 않아도 일상생활에 불편함이 없나요? | 필수품 아님 |
| 4 | 한 달 뒤에는 필요하지 않을 것 같나요? | 지속성 없음 |

- **Yes 2개 이상** → 경고 노출
- **Yes 1개 이하** → 경고 없음

---

## 용어 정의

| 용어 | 정의 |
|------|------|
| 이번 달 사용 금액 | 이번 달 BOUGHT 처리된 위시 가격 합계 |
| 비합리적 선택 | 이번 달 BOUGHT 결정 중 숙려 질문 Yes가 2개 이상이었던 구매 수 |
| 기회비용 | 이번 달 RESTRAINED(절제) 처리된 위시 가격 합계 |

---

## API 명세

### 1. 구매 숙려 화면 조회

```
GET /api/wishes/{wishId}/deliberation
```

**Path Variable**

| 이름 | 타입 | 설명 |
|------|------|------|
| wishId | UUID | 대상 위시 ID |

**응답 200 OK**

```json
{
  "wish": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "에어팟 프로",
    "price": 350000,
    "imageUrl": "https://example.com/image.jpg",
    "category": "ELECTRONICS"
  },
  "budget": {
    "monthlyBudget": 500000,
    "spentAmount": 150000,
    "remainingBudget": 350000
  },
  "monthStats": {
    "spentAmount": 150000,
    "irrationalCount": 2,
    "opportunityCost": 80000
  },
  "questions": [
    { "id": 1, "text": "오늘 갑자기 갖고 싶어진 건가요?" },
    { "id": 2, "text": "비슷한 물건을 이미 갖고 있나요?" },
    { "id": 3, "text": "구매하지 않아도 일상생활에 불편함이 없나요?" },
    { "id": 4, "text": "한 달 뒤에는 필요하지 않을 것 같나요?" }
  ]
}
```

**오류 응답**

| HTTP | 코드 | 설명 |
|------|------|------|
| 404 | WISH_NOT_FOUND | 위시를 찾을 수 없음 |
| 403 | FORBIDDEN | 본인 위시가 아님 |
| 403 | DELIBERATION_NOT_READY | 24시간 미경과 |
| 409 | ALREADY_DECIDED | 이미 구매/절제 결정된 위시 |

---

### 2. 숙려 답변 제출 및 구매 결정

```
POST /api/wishes/{wishId}/deliberation
```

**Path Variable**

| 이름 | 타입 | 설명 |
|------|------|------|
| wishId | UUID | 대상 위시 ID |

**요청 Body**

```json
{
  "answers": [true, false, true, false],
  "decision": "RESTRAINED"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| answers | boolean[4] | Y | 질문 1~4에 대한 Y(true)/N(false) 순서대로 |
| decision | string | Y | `BOUGHT`(살게요) 또는 `RESTRAINED`(참을게요) |

**응답 200 OK**

```json
{
  "wishId": "550e8400-e29b-41d4-a716-446655440000",
  "decision": "RESTRAINED",
  "yesCount": 2,
  "warningTriggered": true,
  "monthStats": {
    "spentAmount": 150000,
    "irrationalCount": 2,
    "opportunityCost": 430000
  }
}
```

| 필드 | 설명 |
|------|------|
| yesCount | Yes 답변 수 |
| warningTriggered | Yes 2개 이상 여부 (경고 노출 여부) |
| monthStats.spentAmount | 이번 달 총 소비 금액 (결정 반영 후) |
| monthStats.irrationalCount | 이번 달 비합리적 선택 횟수 (결정 반영 후) |
| monthStats.opportunityCost | 이번 달 기회비용 (결정 반영 후) |

**오류 응답**

| HTTP | 코드 | 설명 |
|------|------|------|
| 400 | INVALID_INPUT | answers 길이 != 4 또는 decision 값 오류 |
| 404 | WISH_NOT_FOUND | 위시를 찾을 수 없음 |
| 403 | FORBIDDEN | 본인 위시가 아님 |
| 403 | DELIBERATION_NOT_READY | 24시간 미경과 |
| 409 | ALREADY_DECIDED | 이미 결정된 위시 |

---

## DB 스키마

### wish_deliberations 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | UUID | PK |
| wish_id | UUID | FK → wishes.id (unique) |
| answer1 | BOOLEAN | 질문1 응답 |
| answer2 | BOOLEAN | 질문2 응답 |
| answer3 | BOOLEAN | 질문3 응답 |
| answer4 | BOOLEAN | 질문4 응답 |
| yes_count | INT | Yes 합계 |
| warning_triggered | BOOLEAN | 경고 발동 여부 (yes_count >= 2) |
| created_at | DATETIME | 생성 시각 |
| updated_at | DATETIME | 수정 시각 |

---

## 비즈니스 규칙 요약

1. **24시간 숙려**: `wishes.created_at + 24h > now` 이면 접근 불가
2. **중복 결정 방지**: `wishes.status != PENDING` 이면 접근 불가
3. **경고 조건**: `yesCount >= 2`
4. **BOUGHT 처리 시** `monthly_budgets.spent_amount` 자동 증가
5. **비합리적 선택**: 경고가 발동되었음에도(warningTriggered=true) BOUGHT 결정된 위시