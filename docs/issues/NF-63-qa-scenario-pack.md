# NF-63 Dev QA Scenario Pack

`/api/dev/qa/**`는 non-prod profile에서만 사용하는 QA seed API다.
운영 데이터나 실제 사용자 데이터는 대상으로 삼지 않는다.

## 공통 사용법

모든 시나리오는 응답의 `userId`를 이후 조회 API의 `X-User-Id` 헤더로 사용한다.

```http
X-User-Id: {userId}
```

생성된 QA 유저는 `QA너굴` prefix를 가진다.
QA cleanup 허용 여부는 `social_accounts.provider_user_id = DEV_QA:{userId}` marker로 판별한다.
정리는 응답의 `paths.cleanup`으로 수행한다.

```http
DELETE /api/dev/qa/users/{userId}
```

`feed-ready`처럼 peer 유저를 함께 만드는 시나리오는 `paths.peerCleanup`도 같이 호출한다.

## API 목록

### QA 유저만 생성

```http
POST /api/dev/qa/users
```

생성 상태:

- ACTIVE / COMPLETED 유저
- user profile
- notification settings
- mascot profile
- 현재 월 budget cycle

### Basic 상태팩

```http
POST /api/dev/qa/scenarios/basic
```

확인 가능한 화면/API:

- `GET /api/v1/home/summary`
- `GET /api/v1/notifications`
- `GET /api/v1/wishlist/items`
- `GET /api/v1/deliberations/items/{deliberationItemId}`

생성 상태:

- 예산 초과 홈 상태
- 읽지 않은 `BUDGET_WARNING` 알림
- 위시 목록용 `SAVED` 상품
- 숙려 진입용 상품
- 유사 카테고리 소비금액 계산용 GO 결정

### 예산 0원 상태팩

```http
POST /api/dev/qa/scenarios/budget-zero
```

확인 가능한 화면/API:

- `GET /api/v1/home/summary`
- `GET /api/v1/users/me/stats?yearMonth={yearMonth}`

생성 상태:

- 현재 월 예산 0원
- 현재 월 소비금액 0원
- 마이페이지 통계의 예산 0원 조회 상태

### 결정 조합 상태팩

```http
POST /api/dev/qa/scenarios/result-combinations
```

확인 가능한 화면/API:

- `GET /api/v1/my/consumption?month={yearMonth}`

생성 상태:

- GO + RATIONAL
- GO + IRRATIONAL
- STOP + RATIONAL
- STOP + IRRATIONAL

### 마이페이지 소비 상태팩

```http
POST /api/dev/qa/scenarios/mypage-consumption
```

확인 가능한 화면/API:

- `GET /api/v1/users/me/stats/months`
- `GET /api/v1/users/me/stats?yearMonth={yearMonth}`
- `GET /api/v1/users/me/wishes/history?yearMonth={yearMonth}`

생성 상태:

- 결정 조합 상태팩과 같은 GO/STOP + 합리/비합리 4건
- 현재 월 예산 cycle
- 통계/월 목록/위시 히스토리 조회용 소비 데이터

### 회고 알림 준비 상태팩

```http
POST /api/dev/qa/scenarios/regret-notification-ready
```

생성 상태:

- GO 결정
- due 상태의 `REGRET_CHECK_7_DAYS` reminder

수동 worker 실행:

```http
POST /api/dev/qa/reminders/{reminderId}/process
```

이 endpoint는 해당 reminder의 owner가 QA marker를 가진 유저인지 확인한 뒤 그 reminder 1건만 처리한다.

확인 가능한 화면/API:

- `GET /api/v1/notifications?unreadOnly=true`
- `POST /api/v1/reflections`

### 피드 상태팩

```http
POST /api/dev/qa/scenarios/feed-ready
```

확인 가능한 화면/API:

- `GET /api/v1/social/posts`
- `GET /api/v1/social/posts/{peerPostId}`
- `GET /api/v1/social/posts/{peerPostId}/comments`
- `GET /api/v1/users/me/posts`
- `GET /api/v1/users/me/votes`

생성 상태:

- viewer 본인 게시글
- peer 게시글
- viewer가 peer 게시글에 남긴 댓글
- viewer가 peer 게시글에 남긴 GO 투표
- viewer 본인 게시글 투표 시 `403 Forbidden`

정리할 때는 viewer cleanup 후 peer cleanup도 호출한다.

## Cleanup 주의사항

cleanup은 `social_accounts.provider_user_id = DEV_QA:{userId}` marker를 가진 유저만 허용한다.
일반 dev/test 유저나 실제 사용자 ID를 넘기면 `400 Bad Request`가 반환된다.

권장 순서:

1. 시나리오 응답의 `paths.cleanup` 호출
2. peer 유저가 있으면 `paths.peerCleanup` 호출
3. 같은 화면을 다시 확인해야 하면 새 시나리오를 재생성

## Stacked PR

- PR #137: QA 시나리오 골격
- PR #138: 기본 상태팩
- PR #139: 결정/회고/알림 worker 상태팩
- PR #140: 피드/마이페이지 상태팩
- PR #141: 문서 정리

`develop` base인 PR #137은 GitHub Actions가 자동 실행된다.
feature branch를 base로 둔 상위 stacked PR은 현재 workflow trigger 대상이 아니므로 로컬 테스트 결과와 최종 develop retarget 이후 CI를 함께 확인한다.
