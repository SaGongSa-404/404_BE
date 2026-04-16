# 마이페이지 구현 계획

> 작성일: 2026-04-16  
> 브랜치: feature/social-clean

---

## 1. 현재 구현 현황

### 기존 User 도메인
| 항목 | 상태 |
|------|------|
| `GET /api/users/me` | ✅ 구현됨 (일부 필드 누락) |
| `PATCH /api/users/me` (닉네임) | ✅ 구현됨 |
| `GET /api/users/me/posts` | ✅ 구현됨 |
| `GET /api/users/me/votes` | ✅ 구현됨 |
| 계정 탈퇴 | ❌ 미구현 |
| 소비 통계 / 기록 | ❌ 미구현 |
| 알림 설정 | ❌ 미구현 |
| 약관 | ❌ 미구현 |

### User 엔티티 현재 필드
- id, provider, providerUserId, nickname, email, profileImageUrl, createdAt, updatedAt

### 없는 엔티티
- `Wish` (소비 기록 관리에 필요)
- `MonthlyBudget` (소비 통계에 필요)

---

## 2. 구현 대상 API

### 2.1 내 정보 섹션

| Method | 경로 | 설명 | 상태 |
|--------|------|------|------|
| GET | `/api/users/me` | 내 정보 조회 (스펙 확장) | 수정 |
| PATCH | `/api/users/me/profile` | 닉네임 · 너구리 이름 수정 | 신규 |
| PATCH | `/api/users/me/budget` | 월 예산 설정 | 신규 |
| PATCH | `/api/users/me/notification-settings` | 알림 수신 여부 설정 | 신규 |
| DELETE | `/api/users/me` | 계정 탈퇴 | 신규 |
| POST | `/api/logout` (Spring Security) | 로그아웃 | 기존 활용 |

#### `GET /api/users/me` 응답 (확장)
```json
{
  "id": "uuid",
  "nickname": "귀여운너굴",
  "raccoonName": "너굴이",
  "email": "user@example.com",
  "profileImageUrl": "https://...",
  "provider": "kakao",
  "raccoonStatus": "NORMAL",
  "monthlyBudget": 300000,
  "impulseFrequency": "OFTEN",
  "notificationEnabled": true,
  "postCount": 5,
  "createdAt": "2026-03-01T00:00:00Z"
}
```

#### `PATCH /api/users/me/profile` 요청/응답
```json
// 요청
{ "nickname": "귀여운너굴", "raccoonName": "너굴이" }

// 응답 → GET /api/users/me 와 동일 구조
```
- `nickname`: 1~10자, 한글·영문·숫자만 허용, 특수문자 불허
- `raccoonName`: 1~10자, nullable (미전송 시 기존 값 유지)

#### `PATCH /api/users/me/budget` 요청/응답
```json
// 요청
{ "monthlyBudget": 300000 }

// 응답
{ "monthlyBudget": 300000, "raccoonStatus": "NORMAL" }
```
- 0 이하 불가

#### `PATCH /api/users/me/notification-settings` 요청/응답
```json
// 요청
{ "notificationEnabled": true }

// 응답
{ "notificationEnabled": true }
```

#### `DELETE /api/users/me` (계정 탈퇴)
- 연관 데이터 하드 딜리트 순서:  
  Vote → Comment → SocialPost → Wish → MonthlyBudget → User
- 응답: `204 No Content`

---

### 2.2 소비 관리 섹션

| Method | 경로 | 설명 |
|--------|------|------|
| GET | `/api/users/me/stats` | 월별 소비 통계 |
| GET | `/api/users/me/wishes/history` | 소비/절제 기록 목록 |

#### `GET /api/users/me/stats?year_month=2026-04` 응답
```json
{
  "yearMonth": "2026-04",
  "budgetAmount": 300000,
  "spentAmount": 185000,
  "restrainedAmount": 158000,
  "usageRate": 61.7,
  "boughtCount": 3,
  "restrainedCount": 4,
  "byCategory": [
    { "category": "FASHION", "spent": 130000, "count": 2 },
    { "category": "BEAUTY", "spent": 55000, "count": 1 }
  ]
}
```

#### `GET /api/users/me/wishes/history` 파라미터
- `status`: BOUGHT | RESTRAINED (생략 시 전체)
- `year_month`: 기본값 현재 월 (예: `2026-04`)
- `page`: 기본 0
- `size`: 기본 20

```json
{
  "wishes": [
    {
      "id": "uuid",
      "title": "오버핏 데님 자켓",
      "price": 89000,
      "imageUrl": "https://...",
      "category": "FASHION",
      "status": "BOUGHT",
      "decisionAt": "2026-04-10T14:00:00Z"
    }
  ],
  "total": 7,
  "page": 0,
  "size": 20
}
```

---

### 2.3 나의 게시글 섹션

| Method | 경로 | 설명 | 상태 |
|--------|------|------|------|
| GET | `/api/users/me/posts` | 내가 쓴 게시글 목록 | ✅ 기존 |

---

### 2.4 약관 확인 섹션

| Method | 경로 | 설명 |
|--------|------|------|
| GET | `/api/terms` | 약관 목록 조회 |
| GET | `/api/terms/{type}` | 약관 단건 조회 |

- DB 저장 없이 서버 상수로 관리 (MVP)
- `type`: `SERVICE` (서비스 이용약관) | `PRIVACY` (개인정보처리방침) | `MARKETING` (마케팅 수신 동의)

```json
// GET /api/terms
[
  { "type": "SERVICE", "title": "서비스 이용약관", "required": true },
  { "type": "PRIVACY", "title": "개인정보처리방침", "required": true },
  { "type": "MARKETING", "title": "마케팅 수신 동의", "required": false }
]

// GET /api/terms/{type}
{
  "type": "SERVICE",
  "title": "서비스 이용약관",
  "required": true,
  "content": "..."
}
```

---

## 3. 엔티티 변경 사항

### 3.1 User 엔티티 필드 추가

| 필드 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `raccoonStatus` | `RaccoonStatus` (enum) | `NORMAL` | 너굴이 상태 |
| `monthlyBudget` | `Integer` | `null` | 월 예산 (null = 미설정) |
| `raccoonName` | `String` | `null` | 너구리 이름 |
| `impulseFrequency` | `ImpulseFrequency` (enum) | `null` | 충동구매 빈도 (온보딩) |
| `notificationEnabled` | `boolean` | `true` | 알림 수신 여부 |

### 3.2 새 Enum

```
RaccoonStatus: NORMAL | CAUTION | DANGER | DARK
ImpulseFrequency: NEVER | RARELY | SOMETIMES | OFTEN | ALWAYS
WishStatus: PENDING | BOUGHT | RESTRAINED
```

### 3.3 새 엔티티: Wish

```
Wish
├── id (UUID)
├── user (FK → User)
├── title (String, max 100)
├── price (Integer)
├── imageUrl (String, nullable)
├── productUrl (String, nullable)
├── category (Category enum)
├── status (WishStatus, default PENDING)
├── decisionAt (LocalDateTime, nullable)
├── regret (Boolean, nullable)
├── regretRecordedAt (LocalDateTime, nullable)
└── BaseEntity (createdAt, updatedAt)
```

### 3.4 새 엔티티: MonthlyBudget

```
MonthlyBudget
├── id (UUID)
├── user (FK → User)
├── yearMonth (String, "2026-04")
├── budgetAmount (Integer)
├── spentAmount (Integer, default 0)
└── updatedAt
```

---

## 4. 구현 순서

### Step 1. 엔티티·Enum 추가 (기반 작업)
1. `RaccoonStatus`, `ImpulseFrequency`, `WishStatus` enum 생성
2. `User` 엔티티 필드 추가 및 update 메서드 추가
3. `Wish` 엔티티 생성
4. `MonthlyBudget` 엔티티 생성
5. `WishRepository`, `MonthlyBudgetRepository` 생성

### Step 2. 내 정보 API
1. `UpdateProfileRequest` DTO 생성 (닉네임 + 너구리 이름)
2. `UpdateBudgetRequest` DTO 생성
3. `NotificationSettingsRequest` / `NotificationSettingsResponse` DTO 생성
4. `MyProfileResponse` 확장 (raccoonStatus, monthlyBudget 등 추가)
5. `UserService` 메서드 추가:
   - `updateProfile()`
   - `updateBudget()`
   - `updateNotificationSettings()`
   - `deleteAccount()`
6. `UserController` 엔드포인트 추가

### Step 3. 소비 관리 API
1. `WishHistoryResponse`, `StatsResponse`, `CategoryStatResponse` DTO 생성
2. `UserService` 메서드 추가:
   - `getStats()`
   - `getWishHistory()`
3. `UserController` 엔드포인트 추가

### Step 4. 약관 API
1. `TermsType` enum 생성 (SERVICE | PRIVACY | MARKETING)
2. `TermsSummaryResponse`, `TermsDetailResponse` DTO 생성
3. `TermsController` 생성 (static content 반환)

### Step 5. SecurityConfig 업데이트
- `DELETE /api/users/me` 인증 필요 설정
- `GET /api/terms/**` 공개 설정

### Step 6. ErrorCode 추가
- `INVALID_NICKNAME` (400)
- `BUDGET_MUST_BE_POSITIVE` (400)

---

## 5. 너굴이 상태 계산 규칙

```
usageRate = spentAmount / budgetAmount * 100

NORMAL  → usageRate < 60
CAUTION → 60 ≤ usageRate < 80
DANGER  → 80 ≤ usageRate
DARK    → DANGER 상태에서 추가 구매 발생 시
```
- 예산(monthlyBudget) 미설정 시 상태 계산 생략, NORMAL 유지
- 계산 시점: 예산 변경 시, 위시 BOUGHT 결정 시 (현재 스코프에서는 예산 변경 시만)

---

## 6. 파일 목록 (예정 생성/수정)

### 신규 생성
```
domain/user/enums/RaccoonStatus.java
domain/user/enums/ImpulseFrequency.java
domain/wish/entity/Wish.java
domain/wish/enums/WishStatus.java
domain/wish/repository/WishRepository.java
domain/wish/entity/MonthlyBudget.java
domain/wish/repository/MonthlyBudgetRepository.java
domain/user/dto/request/UpdateProfileRequest.java
domain/user/dto/request/UpdateBudgetRequest.java
domain/user/dto/request/NotificationSettingsRequest.java
domain/user/dto/response/NotificationSettingsResponse.java
domain/user/dto/response/BudgetUpdateResponse.java
domain/user/dto/response/StatsResponse.java
domain/user/dto/response/CategoryStatResponse.java
domain/user/dto/response/WishHistoryResponse.java
domain/user/dto/response/WishSummaryResponse.java
domain/terms/controller/TermsController.java
domain/terms/dto/TermsSummaryResponse.java
domain/terms/dto/TermsDetailResponse.java
domain/terms/enums/TermsType.java
```

### 수정
```
domain/user/entity/User.java         ← 필드 추가
domain/user/dto/response/MyProfileResponse.java  ← 필드 확장
domain/user/dto/request/UpdateNicknameRequest.java  ← 유효성 강화
domain/user/service/UserService.java  ← 메서드 추가
domain/user/controller/UserController.java  ← 엔드포인트 추가
global/error/ErrorCode.java           ← 에러 코드 추가
global/config/SecurityConfig.java     ← 경로 권한 추가
```
