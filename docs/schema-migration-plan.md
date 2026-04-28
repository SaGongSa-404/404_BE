# NF-12 베이스라인 스키마 반영 계획

## 배경

PR #6(NF-12)에서 팀 전체 도메인/스키마 베이스라인이 확정되었다.
현재 `feature/mypage` 브랜치(마이페이지 + 소셜피드 + 구매 숙려)는 별도로 설계된
엔티티/테이블명을 사용하고 있으므로, 베이스라인 스키마에 맞게 정렬한다.

---

## 변경 범위 요약

| 영역 | 현재 | 변경 후 (베이스라인) |
|------|------|---------------------|
| 유저 | `User` 단일 엔티티 | `User` (핵심) + `UserProfile` (프로필) |
| 유저 상태 | `RaccoonStatus` (NORMAL/CAUTION/DANGER) | `UserStatus` (ACTIVE/SUSPENDED/WITHDRAWN) |
| 유저 프로필 | User 컬럼에 직접 보유 | `user_profiles` 별도 테이블 |
| 위시리스트 | `Wish` / `wishes` | `SavedItem` / `saved_items` |
| 위시 상태 | `WishStatus` (PENDING/BOUGHT/RESTRAINED) | `ItemStatus` (SAVED/GO/STOP/DROPPED) |
| 카테고리 | `Category` (FASHION/BEAUTY/ELECTRONICS/FOOD/HOBBY/ETC) | `ItemCategory` (FASHION/BEAUTY/DIGITAL/LIVING/FOOD/HOBBY/SUBSCRIPTION/ETC) |
| 예산 | `MonthlyBudget` / `monthly_budgets` | `BudgetCycle` / `budget_cycles` |
| 숙려 답변 | `WishDeliberation` (answer1~4 컬럼) | `SelfCheckResponseSet` + `SelfCheckAnswer` (정규화) |
| 소셜 게시글 | `SocialPost` / `social_posts` | `FeedPost` / `feed_posts` |
| 투표 | `Vote` / `votes` (GO/STOP) | `PostVote` / `post_votes` (GO/STOP + canceledAt) |
| 댓글 | `Comment` / `comments` | `PostComment` / `post_comments` (soft delete) |
| 너구리 상태 | `RaccoonStatus` enum + User 필드 | 미구현 (추후 PR) |

---

## 세부 변경 내용

### 1. User 엔티티

| 변경 | 내용 |
|------|------|
| 테이블명 유지 | `users` |
| 제거 필드 | `nickname`, `raccoon_name`, `email`, `profile_image_url`, `monthly_budget`, `impulse_frequency`, `notification_enabled`, `raccoon_status` |
| 추가 필드 | `status` (UserStatus), `onboarding_status` (OnboardingStatus), `withdrawn_at` |
| 신규 엔티티 | `UserProfile` — `user_profiles` 테이블 (nickname, mascot_name, timezone, profile_image_url) |

> **API 영향**: `MyProfileResponse`, `UpdateProfileRequest` 등 User 필드 직접 참조 코드를 `UserProfile` 경유로 수정

---

### 2. Wish → SavedItem

| 변경 | 내용 |
|------|------|
| 클래스명 | `Wish` → `SavedItem` |
| 테이블명 | `wishes` → `saved_items` |
| 필드 변경 | `price` → `listed_price`, `productUrl` → `original_url` |
| 추가 필드 | `input_source` (SHARE/DIRECT_INPUT), `normalized_url`, `currency_code`, `category_confidence`, `category_locked_by_user` |
| 제거 필드 | `decision_at`, `regret`, `regret_recorded_at` (PurchaseDecision으로 이동) |
| 상태 enum | `WishStatus` (PENDING/BOUGHT/RESTRAINED) → `ItemStatus` (SAVED/GO/STOP/DROPPED) |
| 카테고리 enum | `Category` → `ItemCategory` (ELECTRONICS→DIGITAL, 추가: LIVING/SUBSCRIPTION) |

---

### 3. MonthlyBudget → BudgetCycle

| 변경 | 내용 |
|------|------|
| 클래스명 | `MonthlyBudget` → `BudgetCycle` |
| 테이블명 | `monthly_budgets` → `budget_cycles` |
| 필드 변경 | `budget_amount` → `monthly_budget_amount` |
| 추가 필드 | `warning_threshold_rate` (NUMERIC 5,2) |

---

### 4. WishDeliberation → SelfCheckResponseSet + SelfCheckAnswer

| 변경 | 내용 |
|------|------|
| 클래스명 | `WishDeliberation` → `SelfCheckResponseSet` + `SelfCheckAnswer` |
| 테이블명 | `wish_deliberations` → `self_check_response_sets` + `self_check_answers` |
| 구조 변경 | answer1~4 컬럼 → `SelfCheckAnswer` 행으로 정규화 (question_code + answer_boolean) |
| 추가 필드 | `rationality_result` (RATIONAL/IRRATIONAL), `submitted_at` |
| 연결 변경 | wish_id → `PurchaseDecision`의 decision_id (wishId 직접 연결 제거) |

> 현재 숙려 화면은 wish_id 기준으로 동작하므로, PurchaseDecision과 SavedItem의 관계를 거쳐 접근하도록 수정

---

### 5. 소셜 피드

| 변경 | 현재 | 변경 후 |
|------|------|---------|
| 게시글 클래스 | `SocialPost` | `FeedPost` |
| 게시글 테이블 | `social_posts` | `feed_posts` |
| 게시글 추가 | - | `go_count`, `stop_count` (비정규화), `deleted_at` (소프트 삭제) |
| 게시글 제거 | `category`, `image_url`, `product_url` | - |
| 투표 클래스 | `Vote` | `PostVote` |
| 투표 테이블 | `votes` | `post_votes` |
| 투표 enum | `VoteType` (GO/STOP) | `PostVoteType` (GO/STOP) |
| 투표 추가 | - | `canceled_at` |
| 댓글 클래스 | `Comment` | `PostComment` |
| 댓글 테이블 | `comments` | `post_comments` |
| 댓글 추가 | - | `deleted_at` (소프트 삭제) |

---

## API 영향 범위

### 변경 필요 API

| API | 영향 |
|-----|------|
| `GET /api/users/me` | UserProfile 조인 필요 |
| `PATCH /api/users/me/profile` | UserProfile 수정으로 변경 |
| `PATCH /api/users/me/budget` | BudgetCycle 사용으로 변경 |
| `GET /api/users/me/stats` | BudgetCycle + SavedItem 기준으로 변경 |
| `GET /api/users/me/wishes/history` | SavedItem 기준으로 변경 |
| `GET/POST /api/wishes/{id}/deliberation` | SavedItem + PurchaseDecision 구조로 변경 |
| `GET /social/posts` | FeedPost + 소프트 삭제 필터 |
| `POST /social/posts/{id}/votes` | PostVote + canceledAt 처리 |
| `DELETE /social/posts/{id}/comments/{cId}` | 소프트 삭제 처리 |

---

## 구현 순서

1. **Enum 정리** — ItemStatus, ItemCategory, ItemInputSource, PostVoteType, UserStatus, OnboardingStatus, RationalityResult
2. **User 분리** — User 필드 정리 + UserProfile 엔티티 추가
3. **Wish → SavedItem** — 엔티티/레포지토리/서비스 리네임 및 필드 수정
4. **MonthlyBudget → BudgetCycle** — 리네임 및 필드 수정
5. **WishDeliberation → SelfCheckResponseSet/Answer** — 구조 변경
6. **소셜 엔티티** — FeedPost / PostVote / PostComment 리네임 및 소프트 삭제 추가
7. **서비스/컨트롤러** — 변경된 엔티티 참조 수정
8. **응답 DTO** — 필드명 변경 반영
