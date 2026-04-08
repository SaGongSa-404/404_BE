# 위굴(Wigul) 백엔드 기능 정의서

> 작성일: 2026-04-08  
> 대상: 백엔드 개발팀  
> 기준 문서: PRD v1.0, 8차 팀 회의록

---

## 목차

1. [도메인 모델 및 스키마](#1-도메인-모델-및-스키마)
2. [온보딩](#2-온보딩)
3. [홈화면](#3-홈화면)
4. [상품 저장 (위시)](#4-상품-저장-위시)
5. [구매 숙려 화면](#5-구매-숙려-화면)
6. [소셜 피드](#6-소셜-피드)
7. [마이페이지](#7-마이페이지)
8. [알림](#8-알림)
9. [공통 규격](#9-공통-규격)

---

## 1. 도메인 모델 및 스키마

### 1.1 핵심 엔티티

```
User
 ├── id (UUID)
 ├── social_provider (KAKAO | GOOGLE)
 ├── social_id (string)
 ├── nickname (string)
 ├── monthly_budget (integer, 원 단위)
 ├── raccoon_status (NORMAL | CAUTION | DANGER | DARK)
 ├── raccoon_name (string, nullable — 추후 확장)
 ├── impulse_frequency (NEVER | RARELY | SOMETIMES | OFTEN | ALWAYS)  ← 온보딩 설문
 ├── created_at
 └── updated_at

Wish (상품 위시)
 ├── id (UUID)
 ├── user_id (FK → User)
 ├── title (string)
 ├── price (integer)
 ├── image_url (string, nullable)
 ├── product_url (string, nullable)
 ├── category (FASHION | BEAUTY | ELECTRONICS | FOOD | HOBBY | ETC)
 ├── status (PENDING | BOUGHT | RESTRAINED)
 ├── decision_at (timestamp, nullable)         ← 참았어요/샀어요 선택 시각
 ├── regret (boolean, nullable)                ← 구매 후회 여부 (구매 후 팝업 수집)
 ├── regret_recorded_at (timestamp, nullable)
 ├── created_at
 └── updated_at

MonthlyBudget (월별 예산 스냅샷)
 ├── id (UUID)
 ├── user_id (FK → User)
 ├── year_month (string, "2026-04")
 ├── budget_amount (integer)
 ├── spent_amount (integer)                    ← 샀어요 확정된 wish.price 합산
 └── updated_at

SocialPost (소셜 피드 게시글)
 ├── id (UUID)
 ├── user_id (FK → User)
 ├── wish_id (FK → Wish, nullable)             ← 위시에서 공유한 경우
 ├── product_url (string, nullable)
 ├── image_url (string, nullable)
 ├── title (string)
 ├── body (string, nullable)
 ├── price (integer, nullable)
 ├── category (same enum as Wish)
 ├── go_count (integer, default 0)
 ├── stop_count (integer, default 0)
 ├── created_at
 └── updated_at

Vote (소셜 투표)
 ├── id (UUID)
 ├── post_id (FK → SocialPost)
 ├── user_id (FK → User)
 ├── vote_type (GO | STOP)
 └── created_at

Comment (소셜 댓글)
 ├── id (UUID)
 ├── post_id (FK → SocialPost)
 ├── user_id (FK → User)
 ├── body (string)
 ├── created_at
 └── updated_at

Notification
 ├── id (UUID)
 ├── user_id (FK → User)
 ├── type (DELIBERATION_REMIND | VOTE_RESULT | REGRET_CHECK | RACCOON_STATUS | CHALLENGE)
 ├── title (string)
 ├── body (string)
 ├── ref_id (UUID, nullable)                   ← wish_id 또는 post_id
 ├── ref_type (WISH | POST, nullable)
 ├── is_read (boolean, default false)
 └── created_at
```

### 1.2 너굴이 상태 전환 기준

| 상태 | 조건 | 비고 |
|------|------|------|
| NORMAL | 이번 달 소비 < 예산 60% | 기본 상태 |
| CAUTION | 예산 60% 이상 ~ 80% 미만 | 경고 |
| DANGER | 예산 80% 이상 초과 | 위험 |
| DARK | DANGER 상태에서 추가 구매 발생 | 흑화 |

- 상태 계산은 `spent_amount / budget_amount` 비율 기반
- 상태는 Wish의 `status = BOUGHT` 업데이트 시마다 재계산
- 매달 1일 00:00 KST에 `spent_amount` 리셋 → 상태 NORMAL로 초기화

---

## 2. 온보딩

### 2.1 소셜 로그인

#### `POST /auth/social/login`

소셜 인증 토큰을 받아 JWT를 발급한다.

**요청**
```json
{
  "provider": "KAKAO" | "GOOGLE",
  "access_token": "소셜 플랫폼에서 받은 access token"
}
```

**처리 로직**
1. 소셜 플랫폼 서버에 `access_token` 검증 요청
2. 플랫폼에서 `social_id`, 이메일 수신
3. DB에서 `(provider, social_id)` 조합으로 User 조회
   - 기존 유저 → JWT 발급 후 반환 (`is_new_user: false`)
   - 신규 유저 → User 레코드 생성 후 JWT 발급 (`is_new_user: true`)

**응답**
```json
{
  "access_token": "JWT",
  "refresh_token": "JWT",
  "is_new_user": true | false
}
```

- `is_new_user: true`인 경우 클라이언트는 온보딩 플로우로 진행
- `is_new_user: false`인 경우 홈화면으로 바로 진입
- JWT 만료: access 1시간, refresh 30일

---

#### `POST /auth/refresh`

**요청**
```json
{ "refresh_token": "JWT" }
```

**응답**
```json
{ "access_token": "JWT" }
```

---

#### `POST /auth/logout`

서버 측 refresh token 무효화 (블랙리스트 or 삭제)

**요청**: Authorization 헤더 필요  
**응답**: `204 No Content`

---

### 2.2 닉네임 입력

#### `PATCH /users/me/nickname`

**요청**
```json
{ "nickname": "귀여운너굴" }
```

**유효성 검사**
- 1~10자
- 특수문자 불허 (한글, 영문, 숫자 허용)
- 중복 허용 (닉네임 고유성 보장 불필요)

**응답**
```json
{ "nickname": "귀여운너굴" }
```

---

### 2.3 월 예산 설정

#### `PATCH /users/me/budget`

**요청**
```json
{ "monthly_budget": 300000 }
```

**처리 로직**
1. `users.monthly_budget` 업데이트
2. 현재 년월의 `MonthlyBudget` 레코드가 없으면 생성, 있으면 `budget_amount` 갱신
3. 너굴이 상태 재계산

**응답**
```json
{
  "monthly_budget": 300000,
  "raccoon_status": "NORMAL"
}
```

**제약**
- 0원 이하 입력 불가
- 온보딩 완료 전(budget 미설정)에는 위시 등록 API 차단 (`403`)

---

### 2.4 온보딩 설문 (충동구매 후회 빈도)

#### `PATCH /users/me/onboarding-survey`

온보딩 1문항: "충동구매 후 후회한 경험이 얼마나 자주 있나요?"

**요청**
```json
{
  "impulse_frequency": "NEVER" | "RARELY" | "SOMETIMES" | "OFTEN" | "ALWAYS"
}
```

**응답**: `200 OK`
```json
{ "impulse_frequency": "OFTEN" }
```

**비고**: KPI 지표화 및 향후 개인화 기능에 활용. MVP에서는 저장만 하고 별도 로직 없음.

---

## 3. 홈화면

### 3.1 홈화면 데이터 조회

#### `GET /home`

홈화면에 필요한 모든 정보를 단일 API로 반환한다.

**응답**
```json
{
  "user": {
    "nickname": "귀여운너굴",
    "raccoon_status": "CAUTION"
  },
  "budget": {
    "year_month": "2026-04",
    "budget_amount": 300000,
    "spent_amount": 185000,
    "usage_rate": 61.7
  },
  "pending_wishes_count": 2
}
```

**처리 로직**
- `usage_rate = spent_amount / budget_amount * 100` (소수점 1자리)
- `pending_wishes_count`: `status = PENDING`이고 생성 후 24시간이 지난 wish 수 (구매 결정 대기 중인 항목)
- 너굴이 상태는 `usage_rate` 기준으로 실시간 계산 후 반환

---

## 4. 상품 저장 (위시)

### 4.1 링크 파싱 (외부 공유 또는 수동 입력 전처리)

#### `POST /wishes/parse-link`

링크를 입력하면 상품 정보를 자동 파싱해 반환. 실제 저장은 하지 않음.

**요청**
```json
{ "url": "https://www.musinsa.com/products/12345" }
```

**파싱 지원 플랫폼**: 무신사, 올리브영, 지그재그, 29CM, 에이블리  
**파싱 항목**: 상품명, 가격, 대표 이미지 URL, 카테고리 추정

**응답 (파싱 성공)**
```json
{
  "title": "오버핏 데님 자켓",
  "price": 89000,
  "image_url": "https://...",
  "product_url": "https://www.musinsa.com/products/12345",
  "category": "FASHION",
  "parse_success": true
}
```

**응답 (파싱 실패 / 미지원 플랫폼)**
```json
{
  "product_url": "https://...",
  "parse_success": false
}
```

**비고**
- 파싱 실패 시 클라이언트는 사용자에게 제목·가격·카테고리 수동 입력 유도
- 카테고리 자동 분류 불가 시 사용자가 직접 선택하도록 `category: null` 반환

---

### 4.2 위시 등록

#### `POST /wishes`

**요청**
```json
{
  "title": "오버핏 데님 자켓",
  "price": 89000,
  "image_url": "https://...",
  "product_url": "https://...",
  "category": "FASHION"
}
```

**유효성 검사**
- `title` 필수, 최대 100자
- `price` 필수, 0 초과 정수
- `category` 필수
- 온보딩 미완료(budget 미설정) 시 `403` 반환

**처리 로직**
1. Wish 레코드 생성 (`status = PENDING`)
2. **24시간 후 구매 숙려 알림 스케줄 등록** (알림 섹션 참고)

**응답 `201 Created`**
```json
{
  "id": "uuid",
  "title": "오버핏 데님 자켓",
  "price": 89000,
  "image_url": "https://...",
  "product_url": "https://...",
  "category": "FASHION",
  "status": "PENDING",
  "created_at": "2026-04-08T14:00:00Z"
}
```

---

### 4.3 위시 목록 조회

#### `GET /wishes`

**쿼리 파라미터**
| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `status` | string | (전체) | PENDING / BOUGHT / RESTRAINED |
| `page` | int | 1 | 페이지 번호 |
| `size` | int | 20 | 페이지 크기 |

**응답**
```json
{
  "wishes": [
    {
      "id": "uuid",
      "title": "오버핏 데님 자켓",
      "price": 89000,
      "image_url": "https://...",
      "category": "FASHION",
      "status": "PENDING",
      "is_deliberation_ready": true,
      "created_at": "2026-04-08T14:00:00Z"
    }
  ],
  "total": 5,
  "page": 1,
  "size": 20
}
```

- `is_deliberation_ready`: 생성 후 24시간 경과 여부 (`true`이면 구매 결정 가능)

---

### 4.4 위시 단건 조회

#### `GET /wishes/{wish_id}`

**응답**: 위시 전체 필드 반환 (동일 구조)

---

### 4.5 위시 삭제 (동면 삭제)

#### `DELETE /wishes/{wish_id}`

**처리 로직**
- `status = PENDING`인 경우에만 삭제 허용
- `BOUGHT` / `RESTRAINED` 상태는 기록 보존을 위해 삭제 불가 (`403`)
- 소프트 딜리트는 사용하지 않음 (실제 삭제, 기록 남기지 않음)

**응답**: `204 No Content`

---

### 4.6 구매 후회 여부 기록

#### `PATCH /wishes/{wish_id}/regret`

`status = BOUGHT`인 위시에 대해 구매 후 후회 여부를 기록한다.  
팝업 알림을 통해 일정 시간 후 수집.

**요청**
```json
{ "regret": true | false }
```

**처리 로직**
- `regret_recorded_at` 기록
- `regret`이 이미 기록된 경우 덮어쓰기 허용

**응답**
```json
{
  "wish_id": "uuid",
  "regret": true,
  "regret_recorded_at": "2026-04-09T10:00:00Z"
}
```

---

## 5. 구매 숙려 화면

구매 결정 화면에 필요한 모든 맥락 정보를 제공하고, 최종 결정을 처리한다.

### 5.1 구매 숙려 맥락 데이터 조회

#### `GET /wishes/{wish_id}/deliberation`

**접근 조건**
- `status = PENDING`이어야 함
- 생성 후 24시간 이후부터 접근 가능 (`is_deliberation_ready: false`이면 `403`)

**응답**
```json
{
  "wish": {
    "id": "uuid",
    "title": "오버핏 데님 자켓",
    "price": 89000,
    "image_url": "https://...",
    "category": "FASHION"
  },
  "budget_context": {
    "year_month": "2026-04",
    "budget_amount": 300000,
    "spent_amount": 185000,
    "usage_rate": 61.7,
    "after_purchase_rate": 91.3,
    "is_over_budget": false,
    "after_purchase_over_budget": true
  },
  "similar_category_history": {
    "category": "FASHION",
    "recent_count": 3,
    "recent_spent": 210000,
    "items": [
      {
        "title": "린넨 와이드 팬츠",
        "price": 72000,
        "bought_at": "2026-04-01"
      }
    ]
  },
  "opportunity_cost": {
    "amount": 89000,
    "examples": [
      { "label": "스타벅스 아메리카노", "count": 22 },
      { "label": "편의점 도시락", "count": 18 }
    ]
  },
  "check_questions": [
    "비슷한 물건이 이미 있나요?",
    "기분이 우울해서 사고 싶은 건 아닌가요?",
    "한 달 뒤에도 여전히 갖고 싶을까요?",
    "지금 안 사면 정말 후회할까요?"
  ]
}
```

**처리 로직**
- `similar_category_history.items`: 최근 3개월 내 같은 카테고리 `BOUGHT` 위시, 최대 3건 노출
- `opportunity_cost.examples`: 서버에서 사전 정의된 고정 항목 (MVP) — 가격÷단가로 count 계산
  - 아메리카노: 4,100원 기준
  - 편의점 도시락: 4,900원 기준
- `check_questions`: 현재는 고정 4개 질문 (MVP), 향후 카테고리별 개인화 가능

---

### 5.2 구매 결정 처리

#### `POST /wishes/{wish_id}/decision`

**요청**
```json
{
  "decision": "BOUGHT" | "RESTRAINED"
}
```

**처리 로직 — RESTRAINED (참았어요)**
1. `wish.status = RESTRAINED`, `wish.decision_at = now()` 업데이트
2. 너굴이 상태 재계산 (소비 없으므로 상태 유지 또는 개선 가능성 없음, 단순 기록)
3. 보상 애니메이션 트리거용 플래그 응답에 포함

**처리 로직 — BOUGHT (샀어요)**
1. `wish.status = BOUGHT`, `wish.decision_at = now()` 업데이트
2. 현재 월의 `MonthlyBudget.spent_amount += wish.price`
3. 너굴이 상태 재계산:
   - `usage_rate` 재산출
   - 임계값(60%, 80%) 기준 상태 갱신
   - DANGER 상태에서 추가 구매 시 DARK로 전환
4. `users.raccoon_status` 업데이트
5. **구매 후회 확인 알림 3일 후 스케줄 등록** (알림 섹션 참고)

**응답**
```json
{
  "decision": "BOUGHT",
  "raccoon_status": "DANGER",
  "raccoon_status_changed": true,
  "spent_amount": 274000,
  "usage_rate": 91.3
}
```

---

## 6. 소셜 피드

익명 방식으로 운영 (MVP). 사용자 닉네임/프로필 미노출.

### 6.1 게시글 작성

#### `POST /social/posts`

**요청**
```json
{
  "title": "이 가방 살까말까",
  "body": "요즘 너무 갖고 싶은데 이미 비슷한 게 있어서 고민됩니다",
  "product_url": "https://...",
  "image_url": "https://...",
  "price": 89000,
  "category": "FASHION",
  "wish_id": "uuid (optional, 위시에서 공유 시)"
}
```

**유효성 검사**
- `title` 필수, 최대 100자
- `body` 선택, 최대 500자
- `image_url` 또는 `product_url` 중 하나 이상 필수
- `category` 필수

**응답 `201 Created`**
```json
{
  "id": "uuid",
  "title": "이 가방 살까말까",
  "body": "...",
  "image_url": "https://...",
  "price": 89000,
  "category": "FASHION",
  "go_count": 0,
  "stop_count": 0,
  "comment_count": 0,
  "my_vote": null,
  "created_at": "2026-04-08T14:00:00Z"
}
```

---

### 6.2 피드 목록 조회

#### `GET /social/posts`

**쿼리 파라미터**
| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `category` | string | (전체) | 카테고리 필터 |
| `cursor` | string | null | 커서 기반 페이지네이션 |
| `size` | int | 20 | 페이지 크기 |

**응답**
```json
{
  "posts": [
    {
      "id": "uuid",
      "title": "이 가방 살까말까",
      "body": "...",
      "image_url": "https://...",
      "price": 89000,
      "category": "FASHION",
      "go_count": 12,
      "stop_count": 8,
      "comment_count": 5,
      "my_vote": "GO" | "STOP" | null,
      "created_at": "2026-04-08T14:00:00Z"
    }
  ],
  "next_cursor": "uuid",
  "has_more": true
}
```

- 정렬: 최신순 (created_at DESC)
- `my_vote`: 로그인 유저의 투표 여부

---

### 6.3 게시글 단건 조회

#### `GET /social/posts/{post_id}`

**응답**: 피드 목록의 단건 항목과 동일한 구조

---

### 6.4 게시글 삭제

#### `DELETE /social/posts/{post_id}`

- 본인 게시글만 삭제 가능 (`403` 반환)
- 하드 딜리트 (댓글, 투표도 함께 삭제)

**응답**: `204 No Content`

---

### 6.5 투표

#### `POST /social/posts/{post_id}/votes`

**요청**
```json
{ "vote_type": "GO" | "STOP" }
```

**처리 로직**
1. 기존 투표 조회
   - 없으면 새로 생성, `go_count` 또는 `stop_count` +1
   - 같은 타입이면 투표 취소(삭제), 카운트 -1
   - 다른 타입이면 투표 변경, 이전 카운트 -1 / 새 카운트 +1
2. `social_posts.go_count`, `stop_count` 업데이트
3. **게시글 작성자에게 투표 결과 알림 발송** (알림 섹션 참고)

**응답**
```json
{
  "my_vote": "GO" | null,
  "go_count": 13,
  "stop_count": 8
}
```

---

### 6.6 댓글 작성

#### `POST /social/posts/{post_id}/comments`

**요청**
```json
{ "body": "저도 비슷한 거 사고 후회했어요 ㅠ STOP 추천합니다" }
```

**유효성 검사**
- `body` 필수, 최대 300자

**응답 `201 Created`**
```json
{
  "id": "uuid",
  "body": "저도 비슷한 거 사고 후회했어요 ㅠ STOP 추천합니다",
  "created_at": "2026-04-08T14:30:00Z"
}
```

---

### 6.7 댓글 목록 조회

#### `GET /social/posts/{post_id}/comments`

**쿼리 파라미터**: `page`, `size` (기본 20)

**응답**
```json
{
  "comments": [
    {
      "id": "uuid",
      "body": "저도 비슷한 거 사고 후회했어요 ㅠ STOP 추천합니다",
      "is_mine": true,
      "created_at": "2026-04-08T14:30:00Z"
    }
  ],
  "total": 5
}
```

- 익명 운영이므로 닉네임 미노출
- `is_mine`: 본인 댓글 여부 (삭제 버튼 표시용)

---

### 6.8 댓글 삭제

#### `DELETE /social/posts/{post_id}/comments/{comment_id}`

- 본인 댓글만 삭제 가능
- 하드 딜리트

**응답**: `204 No Content`

---

## 7. 마이페이지

### 7.1 내 정보 조회

#### `GET /users/me`

**응답**
```json
{
  "nickname": "귀여운너굴",
  "social_provider": "KAKAO",
  "raccoon_status": "CAUTION",
  "monthly_budget": 300000,
  "impulse_frequency": "OFTEN",
  "created_at": "2026-03-01T00:00:00Z"
}
```

---

### 7.2 소비 통계 조회

#### `GET /users/me/stats`

**쿼리 파라미터**: `year_month` (기본: 현재 월, 형식 `2026-04`)

**응답**
```json
{
  "year_month": "2026-04",
  "budget_amount": 300000,
  "spent_amount": 185000,
  "restrained_amount": 158000,
  "usage_rate": 61.7,
  "bought_count": 3,
  "restrained_count": 4,
  "by_category": [
    { "category": "FASHION", "spent": 130000, "count": 2 },
    { "category": "BEAUTY", "spent": 55000, "count": 1 }
  ]
}
```

- `restrained_amount`: 참았어요 처리된 위시들의 price 합산 (절약 금액)

---

### 7.3 소비 기록 목록 조회

#### `GET /users/me/wishes/history`

**쿼리 파라미터**
| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `status` | (전체) | BOUGHT / RESTRAINED |
| `year_month` | 현재 월 | |
| `page` | 1 | |
| `size` | 20 | |

**응답**: `/wishes` 목록과 동일 구조

---

### 7.4 내 게시글 목록 조회

#### `GET /users/me/posts`

**쿼리 파라미터**: `page`, `size`

**응답**: `/social/posts` 목록과 동일 구조

---

### 7.5 계정 탈퇴

#### `DELETE /users/me`

**처리 로직**
1. JWT 검증
2. User 레코드 및 연관 데이터 처리:
   - SocialPost, Comment, Vote: 하드 딜리트
   - Wish, MonthlyBudget, Notification: 하드 딜리트
   - User 레코드 삭제
3. 소셜 플랫폼 연동 해제는 별도 정책에 따름 (MVP에서는 앱 내 데이터만 삭제)

**응답**: `204 No Content`

---

## 8. 알림

FCM(Firebase Cloud Messaging) 기반 푸시 알림 사용.

### 8.1 FCM 토큰 등록 및 갱신

#### `PUT /users/me/fcm-token`

**요청**
```json
{ "fcm_token": "FCM 토큰 문자열" }
```

**응답**: `200 OK`

---

### 8.2 알림 타입 및 발송 시점

| 타입 | 발송 조건 | 발송 시점 | 내용 |
|------|-----------|-----------|------|
| `DELIBERATION_REMIND` | 위시 등록 후 24시간 미결정 | 등록 24시간 후 | "아직 고민 중이에요! 지금도 갖고 싶은지 확인해볼게요" |
| `REGRET_CHECK` | BOUGHT 결정 후 3일 경과 | 결정 72시간 후 | "그 상품, 사고 나서 어때요? 후회하지는 않으셨나요?" |
| `VOTE_RESULT` | 소셜 피드 투표 발생 시 | 투표 즉시 | "누군가 내 위시에 GO / STOP 을 눌렀어요!" (게시글 작성자에게) |
| `RACCOON_STATUS` | 너굴이 상태 변경 시 | 상태 변경 즉시 | "너굴이가 걱정되고 있어요. 소비를 한 번 확인해볼까요?" |

**알림 스케줄링 방식 (권장)**
- 지연 발송이 필요한 타입(`DELIBERATION_REMIND`, `REGRET_CHECK`)은 큐(Queue) 또는 스케줄러 활용
- 즉시 발송 타입(`VOTE_RESULT`, `RACCOON_STATUS`)은 이벤트 발생 시 FCM 직접 호출

---

### 8.3 알림 목록 조회

#### `GET /notifications`

**쿼리 파라미터**: `page`, `size` (기본 20), `is_read` (true/false 필터)

**응답**
```json
{
  "notifications": [
    {
      "id": "uuid",
      "type": "DELIBERATION_REMIND",
      "title": "아직 고민 중이에요!",
      "body": "오버핏 데님 자켓, 지금도 갖고 싶은지 확인해볼까요?",
      "ref_id": "wish-uuid",
      "ref_type": "WISH",
      "is_read": false,
      "created_at": "2026-04-09T14:00:00Z"
    }
  ],
  "unread_count": 3
}
```

---

### 8.4 알림 읽음 처리

#### `PATCH /notifications/{notification_id}/read`

**응답**: `200 OK`

#### `PATCH /notifications/read-all`

전체 읽음 처리

**응답**: `200 OK`

---

## 9. 공통 규격

### 9.1 인증

- 모든 API (로그인 제외)는 `Authorization: Bearer {JWT}` 헤더 필수
- 인증 실패 시 `401 Unauthorized`

### 9.2 에러 응답 공통 포맷

```json
{
  "code": "WISH_NOT_FOUND",
  "message": "해당 위시를 찾을 수 없습니다.",
  "status": 404
}
```

**주요 에러 코드**

| 코드 | HTTP | 설명 |
|------|------|------|
| `UNAUTHORIZED` | 401 | 인증 토큰 없음 / 만료 |
| `FORBIDDEN` | 403 | 권한 없음 (타인 리소스, 온보딩 미완료 등) |
| `WISH_NOT_FOUND` | 404 | 위시 없음 |
| `POST_NOT_FOUND` | 404 | 게시글 없음 |
| `DELIBERATION_NOT_READY` | 403 | 24시간 미경과로 구매 결정 불가 |
| `ALREADY_DECIDED` | 409 | 이미 결정된 위시 |
| `BUDGET_NOT_SET` | 403 | 예산 미설정 (위시 등록 차단) |
| `INVALID_INPUT` | 400 | 요청 파라미터 유효성 오류 |
| `LINK_PARSE_FAILED` | 422 | 링크 파싱 실패 |

### 9.3 페이지네이션 방식

- 피드(`/social/posts`): 커서 기반 (`cursor`, `size`, `next_cursor`)
- 그 외 목록 API: 오프셋 기반 (`page`, `size`, `total`)

### 9.4 날짜/시간

- 모든 타임스탬프: ISO 8601 UTC 형식 (`2026-04-08T14:00:00Z`)
- 클라이언트 표시 변환은 프론트엔드 담당
- 서버 기준 타임존: UTC, KST 변환 필요 시 `Asia/Seoul` 사용

### 9.5 MVP 제외 / 추후 확장 기능

아래 기능은 스키마 설계 시 고려하되 MVP 구현 범위에서는 제외한다.

| 기능 | 비고 |
|------|------|
| 친구 기능 / 친구 피드 | 소셜 피드 확장 시 추가 |
| 카테고리별 피드 필터 | MVP에서는 전체 피드만 |
| 소셜 피드 물품 묶음 등록 | 단일 상품 등록만 지원 |
| 너굴이 커스터마이징 | 캐릭터 무늬·색상·이름 변경 |
| 절제 챌린지 | 흑화 회복 시스템, 별도 설계 필요 |
| iOS / 애플 로그인 | MVP 이후 추가 |
| 합리적 소비 점수 / 랭킹 | 데이터 누적 후 검토 |
| 기회비용 개인화 | MVP는 고정 항목, 이후 관심 카테고리 기반 |
