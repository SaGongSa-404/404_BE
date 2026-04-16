# OAuth 통합 + 마이페이지 구현 계획

## 현재 상태 분석

### 문제점
현재 코드베이스에는 **두 개의 독립적인 패키지**가 공존하고 있다.

| 패키지 | 역할 | 문제 |
|---|---|---|
| `com.example._04_backend` | 소셜 피드 (게시글/댓글/투표) | `X-User-Id` 헤더 임시 인증, User 엔티티 없음 |
| `com.example.oauthsocialtest` | OAuth2 소셜 로그인 | 메인 앱과 분리, DB에 유저 저장 안 함 |

세부 문제:
- `SecurityConfig`가 두 개 → 충돌 위험
- `SocialPost.userId`가 UUID이지만 OAuth 사용자와 연결 없음
- `_04_backend`는 Stateless 세션, `oauthsocialtest`는 세션 기반으로 정책 불일치
- 로그인 후 `User` 엔티티가 DB에 저장되지 않아 마이페이지 구현 불가

---

## 통합 아키텍처

### 인증 방식 결정: 세션 기반 OAuth2

Spring Security OAuth2 Login의 기본 동작(세션 기반)을 그대로 사용한다.
- 로그인 → 세션에 `Authentication` 저장
- API 요청 시 세션 쿠키로 인증
- `@AuthenticationPrincipal LoginUser`로 컨트롤러에서 현재 사용자 접근

> JWT 전환이 필요한 경우 `CustomOAuth2UserService`에서 JWT 발급 + 세션 Stateless 전환으로 확장 가능하도록 설계한다.

### 최종 패키지 구조

```
com.example._04_backend
├── Application.java
├── domain/
│   ├── user/
│   │   ├── entity/User.java               # 신규: OAuth 유저 DB 저장
│   │   ├── repository/UserRepository.java  # 신규
│   │   ├── service/UserService.java        # 신규: 마이페이지 로직
│   │   └── controller/UserController.java  # 신규: 마이페이지 API
│   └── social/                             # 기존 유지 (인증 연동만 수정)
│       └── controller/SocialController.java  # X-User-Id 헤더 → 세션 인증으로 교체
└── global/
    ├── auth/                               # oauthsocialtest에서 이전 + 확장
    │   ├── CustomOAuth2UserService.java    # 이전 + DB upsert 추가
    │   ├── LoginUser.java                  # 신규: 인증된 유저 정보 DTO
    │   └── SocialUserProfile.java          # 이전 (변경 없음)
    ├── config/
    │   ├── SecurityConfig.java             # 통합 (OAuth2 + 세션)
    │   └── WebMvcConfig.java               # 기존 유지
    └── error/                              # 기존 유지 + COMMENT_NOT_FOUND 추가
```

`oauthsocialtest` 패키지 전체 제거.

---

## 구현 단계

### Step 1. User 엔티티 생성

```
users 테이블
├── id: UUID (PK)
├── provider: VARCHAR (google / kakao / naver)
├── provider_user_id: VARCHAR
├── nickname: VARCHAR (null 허용, 소셜 이름으로 초기화)
├── email: VARCHAR (null 허용)
├── profile_image_url: VARCHAR
├── created_at: TIMESTAMP
└── updated_at: TIMESTAMP

UNIQUE(provider, provider_user_id)
```

### Step 2. global/auth 패키지 구성

**`LoginUser`** — 인증된 유저의 최소 정보를 담는 DTO  
컨트롤러에서 `@AuthenticationPrincipal LoginUser loginUser`로 사용

**`CustomOAuth2UserService`** — OAuth 로그인 성공 시:
1. `SocialUserProfile`로 프로바이더 정보 파싱 (기존 로직 재사용)
2. `(provider, providerUserId)` 조합으로 DB 조회
3. 없으면 `User` 생성(INSERT), 있으면 `nickname` / `profileImageUrl` 업데이트
4. `LoginUser`를 담은 `OAuth2User` 반환

### Step 3. SecurityConfig 통합

- `oauthsocialtest.config.SecurityConfig` 제거
- `global.config.SecurityConfig`에 OAuth2 Login 설정 병합
- 세션 정책: `IF_REQUIRED` (기본값 — OAuth 로그인에 필요)
- 보호 경로:
  - `POST /social/posts`, `POST /social/posts/*/votes`, `POST /social/posts/*/comments` → `authenticated()`
  - `DELETE /social/posts/*`, `DELETE /social/posts/*/comments/*` → `authenticated()`
  - `GET /social/**` → `permitAll()` (피드 조회는 비로그인도 가능)
  - `GET /api/users/me/**` → `authenticated()`

### Step 4. SocialController 인증 연동

- `X-User-Id` 헤더 제거
- `@AuthenticationPrincipal LoginUser loginUser` 파라미터로 교체
- `loginUser.getId()`로 userId 추출
- 비로그인 조회(GET) 시: `myVote`는 `null` 반환

### Step 5. 마이페이지 API 구현

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/users/me` | 내 프로필 조회 | 필요 |
| PATCH | `/api/users/me` | 닉네임 수정 | 필요 |
| GET | `/api/users/me/posts` | 내가 작성한 게시글 (커서 페이지네이션) | 필요 |
| GET | `/api/users/me/votes` | 내가 투표한 게시글 | 필요 |

**`GET /api/users/me` 응답:**
```json
{
  "id": "UUID",
  "nickname": "홍길동",
  "email": "user@gmail.com",
  "profileImageUrl": "https://...",
  "provider": "google",
  "postCount": 5
}
```

**`PATCH /api/users/me` 요청:**
```json
{ "nickname": "새닉네임" }
```

**`GET /api/users/me/posts` 응답:**
```json
{
  "posts": [...PostResponse],
  "nextCursor": "UUID | null",
  "hasMore": true
}
```

**`GET /api/users/me/votes` 응답:**
```json
{
  "posts": [...PostResponse with myVote],
  "nextCursor": "UUID | null",
  "hasMore": true
}
```

---

## 에러 코드 추가

| 코드 | HTTP | 설명 |
|---|---|---|
| `USER_NOT_FOUND` | 404 | 유저를 찾을 수 없음 |
| `COMMENT_NOT_FOUND` | 404 | 댓글을 찾을 수 없음 (기존 POST_NOT_FOUND 재사용 수정) |

---

## 변경 파일 목록

### 신규 생성
- `domain/user/entity/User.java`
- `domain/user/repository/UserRepository.java`
- `domain/user/service/UserService.java`
- `domain/user/controller/UserController.java`
- `domain/user/dto/response/MyProfileResponse.java`
- `domain/user/dto/request/UpdateNicknameRequest.java`
- `global/auth/LoginUser.java`
- `global/auth/CustomOAuth2UserService.java` (이전 + 확장)
- `global/auth/SocialUserProfile.java` (이전)

### 수정
- `global/config/SecurityConfig.java` — OAuth2 통합
- `domain/social/controller/SocialController.java` — 인증 연동
- `domain/social/service/SocialPostService.java` — 비로그인 조회 대응
- `domain/social/repository/VoteRepository.java` — `findByUserId` 추가
- `global/error/ErrorCode.java` — 에러 코드 추가

### 삭제
- `com.example.oauthsocialtest` 패키지 전체
