# Mobile API v1 Endpoint Catalog

## 목적

이 문서는 모바일 앱 기준의 HTTP 엔드포인트 목록 초안이다.

- 기준 클라이언트: iOS, Android
- base path: `/api/v1`
- 인증 방식: `Authorization: Bearer <access_token>`
- 응답 규칙: facade는 화면용 응답을 조합하고, domain API는 단일 모듈 책임만 가진다.

## Screen To Facade Mapping

| Screen | Primary Facade | Domain APIs |
| --- | --- | --- |
| `ONB_001` | `onboardingFacade` | `auth`, `user` |
| `ONB_002`, `ONB_005`, `ONB_006`, `ONB_007`, `ONB_008`, `ONB_009` | `onboardingFacade` | `house` |
| `ONB_004`, `SPC_001`, `SPC_002`, `SPC_003` | `onboardingFacade`, `space` | `space`, `house` |
| `SCH_001`, `SCH_002` | `homeFacade` | `chore` |
| `SCH_003` | `homeFacade` | `adjustment`, `chore` |
| `ALM_001`, `ALM_002` | `notificationFacade` | `notification`, `adjustment` |
| `SET_001`, `SET_003` | `settingsFacade` | `user`, `house` |
| `SET_004`, `SET_005`, `SET_006` | `settingsFacade` | `review` |

## Facade APIs

### onboardingFacade

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/facades/onboarding/bootstrap` | 로그인 직후 신규/기존 회원, 현재 집 존재 여부, 다음 화면 결정 |
| `GET` | `/facades/onboarding/house-setup` | 집 정보, 초대코드, 청결 기준 투표 여부, 공간 요약 조회 |

#### `GET /facades/onboarding/bootstrap`

- 사용 화면: `ONB_001`, `ONB_002`
- 반환 핵심
  - `isNewUser`
  - `hasActiveHouse`
  - `activeHouseId`
  - `requiredTermsPending`
  - `nextStep`

#### `GET /facades/onboarding/house-setup`

- 사용 화면: `ONB_004` ~ `ONB_009`
- 반환 핵심
  - `house`
  - `inviteCode`
  - `cleanlinessVote`
  - `spaces`
  - `memberCount`

### homeFacade

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/facades/home` | 홈 화면 진입 시 오늘 할 일, 진행률, 대타 요청 진입 정보 조합 |

#### `GET /facades/home`

- 사용 화면: `SCH_001`, `SCH_002`, `SCH_003`
- query
  - `date`: optional, 기본 오늘
- 반환 핵심
  - `house`
  - `todayChores`
  - `dailyProgress`
  - `openAdjustmentRequests`
  - `myPendingActions`

### notificationFacade

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/facades/notifications` | 알림 피드와 타입별 payload 조합 |

#### `GET /facades/notifications`

- 사용 화면: `ALM_001`, `ALM_002`
- query
  - `cursor`
  - `limit`
- 반환 핵심
  - `items`
  - `unreadCount`
  - `nextCursor`

### settingsFacade

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/facades/settings` | 내 정보, 집 정보, 최신 주간 회고 상태 조합 |

#### `GET /facades/settings`

- 사용 화면: `SET_001`, `SET_003`, `SET_004`, `SET_005`, `SET_006`
- 반환 핵심
  - `profile`
  - `houseSummary`
  - `latestWeeklyRecap`
  - `weeklySatisfactionStatus`

## Domain APIs

### auth

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/auth/kakao/login` | 카카오 인가 코드로 로그인/회원가입 |
| `POST` | `/auth/terms/accept` | 필수 약관 동의 |
| `POST` | `/auth/session/refresh` | access token 재발급 |
| `POST` | `/auth/logout` | 현재 세션 종료 |
| `GET` | `/auth/me` | 인증 사용자 요약 |

#### `POST /auth/kakao/login`

request body

```json
{
  "authorizationCode": "string",
  "redirectUri": "string",
  "deviceId": "string",
  "deviceName": "iPhone 16"
}
```

response highlights

```json
{
  "userId": "uuid",
  "isNewUser": true,
  "accessToken": "jwt",
  "refreshToken": "opaque",
  "requiredTerms": ["SERVICE", "PRIVACY"]
}
```

### user

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/users/me` | 내 프로필 조회 |
| `PATCH` | `/users/me` | 닉네임/이미지 수정 |
| `DELETE` | `/users/me` | 회원 탈퇴 |

### house

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/houses` | 집 생성 |
| `POST` | `/houses/join` | 초대코드로 집 참여 |
| `GET` | `/houses/me` | 현재 참여 중인 집 조회 |
| `POST` | `/houses/{houseId}/leave` | 집 나가기 |
| `POST` | `/houses/{houseId}/owner/transfer` | 방장 위임 |
| `POST` | `/houses/{houseId}/cleanliness-votes` | 청결 기준 투표 |
| `GET` | `/houses/{houseId}/cleanliness-summary` | 청결 기준 집계 결과 |
| `POST` | `/houses/{houseId}/invite-codes` | 초대코드 생성 또는 재발급 |

#### `POST /houses`

request body

```json
{
  "name": "404 하우스",
  "initialCleanlinessVote": "BALANCED"
}
```

response highlights

```json
{
  "houseId": "uuid",
  "membershipId": "uuid",
  "inviteCode": {
    "code": "ABCD12",
    "expiresAt": null
  }
}
```

### space

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/houses/{houseId}/spaces` | 공간 추가 |
| `GET` | `/houses/{houseId}/spaces` | 공간 목록 조회 |
| `PATCH` | `/spaces/{spaceId}` | 공간명 수정 |
| `GET` | `/houses/{houseId}/spaces/chore-counts` | 공간별 집안일 개수 조회 |

### chore

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/houses/{houseId}/chores` | 집안일 생성 |
| `PATCH` | `/chores/{choreRuleId}` | 집안일 규칙 수정 |
| `DELETE` | `/chores/{choreRuleId}` | 집안일 삭제 |
| `GET` | `/houses/{houseId}/chores/today` | 오늘 할 일 목록 |
| `GET` | `/houses/{houseId}/chores/daily-progress` | 오늘 진행률 |
| `POST` | `/chore-instances/{choreInstanceId}/completion` | 완료 처리 |
| `DELETE` | `/chore-instances/{choreInstanceId}/completion` | 완료 취소 |

#### `POST /houses/{houseId}/chores`

request body

```json
{
  "spaceId": "uuid",
  "title": "분리수거",
  "description": "저녁 8시 전에 배출",
  "estimatedMinutes": 10,
  "defaultAssigneeMembershipId": "uuid",
  "recurrence": {
    "frequency": "WEEKLY",
    "interval": 1,
    "daysOfWeek": ["THU"]
  }
}
```

#### `POST /chore-instances/{choreInstanceId}/completion`

request body

```json
{
  "memo": "완료했어요",
  "proofImageUrl": null
}
```

### adjustment

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/houses/{houseId}/adjustments/substitute-requests` | 대타 요청 생성 |
| `POST` | `/houses/{houseId}/adjustments/reschedule-requests` | 날짜 조정 요청 생성 |
| `POST` | `/adjustments/{adjustmentRequestId}/accept` | 요청 수락 |
| `POST` | `/adjustments/{adjustmentRequestId}/reject` | 요청 거절 |
| `POST` | `/adjustments/{adjustmentRequestId}/cancel` | 요청 취소 |
| `GET` | `/houses/{houseId}/adjustments/open` | 열려 있는 요청 조회 |

#### `POST /houses/{houseId}/adjustments/substitute-requests`

request body

```json
{
  "choreInstanceId": "uuid",
  "reason": "야근 예정",
  "expiresAt": "2026-03-18T18:00:00+09:00"
}
```

### notification

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/houses/{houseId}/notifications` | 우리 집 알림 피드 |
| `POST` | `/notifications/{notificationId}/read` | 단건 읽음 처리 |
| `POST` | `/notifications/read-batch` | 스크롤 기반 일괄 읽음 |
| `POST` | `/push-devices` | 푸시 디바이스 등록 |
| `DELETE` | `/push-devices/{deviceId}` | 푸시 디바이스 해제 |

#### `POST /notifications/read-batch`

request body

```json
{
  "notificationIds": ["uuid", "uuid"]
}
```

### review

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/houses/{houseId}/weekly-recaps/latest` | 최신 주간 회고 |
| `GET` | `/houses/{houseId}/weekly-recaps` | 지난 회고 목록 |
| `POST` | `/houses/{houseId}/weekly-recaps/{snapshotId}/satisfaction` | 만족도 제출 |

#### `GET /houses/{houseId}/weekly-recaps/latest`

response highlights

```json
{
  "snapshotId": "uuid",
  "weekStartDate": "2026-03-09",
  "weekEndDate": "2026-03-15",
  "houseStats": {
    "totalChores": 18,
    "completedChores": 15,
    "completionRate": 83.33
  },
  "memberStats": [],
  "satisfactionSubmitted": false
}
```

## 공통 에러 코드

| Code | Meaning |
| --- | --- |
| `AUTH_UNAUTHORIZED` | 유효하지 않은 토큰 또는 세션 |
| `AUTH_TERMS_REQUIRED` | 필수 약관 동의 필요 |
| `HOUSE_NOT_FOUND` | 접근 가능한 집 없음 |
| `HOUSE_INVITE_INVALID` | 초대코드가 없거나 만료 |
| `HOUSE_PERMISSION_DENIED` | 방장 또는 구성원 권한 부족 |
| `SPACE_NOT_FOUND` | 공간 없음 |
| `CHORE_NOT_FOUND` | 집안일 규칙 또는 인스턴스 없음 |
| `CHORE_ALREADY_COMPLETED` | 이미 완료된 집안일 |
| `ADJUSTMENT_NOT_OPEN` | 처리 불가능한 요청 상태 |
| `NOTIFICATION_NOT_FOUND` | 알림 없음 |
| `REVIEW_NOT_READY` | 주간 스냅샷 미생성 |

## Worker-Driven Internal Flows

외부 HTTP 엔드포인트는 아니지만 구현 시 함께 정의해야 할 내부 이벤트다.

| Event | Producer | Consumer |
| --- | --- | --- |
| `chore.completed` | `chore` | `notification`, `review` |
| `adjustment.requested` | `adjustment` | `notification` |
| `adjustment.accepted` | `adjustment` | `chore`, `notification`, `review` |
| `weekly.snapshot.ready` | `review` worker | `notification` |
| `auth.session.expired` | `auth` worker | `notification` optional |

## 구현 우선순위

1. `auth`, `user`, `house`, `space`
2. `chore`, `GET /facades/home`
3. `adjustment`, `notification`, push device 등록
4. `review`, `GET /facades/settings`
