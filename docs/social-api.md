# 소셜 피드 API 명세서

> 작성일: 2026-04-08  
> 최종 수정: 2026-04-08  
> 기준 문서: wigul_backend_spec.md 섹션 6  
> 브랜치: feature/social

---

## 개요

익명 기반 소셜 피드 기능으로, 사용자가 구매 고민 상품을 공유하고 다른 사용자로부터 GO/STOP 투표 및 댓글을 받을 수 있다.

- **익명 운영**: MVP에서는 닉네임/프로필 미노출
- **피드 정렬**: 최신순 (created_at DESC)
- **페이지네이션**: 커서 기반 (cursor, size, next_cursor)

---

## 현재 진행 상황

### 완료

| 항목 | 설명 |
|------|------|
| 엔티티 | SocialPost, Vote, Comment + BaseEntity, Category enum |
| Repository | SocialPostRepository (커서 기반 쿼리), VoteRepository, CommentRepository |
| DTO | 요청 3종 (CreatePostRequest, VoteRequest, CreateCommentRequest), 응답 5종 (PostResponse, PostListResponse, VoteResponse, CommentResponse, CommentListResponse) |
| Service | SocialPostService, VoteService, CommentService |
| Controller | SocialController - 7개 엔드포인트 구현 완료 |
| 에러 처리 | ErrorCode enum, BusinessException, GlobalExceptionHandler |
| Security | SecurityConfig - STATELESS 세션, /social/** permitAll (JWT 미적용 상태) |
| 테스트 페이지 | `static/index.html` - 슬라이드 카드 UI, 다중 유저 테스트 지원 |
| 초기 데이터 | DataInitializer - 서버 시작 시 샘플 게시글 4건 자동 생성 |

### 미완료 (소셜 로그인 관련)

| 항목 | 설명 |
|------|------|
| User 엔티티 | social_provider, social_id, nickname, monthly_budget 등 |
| JWT 인증 | jjwt 라이브러리, JwtTokenProvider, JwtAuthenticationFilter |
| 소셜 로그인 | 카카오/구글 access_token 검증 → JWT 발급 |
| Auth API | POST /auth/social/login, POST /auth/refresh, POST /auth/logout |
| SecurityConfig | JWT 필터 등록, /social/** authenticated()로 전환 |

---

## 엔티티

### SocialPost (소셜 피드 게시글)

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | 게시글 고유 ID |
| user_id | UUID | FK -> User | 작성자 |
| wish_id | UUID | FK -> Wish, nullable | 위시에서 공유한 경우 |
| product_url | String | nullable | 상품 링크 |
| image_url | String | nullable | 상품 이미지 |
| title | String | NOT NULL, max 100 | 게시글 제목 |
| body | String | nullable, max 500 | 게시글 본문 |
| price | Integer | nullable | 상품 가격 |
| category | Enum | NOT NULL | FASHION, BEAUTY, ELECTRONICS, FOOD, HOBBY, ETC |
| go_count | Integer | default 0 | GO 투표 수 |
| stop_count | Integer | default 0 | STOP 투표 수 |
| created_at | Timestamp | NOT NULL | 생성 시각 |
| updated_at | Timestamp | NOT NULL | 수정 시각 |

### Vote (소셜 투표)

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | 투표 고유 ID |
| post_id | UUID | FK -> SocialPost | 대상 게시글 |
| user_id | UUID | FK -> User | 투표자 |
| vote_type | Enum | NOT NULL | GO, STOP |
| created_at | Timestamp | NOT NULL | 생성 시각 |

**유니크 제약**: (post_id, user_id) - 게시글당 1인 1투표

### Comment (소셜 댓글)

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | 댓글 고유 ID |
| post_id | UUID | FK -> SocialPost | 대상 게시글 |
| user_id | UUID | FK -> User | 작성자 |
| body | String | NOT NULL, max 300 | 댓글 내용 |
| created_at | Timestamp | NOT NULL | 생성 시각 |
| updated_at | Timestamp | NOT NULL | 수정 시각 |

---

## API 엔드포인트

> 현재 JWT 미적용 상태. 테스트 시 `X-User-Id` 헤더로 사용자 전환 가능.

### 1. 게시글 작성

```
POST /social/posts
X-User-Id: {uuid} (테스트용, 추후 JWT로 대체)
```

**Request Body**
```json
{
  "title": "이 가방 살까말까",
  "body": "요즘 너무 갖고 싶은데 이미 비슷한 게 있어서 고민됩니다",
  "productUrl": "https://...",
  "imageUrl": "https://...",
  "price": 89000,
  "category": "FASHION",
  "wishId": "uuid (optional)"
}
```

**유효성 검사**
- `title`: 필수, 최대 100자
- `body`: 선택, 최대 500자
- `imageUrl` 또는 `productUrl` 중 하나 이상 필수
- `category`: 필수

**Response `201 Created`**
```json
{
  "id": "uuid",
  "title": "이 가방 살까말까",
  "body": "...",
  "imageUrl": "https://...",
  "price": 89000,
  "category": "FASHION",
  "goCount": 0,
  "stopCount": 0,
  "commentCount": 0,
  "myVote": null,
  "createdAt": "2026-04-08T14:00:00"
}
```

---

### 2. 피드 목록 조회

```
GET /social/posts
X-User-Id: {uuid}
```

**Query Parameters**

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| category | String | 전체 | 카테고리 필터 |
| cursor | UUID | null | 커서 기반 페이지네이션 |
| size | int | 20 | 페이지 크기 |

**Response `200 OK`**
```json
{
  "posts": [
    {
      "id": "uuid",
      "title": "이 가방 살까말까",
      "body": "...",
      "imageUrl": "https://...",
      "price": 89000,
      "category": "FASHION",
      "goCount": 12,
      "stopCount": 8,
      "commentCount": 5,
      "myVote": "GO",
      "createdAt": "2026-04-08T14:00:00"
    }
  ],
  "nextCursor": "uuid",
  "hasMore": true
}
```

- 정렬: 최신순 (created_at DESC)
- `myVote`: 현재 유저의 투표 여부 (GO | STOP | null)

---

### 3. 게시글 단건 조회

```
GET /social/posts/{postId}
X-User-Id: {uuid}
```

**Response `200 OK`**: 피드 목록의 단건 항목과 동일 구조

---

### 4. 게시글 삭제

```
DELETE /social/posts/{postId}
X-User-Id: {uuid}
```

- 본인 게시글만 삭제 가능 (타인 게시글 시 `403`)
- 하드 딜리트 (댓글, 투표도 함께 삭제 - CASCADE)

**Response `204 No Content`**

---

### 5. 투표 (GO / STOP)

```
POST /social/posts/{postId}/votes
X-User-Id: {uuid}
```

**Request Body**
```json
{
  "voteType": "GO"
}
```

**처리 로직**
1. 기존 투표 없음 → 새로 생성, 해당 카운트 +1
2. 같은 타입 재투표 → 투표 취소(삭제), 카운트 -1
3. 다른 타입 투표 → 이전 카운트 -1, 새 카운트 +1

**Response `200 OK`**
```json
{
  "myVote": "GO",
  "goCount": 13,
  "stopCount": 8
}
```

---

### 6. 댓글 작성

```
POST /social/posts/{postId}/comments
X-User-Id: {uuid}
```

**Request Body**
```json
{
  "body": "저도 비슷한 거 사고 후회했어요 ㅠ STOP 추천합니다"
}
```

**유효성 검사**: `body` 필수, 최대 300자

**Response `201 Created`**
```json
{
  "id": "uuid",
  "body": "저도 비슷한 거 사고 후회했어요 ㅠ STOP 추천합니다",
  "mine": true,
  "createdAt": "2026-04-08T14:30:00"
}
```

---

### 7. 댓글 목록 조회

```
GET /social/posts/{postId}/comments
X-User-Id: {uuid}
```

**Query Parameters**: `page` (기본 1), `size` (기본 20)

**Response `200 OK`**
```json
{
  "comments": [
    {
      "id": "uuid",
      "body": "저도 비슷한 거 사고 후회했어요 ㅠ STOP 추천합니다",
      "mine": true,
      "createdAt": "2026-04-08T14:30:00"
    }
  ],
  "total": 5
}
```

- 익명 운영이므로 닉네임 미노출
- `mine`: 본인 댓글 여부 (삭제 버튼 표시용)

---

### 8. 댓글 삭제

```
DELETE /social/posts/{postId}/comments/{commentId}
X-User-Id: {uuid}
```

- 본인 댓글만 삭제 가능 (타인 댓글 시 `403`)
- 하드 딜리트

**Response `204 No Content`**

---

## 에러 코드

| 코드 | HTTP | 설명 |
|------|------|------|
| UNAUTHORIZED | 401 | 인증 토큰 없음/만료 |
| FORBIDDEN | 403 | 권한 없음 (타인 리소스 접근) |
| POST_NOT_FOUND | 404 | 게시글 없음 |
| INVALID_INPUT | 400 | 요청 파라미터 유효성 오류 |

---

## 테스트 방법

### 테스트 페이지

서버 실행 후 `http://localhost:8081` 접속

**기능**
- 슬라이드 카드 UI (스와이프/휠/키보드 화살표로 탐색)
- 상단 User1/User2/User3 전환 버튼으로 다중 유저 테스트
- GO/STOP 투표, 댓글 작성/삭제, 게시글 작성/삭제
- 내 댓글에만 삭제 버튼 표시

**테스트 유저 ID**
| 이름 | UUID |
|------|------|
| User1 | `00000000-0000-0000-0000-000000000001` |
| User2 | `00000000-0000-0000-0000-000000000002` |
| User3 | `00000000-0000-0000-0000-000000000003` |

### 초기 데이터

서버 시작 시 `DataInitializer`가 샘플 게시글 4건을 자동 생성:
1. 나이키 에어맥스 (FASHION, 189,000원)
2. 애플워치 울트라2 (ELECTRONICS, 999,000원)
3. 편의점 디저트 (FOOD, 3,500원)
4. 레고 스타워즈 (HOBBY, 320,000원)

---

## 패키지 구조

```
com.example._04_backend
├── global
│   ├── common
│   │   ├── BaseEntity.java
│   │   └── enums/
│   │       └── Category.java
│   ├── config
│   │   ├── SecurityConfig.java
│   │   ├── JpaAuditingConfig.java
│   │   └── DataInitializer.java
│   └── error
│       ├── ErrorCode.java
│       ├── ErrorResponse.java
│       ├── BusinessException.java
│       └── GlobalExceptionHandler.java
└── domain
    └── social
        ├── entity
        │   ├── SocialPost.java
        │   ├── Vote.java
        │   └── Comment.java
        ├── enums
        │   └── VoteType.java
        ├── repository
        │   ├── SocialPostRepository.java
        │   ├── VoteRepository.java
        │   └── CommentRepository.java
        ├── dto
        │   ├── request
        │   │   ├── CreatePostRequest.java
        │   │   ├── VoteRequest.java
        │   │   └── CreateCommentRequest.java
        │   └── response
        │       ├── PostResponse.java
        │       ├── PostListResponse.java
        │       ├── VoteResponse.java
        │       ├── CommentResponse.java
        │       └── CommentListResponse.java
        ├── service
        │   ├── SocialPostService.java
        │   ├── VoteService.java
        │   └── CommentService.java
        └── controller
            └── SocialController.java
```
