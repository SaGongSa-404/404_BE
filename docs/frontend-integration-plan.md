# 프론트엔드 통합 및 엔드포인트 정리 계획

## 현황 및 문제점

### 현재 정적 파일 (static/)

| 파일 | 역할 | 문제 |
|------|------|------|
| `index.html` | 소셜 피드 테스트 | X-User-Id 헤더 방식 (구 인증), 로그인 없이 동작 불가 |
| `test-mypage.html` | 마이페이지 API 테스트 | 로그인 버튼 있으나 독립 페이지 |
| `deliberation.html` | 구매 숙려 테스트 | 로그인 + 위시 생성 + 숙려 전체 흐름 |

### 문제
- `localhost:8080` 접속 시 로그인 없이 소셜 피드만 보임
- 세 파일이 각각 독립적으로 존재 → 통합 UX 없음
- `index.html`이 구 `X-User-Id` 방식을 사용해 투표/게시글 작성 불가

---

## 통합 후 구조

```
localhost:8080  →  /login.html (소셜 로그인 화면)
                     │
                     ├─ 카카오/구글/네이버 로그인
                     │
                     ↓
                /app.html (통합 테스트 앱)
                     │
                     ├─ [탭] 소셜 피드   (GET/POST /social/posts, 투표, 댓글)
                     ├─ [탭] 구매 숙려   (deliberation.html 흡수)
                     └─ [탭] 마이페이지  (test-mypage.html 흡수)
```

- `index.html` → `/login.html` 로 대체 (로그인 화면)
- `deliberation.html`, `test-mypage.html` 삭제 → `app.html` 로 통합
- `localhost:8080` 접속 시 로그인 체크 → 비로그인이면 `/login.html` 리다이렉트

---

## 전체 API 엔드포인트 명세

### 인증

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/oauth2/authorization/kakao` | 불필요 | 카카오 로그인 |
| GET | `/oauth2/authorization/google` | 불필요 | 구글 로그인 |
| GET | `/oauth2/authorization/naver` | 불필요 | 네이버 로그인 |
| POST | `/api/logout` | 필요 | 로그아웃 |

---

### 유저 (마이페이지)

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/users/me` | 필요 | 내 프로필 조회 |
| PATCH | `/api/users/me` | 필요 | 닉네임 수정 (하위호환) |
| PATCH | `/api/users/me/profile` | 필요 | 닉네임 + 마스코트 이름 수정 |
| PATCH | `/api/users/me/budget` | 필요 | 월 예산 설정 |
| PATCH | `/api/users/me/notification-settings` | 필요 | 알림 설정 |
| DELETE | `/api/users/me` | 필요 | 회원 탈퇴 |
| GET | `/api/users/me/stats?yearMonth=` | 필요 | 소비 통계 |
| GET | `/api/users/me/wishes/history?status=&yearMonth=&page=&size=` | 필요 | 아이템 기록 |
| GET | `/api/users/me/posts?cursor=&size=` | 필요 | 내 게시글 |
| GET | `/api/users/me/votes?cursor=&size=` | 필요 | 내가 투표한 게시글 |

---

### 소셜 피드

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/social/posts?cursor=&size=` | 불필요 | 피드 목록 |
| GET | `/social/posts/{postId}` | 불필요 | 게시글 상세 |
| POST | `/social/posts` | **필요** | 게시글 작성 |
| DELETE | `/social/posts/{postId}` | **필요** | 게시글 삭제 |
| POST | `/social/posts/uploads` | 불필요 | 이미지 업로드 |
| POST | `/social/posts/{postId}/votes` | **필요** | 투표 (GO/STOP 토글) |
| GET | `/social/posts/{postId}/comments?page=&size=` | 불필요 | 댓글 목록 |
| POST | `/social/posts/{postId}/comments` | **필요** | 댓글 작성 |
| DELETE | `/social/posts/{postId}/comments/{commentId}` | **필요** | 댓글 삭제 |

---

### 구매 숙려

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/wishes/{itemId}/deliberation` | **필요** | 숙려 화면 조회 |
| POST | `/api/wishes/{itemId}/deliberation` | **필요** | 답변 제출 + 구매 결정 |

---

### 개발/테스트 전용 (prod 비활성)

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/dev/wishes/test` | **필요** | 24시간 경과 테스트 아이템 생성 |

---

### 약관

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/terms` | 불필요 | 약관 목록 |
| GET | `/api/terms/{type}` | 불필요 | 약관 상세 |

---

## 구현 계획

1. `index.html` → `login.html` 로 교체 (소셜 로그인 3버튼)
2. `app.html` 신규 작성 (탭 기반 통합 앱)
   - 진입 시 `/api/users/me` 체크 → 비로그인이면 `/login.html` 리다이렉트
   - 탭 1: 소셜 피드 (피드 목록, 게시글 작성, 투표, 댓글)
   - 탭 2: 구매 숙려 (테스트 아이템 생성 → 숙려 화면 → 결과)
   - 탭 3: 마이페이지 (프로필, 예산, 통계)
3. `deliberation.html`, `test-mypage.html` 삭제
4. SecurityConfig `defaultSuccessUrl` → `/app.html` 로 변경
