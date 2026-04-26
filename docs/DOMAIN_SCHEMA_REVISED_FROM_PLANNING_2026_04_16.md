# 도메인 및 스키마 수정안

작성일: 2026.04.16

기준 문서:

- `docs/DOMAIN_SCHEMA_DRAFT_2026_04_16.md`
- `docs/PRODUCT_PLANNING_QA_2026_04_16.md`

이 문서는 기존 도메인/스키마 초안에 기획팀 Q&A 내용을 반영한 수정안입니다.
확정된 기획은 스키마에 반영하고, 아직 기획/정책 검토가 필요한 항목은 `보류` 또는 `확장 가능`으로 표시했습니다.

---

## 수정 방향 요약

| 영역 | 수정 방향 | 이유 |
| --- | --- | --- |
| Decision | 최종 결정은 `purchase_decisions`에 1건만 유지하고, 변경 이력은 별도 로그로 분리 | 유저에게는 최종 결정만 노출하지만 팀 내부 확인용 이력은 필요하기 때문 |
| Self Check | 합리성 "점수" 대신 `rationality_result`와 `yes_count` 저장 | 기획상 Yes 개수 기준의 합리/비합리 판별이므로 점수보다 판별 결과가 정확함 |
| Budget | `spent_amount`는 GO 결정 건만 반영 | 위시리스트/고려중 아이템은 실소비와 예산 경고에 포함하지 않기로 했기 때문 |
| Item | 현재 위시목록 내 동일 URL 중복 저장 방지 인덱스 추가 | 동일 URL 저장 시 기존 아이템으로 안내하기로 했기 때문 |
| Mascot | `mood` + `raccoon_status` 분리 제거, 단일 `mascot_state`로 통합 | 기획에서 표정과 배경 날씨를 하나의 상태값으로 통합하기로 했기 때문 |
| Notification | 푸시 알림 설정과 기기 토큰 테이블 추가 | 알림은 인앱만이 아니라 푸시 포함이며 기기별 on/off가 필요하기 때문 |
| Social | 게시글/댓글 soft delete, 게시글당 share token 1개 제약 추가 | 삭제 데이터 보존과 토큰 1:1 정책이 기획에 반영됐기 때문 |
| Terms | 약관 동의 버전 관리 테이블 추가 | 출시 목표 서비스라 약관 개정 및 재동의 이력이 필요하기 때문 |

---

## 도메인

1. Auth
2. User
3. Budget
4. Item
5. Decision
6. Reflection
7. Mascot
8. Notification
9. Social
10. Terms / Consent
11. Token

---

## Auth 스키마

### users

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 사용자 ID |
| status | varchar(20) | `active`, `suspended`, `withdrawn` |
| onboarding_status | varchar(20) | `not_started`, `in_progress`, `completed` |
| created_at | timestamptz | 생성 시각 |
| updated_at | timestamptz | 수정 시각 |
| withdrawn_at | timestamptz null | 탈퇴 처리 시각 |

변경 이유:

- 기존 `terms_agreed_at`, `privacy_agreed_at`, `marketing_opt_in`은 단순 현재값만 표현할 수 있습니다.
- 기획에서 약관 버전 관리와 마케팅 수신 동의/철회 이력 필요성이 나왔으므로, 동의 정보는 `Terms / Consent` 도메인으로 분리합니다.
- `status`, `onboarding_status`는 기획에서 정의한 상태값을 문서에 명시합니다.
- `withdrawn_at`은 탈퇴 상태일 때만 값이 있어야 하므로 `status = withdrawn`과 함께 DB 제약으로 묶습니다.

### social_accounts

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 소셜 계정 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| provider | varchar(20) | `kakao`, `google`, `apple` |
| provider_user_id | varchar(120) | provider 내부 사용자 ID |
| email | varchar(255) null | 소셜 계정 이메일 |
| profile_image_url | text null | 소셜 프로필 이미지 |
| created_at | timestamptz | 생성 시각 |
| updated_at | timestamptz | 수정 시각 |

인덱스:

- unique (`provider`, `provider_user_id`)
- unique (`user_id`)

변경 이유:

- MVP에서는 한 유저가 여러 소셜 계정을 연결하지 않습니다.
- 따라서 `unique(user_id)`를 추가해 사용자 1명당 소셜 계정 1개만 허용합니다.
- `unique(user_id)`가 `user_id` 조회용 인덱스 역할도 하므로 별도 `index(user_id)`는 두지 않습니다.
- 추후 다중 provider 연결을 허용하면 `unique(user_id)`는 제거하고 `unique(user_id, provider)`로 완화할 수 있습니다.

### refresh_tokens

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | refresh token ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| token_hash | varchar(255) | refresh token 해시 |
| device_id | varchar(120) null | 기기 식별자 |
| device_name | varchar(120) null | 기기명 |
| expires_at | timestamptz | 만료 시각 |
| revoked_at | timestamptz null | 폐기 시각 |
| created_at | timestamptz | 생성 시각 |
| last_used_at | timestamptz null | 마지막 사용 시각 |

인덱스:

- index (`user_id`, `created_at desc`)
- unique (`token_hash`)

변경 이유:

- 기존 구조 유지.
- 다만 테이블명은 여러 토큰을 저장하므로 `refresh_tokens`처럼 복수형을 권장합니다.

---

## User 스키마

### user_profiles

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| user_id | uuid pk fk -> `users.id` | 사용자 ID |
| nickname | varchar(40) | 닉네임 |
| mascot_name | varchar(40) | 너굴 이름 |
| timezone | varchar(40) | 사용자 타임존 |
| profile_image_url | text null | 프로필 이미지. MVP에서는 사용하지 않을 수 있음 |
| created_at | timestamptz | 생성 시각 |
| updated_at | timestamptz | 수정 시각 |

변경 이유:

- 소셜 게시글/댓글에는 닉네임만 노출합니다.
- 프로필 이미지는 MVP 범위 밖이지만, 향후 확장을 위해 nullable로 유지할 수 있습니다.

### survey_response_sets

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 설문 제출 묶음 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| survey_type | varchar(40) | 설문 종류 |
| submitted_at | timestamptz | 제출 시각 |

인덱스:

- unique (`user_id`, `survey_type`)

변경 이유:

- 온보딩 설문은 최초 1회만 받기로 했습니다.
- 같은 사용자가 같은 `survey_type`을 여러 번 제출하지 못하도록 제약을 추가합니다.

### survey_answers

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 답변 ID |
| response_set_id | uuid fk -> `survey_response_sets.id` | 설문 제출 묶음 ID |
| question_code | varchar(80) | 문항 코드 |
| answer_text | text null | 텍스트 답변 |
| answer_number | integer null | 숫자 답변 |
| answer_choice | varchar(80) null | 단일 선택 답변 |

변경 이유:

- 온보딩 설문은 단일 선택 중심이지만, 텍스트/숫자 답변 가능성을 유지합니다.
- MVP에서 복수 선택은 없으므로 별도 다중 선택 테이블은 두지 않습니다.

---

## Budget 스키마

### budget_cycles

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 예산 사이클 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| year_month | char(7) | `YYYY-MM` |
| monthly_budget_amount | integer | 월 예산 금액 |
| spent_amount | integer | GO 완료 건 기준 누적 소비 금액 |
| warning_threshold_rate | numeric(5,2) | 예산 경고 기준 비율 |
| created_at | timestamptz | 생성 시각 |
| updated_at | timestamptz | 수정 시각 |

인덱스:

- unique (`user_id`, `year_month`)

변경 이유:

- 기획상 홈 예산 현황은 GO 완료 건만 기준으로 합니다.
- 위시리스트 저장 건, 고려중 아이템, PENDING 상태는 실소비에서 제외합니다.
- 숙려화면의 "이 상품을 사면 예산이 어떻게 되는지"는 별도 저장값이 아니라 조회 시뮬레이션으로 계산하는 것이 적절합니다.
- 월 예산 변경 시 기존 소비 기록은 유지하고 기준 금액만 즉시 업데이트합니다. 따라서 별도 예산 스냅샷 테이블은 MVP에서 만들지 않습니다.
- 예산 금액과 소비 금액은 음수가 될 수 없고, 경고 기준 비율은 0~100 퍼센트 스케일로 DB 제약을 둡니다.

---

## Item 스키마

### saved_items

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 아이템 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| input_source | varchar(20) | 입력 경로 |
| original_url | text null | 원본 URL |
| normalized_url | text null | 정규화 URL |
| title | varchar(255) | 상품명 |
| image_url | text null | 상품 이미지 |
| listed_price | integer null | 사용자가 입력한 상품 금액 |
| currency_code | char(3) null | 통화 코드 |
| category | varchar(30) | 카테고리 |
| category_confidence | numeric(5,2) null | 자동 분류 신뢰도 |
| category_locked_by_user | boolean | 유저가 카테고리를 직접 확정했는지 여부 |
| status | varchar(20) | `SAVED`, `GO`, `STOP`, `DROPPED` |
| created_at | timestamptz | 생성 시각 |
| updated_at | timestamptz | 수정 시각 |

인덱스:

- index (`user_id`, `status`, `created_at desc`)
- index (`user_id`, `category`, `created_at desc`)
- unique (`user_id`, `normalized_url`) where `status = 'SAVED'` and `normalized_url is not null`

변경 이유:

- 기획에서 아이템 상태 흐름을 `SAVED -> GO/STOP`, `SAVED -> DROPPED`로 정의했습니다.
- 동일 URL은 현재 위시목록에 있을 때만 중복 저장을 막습니다.
- 이미 GO/STOP 처리되어 소비기록으로 이동한 과거 아이템까지 중복 저장을 막으면, 같은 상품을 나중에 다시 고민하는 흐름을 막을 수 있으므로 `SAVED` 상태에만 partial unique를 적용합니다.
- 상품 가격은 저장 시점에 유저가 입력한 `listed_price`만 사용합니다. 가격 변동 추적은 MVP 범위 밖입니다.
- 상품 금액은 음수가 될 수 없고, 카테고리 신뢰도는 0~100 퍼센트 스케일로 DB 제약을 둡니다.

### item_source_metadata

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| item_id | uuid pk fk -> `saved_items.id` | 아이템 ID |
| source_domain | varchar(120) null | 출처 도메인 |
| raw_title | text null | 원본 제목 |
| raw_description | text null | 원본 설명 |
| raw_price_text | varchar(120) null | 원본 가격 문자열 |
| raw_payload_json | jsonb null | 원본 추출 데이터 |
| extracted_at | timestamptz | 추출 시각 |

변경 이유:

- 기존 구조 유지.
- 가격 변동 추적용이 아니라 저장 시점의 원본 메타데이터 보관 용도로만 사용합니다.

---

## Decision 스키마

### purchase_decisions

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 구매 결정 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| item_id | uuid fk -> `saved_items.id` | 아이템 ID |
| budget_cycle_id | uuid fk -> `budget_cycles.id` null | 결정 시점의 월 예산 사이클 |
| result | varchar(20) | `GO`, `STOP` |
| final_price | integer null | 예산 반영 금액. 기본값은 `saved_items.listed_price` |
| budget_after_amount | integer null | GO 선택 시 반영 후 소비 누적 금액 |
| similar_category_spend_amount | integer null | 현재 월 같은 카테고리 소비 금액 |
| rationality_result | varchar(20) | `RATIONAL`, `IRRATIONAL` |
| self_check_yes_count | smallint | 셀프체크 Yes 응답 개수 |
| rationale_text | text null | 결정 사유 메모 |
| is_changed | boolean | 소비기록에서 재결정된 적 있는지 여부 |
| change_count | integer | 재결정 횟수 |
| changed_at | timestamptz null | 마지막 재결정 시각 |
| decided_at | timestamptz | 최초 결정 시각 |
| updated_at | timestamptz | 수정 시각 |

인덱스:

- index (`user_id`, `decided_at desc`)
- unique (`item_id`)

변경 이유:

- 유저에게 노출되는 구매 결정은 최종값 1개입니다. 따라서 `unique(item_id)`는 유지합니다.
- 다만 소비기록에서 수정 가능하고, 수정 시 예산에도 반영되므로 최종 row는 업데이트되어야 합니다.
- 소비기록의 `"변경됨"` 배지를 위해 `is_changed`, `change_count`, `changed_at`을 추가합니다.
- 기획에서 "합리성 점수"보다 "합리성 판별 결과"가 적절하다고 정리했으므로 `rationality_result`, `self_check_yes_count`를 추가합니다.
- 예산현황, 지난 카테고리 이력, 기회비용은 합리성 판별에 영향을 주지 않습니다. 따라서 합리성 관련 값은 셀프체크 Yes 개수 기준으로만 저장합니다.
- 금액 스냅샷과 변경 횟수는 음수가 될 수 없으므로 DB 제약으로 방어합니다.

### purchase_decision_change_logs

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 변경 이력 ID |
| decision_id | uuid fk -> `purchase_decisions.id` | 구매 결정 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| item_id | uuid fk -> `saved_items.id` | 아이템 ID |
| previous_result | varchar(20) | 변경 전 `GO` 또는 `STOP` |
| new_result | varchar(20) | 변경 후 `GO` 또는 `STOP` |
| previous_final_price | integer null | 변경 전 예산 반영 금액 |
| new_final_price | integer null | 변경 후 예산 반영 금액 |
| previous_rationality_result | varchar(20) null | 변경 전 합리성 판별 결과 |
| new_rationality_result | varchar(20) null | 변경 후 합리성 판별 결과 |
| previous_self_check_yes_count | smallint null | 변경 전 Yes 개수 |
| new_self_check_yes_count | smallint null | 변경 후 Yes 개수 |
| reason_text | text null | 수정 이유. MVP에서는 선택 입력 권장 |
| changed_at | timestamptz | 변경 시각 |

인덱스:

- index (`decision_id`, `changed_at desc`)
- index (`user_id`, `changed_at desc`)

변경 이유:

- 기획상 유저에게는 최종 결정만 보여주지만, 팀 내부 확인용 이력은 필요하다고 되어 있습니다.
- 그래서 현재 상태는 `purchase_decisions`에 두고, 변경 이력은 별도 로그 테이블로 분리합니다.
- 수정 이유는 강제가 아니라 선택 입력 쪽 의견이 있으므로 `null` 허용으로 둡니다.
- 마이페이지에서 재결정할 때는 너굴 반응이 없어야 하므로, 이 로그가 생겨도 `mascot_state_events`는 만들지 않습니다.
- 변경 전후 금액 스냅샷은 음수가 될 수 없으므로 DB 제약으로 방어합니다.

### self_check_response_sets

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 셀프체크 제출 묶음 ID |
| decision_id | uuid fk -> `purchase_decisions.id` | 구매 결정 ID |
| yes_count | smallint | Yes 응답 개수 |
| rationality_result | varchar(20) | `RATIONAL`, `IRRATIONAL` |
| submitted_at | timestamptz | 제출 시각 |

인덱스:

- unique (`decision_id`)

변경 이유:

- 기획상 최종 결정 직전 마지막 셀프체크 결과만 대표값으로 사용합니다.
- 내부 이력까지 남기는 경우에는 위 `purchase_decision_change_logs`에 변경 전후 판별 결과를 스냅샷으로 저장합니다.
- Yes 0~1개는 `RATIONAL`, Yes 2개 이상은 `IRRATIONAL`로 계산합니다.

### self_check_answers

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 셀프체크 답변 ID |
| response_set_id | uuid fk -> `self_check_response_sets.id` | 셀프체크 제출 묶음 ID |
| question_code | varchar(80) | 문항 코드 |
| answer_boolean | boolean | Yes/No 답변 |
| created_at | timestamptz | 생성 시각 |

변경 이유:

- 셀프체크 4문항은 Yes/No 형태로 확정됐습니다.
- 기존 `answer_type`, `answer_number`, `answer_text`는 과하게 넓습니다.
- MVP에서는 단일 boolean 답변만 두는 편이 구현과 검증이 단순합니다.

---

## Notification 스키마

### user_notification_settings

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| user_id | uuid pk fk -> `users.id` | 사용자 ID |
| push_enabled | boolean | 전체 푸시 알림 on/off |
| regret_reminder_enabled | boolean | 7일 후 후회 여부 알림 on/off |
| wishlist_reminder_enabled | boolean | 오래 고민 중인 아이템 알림 on/off. MVP 확정 전 |
| budget_warning_enabled | boolean | 예산 초과 임박 알림 on/off. MVP 확정 전 |
| social_vote_enabled | boolean | 소셜 투표 결과 알림 on/off. MVP 확정 전 |
| updated_at | timestamptz | 수정 시각 |

변경 이유:

- 기획에서 알림은 인앱만이 아니라 푸시 알림도 포함한다고 했습니다.
- 기기별 알림 설정은 마이페이지에서 관리하므로 사용자 알림 설정 테이블이 필요합니다.
- 확정된 알림은 7일 후 후회 여부 알림이고, 나머지는 고려 대상이므로 nullable 또는 기본값 off로 시작할 수 있습니다.

### device_push_tokens

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 푸시 토큰 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| device_id | varchar(120) null | 기기 식별자 |
| platform | varchar(20) | `ios`, `android` |
| push_token | text | FCM/APNs 토큰 |
| is_active | boolean | 활성 여부 |
| created_at | timestamptz | 생성 시각 |
| updated_at | timestamptz | 수정 시각 |
| disabled_at | timestamptz null | 비활성화 시각 |

인덱스:

- unique (`push_token`)
- index (`user_id`, `is_active`)

변경 이유:

- 푸시 발송을 하려면 사용자별 기기 토큰 저장이 필요합니다.
- 한 사용자가 여러 기기를 사용할 수 있으므로 `users`에 직접 넣지 않고 별도 테이블로 분리합니다.

### notifications

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 알림 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| notification_type | varchar(40) | 알림 종류 |
| title | varchar(140) | 알림 제목 |
| body | text | 알림 본문 |
| item_id | uuid fk -> `saved_items.id` null | 관련 아이템 |
| decision_id | uuid fk -> `purchase_decisions.id` null | 관련 구매 결정 |
| reminder_id | uuid fk -> `reminder_schedules.id` null | 관련 리마인더 |
| target_path | text null | 클릭 시 이동 경로 |
| is_read | boolean | 읽음 여부 |
| read_at | timestamptz null | 읽은 시각 |
| created_at | timestamptz | 생성 시각 |

인덱스:

- index (`user_id`, `is_read`, `created_at desc`)
- index (`user_id`, `created_at desc`)

변경 이유:

- 기존 구조 유지.
- 7일 후 후회 여부 알림 클릭 시 해당 아이템 후회 여부 응답 모달로 이동해야 하므로 `target_path`를 유지합니다.
- `is_read`와 `read_at`이 서로 다른 상태를 가리키지 않도록 읽음 여부와 읽은 시각을 DB 제약으로 묶습니다.

### reminder_schedules

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 리마인더 예약 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| item_id | uuid fk -> `saved_items.id` null | 관련 아이템 |
| decision_id | uuid fk -> `purchase_decisions.id` null | 관련 구매 결정 |
| reminder_type | varchar(40) | `REGRET_CHECK_7_DAYS` |
| scheduled_for | timestamptz | 발송 예약 시각 |
| status | varchar(20) | `scheduled`, `sent`, `failed`, `canceled` |
| sent_at | timestamptz null | 발송 완료 시각 |
| canceled_at | timestamptz null | 취소 시각 |
| cancel_reason | varchar(80) null | 취소 이유 |
| created_at | timestamptz | 생성 시각 |

인덱스:

- index (`user_id`, `scheduled_for`)
- index (`status`, `scheduled_for`)
- unique (`decision_id`, `reminder_type`)

변경 이유:

- 7일 후 후회 여부 알림은 GO 선택 건에만 자동 생성합니다.
- STOP 선택 건에는 생성하지 않습니다.
- GO에서 STOP으로 재결정되면 예약된 알림을 자동 취소해야 하므로 `canceled_at`, `cancel_reason`을 추가합니다.
- 동일 구매 결정에 같은 7일 후 알림이 중복 예약되지 않도록 unique 제약을 둡니다.
- `REGRET_CHECK_7_DAYS`는 구매 결정에 묶인 알림이므로 `decision_id`가 반드시 있어야 합니다.
- `sent_at`, `canceled_at`은 `status`와 불일치하지 않도록 DB 제약으로 묶습니다.

---

## Reflection 스키마

### purchase_reflections

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 후회 여부 응답 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| item_id | uuid fk -> `saved_items.id` | 아이템 ID |
| decision_id | uuid fk -> `purchase_decisions.id` | 구매 결정 ID |
| reminder_id | uuid fk -> `reminder_schedules.id` null | 관련 리마인더 |
| satisfaction_score | smallint null | 만족도 점수 |
| regret_level | varchar(20) | 후회 수준 |
| still_using | boolean null | 계속 사용 중인지 여부 |
| reflection_note | text null | 회고 메모 |
| reflected_at | timestamptz | 응답 시각 |

인덱스:

- unique (`decision_id`)
- index (`user_id`, `reflected_at desc`)

변경 이유:

- 7일 후 후회 여부 질문은 GO 선택 건에만 발송됩니다.
- 한 구매 결정에 대한 후회 여부 응답은 MVP에서는 1회로 보는 것이 단순합니다.
- 후회 여부 질문 화면이 와이어프레임에 빠져 있으므로, 세부 문항은 추후 기획 확정 후 조정할 수 있습니다.

---

## Mascot 스키마

### mascot_profiles

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| user_id | uuid pk fk -> `users.id` | 사용자 ID |
| mascot_state | varchar(20) | `DEFAULT`, `SMILE`, `VERY_HAPPY`, `SAD` |
| last_reaction_message | varchar(140) null | 마지막 반응 문구 |
| last_state_changed_at | timestamptz | 마지막 상태 변경 시각 |
| reaction_expires_at | timestamptz null | 반응 종료 예정 시각. 지속 시간 확정 전 |
| updated_at | timestamptz | 수정 시각 |

너굴 상태값:

| DB 값 | 기획 상태 | 표정 | 배경 날씨 |
| --- | --- | --- | --- |
| DEFAULT | ① 기본 | 평온 | 없음 |
| SMILE | ② 미소 | 미소 | 맑음 |
| VERY_HAPPY | ③ 엄청 기쁨 | 엄청 기쁨 | 맑음 |
| SAD | ④ 슬픔 | 슬픔 | 비 |

변경 이유:

- 기존 초안의 `mood`, `raccoon_status`, `NORMAL / CAUTION / DANGER / DARK`는 기획 확정안과 다릅니다.
- 기획에서는 표정과 배경 날씨를 각각 따로 관리하지 않고 하나의 상태값으로 통합하기로 했습니다.
- 예산 초과 전용 상태는 아직 확정되지 않았으므로 MVP 기본 4종에 넣지 않습니다.
- 반응 지속 시간은 디자이너와 별도 협의가 필요하므로 `reaction_expires_at`은 nullable로 둡니다.

### mascot_state_events

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 너굴 상태 이벤트 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| item_id | uuid fk -> `saved_items.id` null | 관련 아이템 |
| decision_id | uuid fk -> `purchase_decisions.id` null | 관련 구매 결정 |
| event_type | varchar(40) | `DECISION_REACTION`, `REACTION_RESET` |
| previous_state | varchar(20) null | 이전 상태 |
| new_state | varchar(20) | 새 상태 |
| reaction_message | varchar(140) null | 유저에게 보여줄 짧은 문구 |
| created_at | timestamptz | 생성 시각 |

인덱스:

- index (`user_id`, `created_at desc`)
- index (`decision_id`)

변경 이유:

- 너굴 상태는 숙려화면에서 최초 구매 결정 완료 시에만 반응합니다.
- 마이페이지 소비기록에서 재결정할 때는 너굴 반응 없이 조용히 처리하므로 이벤트를 생성하지 않습니다.
- 기획상 반응 이유를 짧은 문구로 전달하므로 `reaction_message`를 추가합니다.

너굴 상태 계산 규칙:

| 구매 결정 | 합리성 판별 | 저장 상태 |
| --- | --- | --- |
| GO | RATIONAL | SMILE |
| GO | IRRATIONAL | SAD |
| STOP | RATIONAL | SMILE |
| STOP | IRRATIONAL | VERY_HAPPY |

---

## Social 스키마

### feed_posts

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 게시글 ID |
| user_id | uuid fk -> `users.id` | 작성자 ID |
| item_id | uuid fk -> `saved_items.id` null | 관련 아이템 |
| decision_id | uuid fk -> `purchase_decisions.id` null | 관련 구매 결정 |
| title | varchar(140) | 제목 |
| body | text null | 본문 |
| go_count | integer | GO 투표 수 |
| stop_count | integer | STOP 투표 수 |
| created_at | timestamptz | 생성 시각 |
| updated_at | timestamptz | 수정 시각 |
| deleted_at | timestamptz null | soft delete 시각 |

인덱스:

- index (`created_at desc`) where `deleted_at is null`
- index (`user_id`, `created_at desc`)

변경 이유:

- 기획에서 게시글 삭제는 가능하지만 soft delete 적용을 요청했습니다.
- 운영/신고 기능이 MVP 밖이어도 추후 검토와 복구 가능성을 위해 `deleted_at`만 기록합니다.
- 투표 수는 음수가 될 수 없으므로 DB 제약으로 방어합니다.

### post_votes

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 투표 ID |
| post_id | uuid fk -> `feed_posts.id` | 게시글 ID |
| user_id | uuid fk -> `users.id` | 투표자 ID |
| vote_type | varchar(10) | `GO`, `STOP` |
| canceled_at | timestamptz null | 투표 취소 시각 |
| created_at | timestamptz | 생성 시각 |
| updated_at | timestamptz | 수정 시각 |

인덱스:

- unique (`post_id`, `user_id`)
- index (`post_id`, `vote_type`) where `canceled_at is null`

변경 이유:

- 로그인 사용자만 투표 가능하므로 `user_id`가 필수입니다.
- `unique(post_id, user_id)`는 중복 투표 방지와 투표 변경을 모두 지원합니다.
- 투표 취소를 지원하려면 row를 삭제하지 않고 `canceled_at`을 채우면 됩니다.
- 만약 투표 취소를 MVP에서 제외한다면 `canceled_at`은 제거해도 됩니다.

### post_comments

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 댓글 ID |
| post_id | uuid fk -> `feed_posts.id` | 게시글 ID |
| user_id | uuid fk -> `users.id` | 작성자 ID |
| body | varchar(300) | 댓글 내용 |
| created_at | timestamptz | 생성 시각 |
| updated_at | timestamptz | 수정 시각 |
| deleted_at | timestamptz null | soft delete 시각 |

인덱스:

- index (`post_id`, `created_at`) where `deleted_at is null`

변경 이유:

- 본인 댓글 삭제는 가능하고 soft delete가 필요합니다.
- 게시글 작성자가 타인 댓글을 삭제하는 기능은 MVP에서 제외합니다.

### share_tokens

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 공유 토큰 ID |
| feed_post_id | uuid fk -> `feed_posts.id` | 게시글 ID |
| token | varchar(120) | 공유 토큰 |
| created_at | timestamptz | 생성 시각 |
| updated_at | timestamptz | 수정 시각 |

인덱스:

- unique (`feed_post_id`)
- unique (`token`)

변경 이유:

- 기획에서 share token은 게시글당 1개만 발급하고, MVP에서는 재발급과 만료를 두지 않기로 했습니다.
- 따라서 `expires_at`, `revoked_at`은 MVP 스키마에서 제거합니다.
- 추후 어뷰징 대응이 필요해지면 `expires_at`, `revoked_at`, 재발급 이력 테이블을 추가할 수 있습니다.

---

## Terms / Consent 스키마

### terms_versions

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 약관 버전 ID |
| terms_type | varchar(40) | `TERMS_OF_SERVICE`, `PRIVACY_POLICY`, `MARKETING`, `AGE_CONFIRMATION` |
| version | varchar(40) | 약관 버전 |
| title | varchar(120) | 약관 제목 |
| content_url | text null | 약관 전문 URL |
| is_required | boolean | 필수 동의 여부 |
| effective_from | timestamptz | 적용 시작 시각 |
| created_at | timestamptz | 생성 시각 |

인덱스:

- unique (`terms_type`, `version`)
- index (`terms_type`, `effective_from desc`)

변경 이유:

- 기획에서 약관/개인정보/마케팅 동의는 버전 관리가 필요하다고 정리했습니다.
- 유저가 어느 버전의 약관에 동의했는지 기록해야 재동의 플로우를 만들 수 있습니다.

### user_terms_agreements

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 사용자 약관 동의 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| terms_version_id | uuid fk -> `terms_versions.id` | 동의한 약관 버전 |
| agreed_at | timestamptz | 동의 시각 |
| revoked_at | timestamptz null | 철회 시각. 선택 약관에 사용 |

인덱스:

- unique (`user_id`, `terms_version_id`)
- index (`user_id`, `agreed_at desc`)

변경 이유:

- `users.terms_agreed_at`처럼 단일 시각만 저장하면 약관 버전을 알 수 없습니다.
- 약관 개정 시 재동의가 필요한 사용자를 찾기 위해 사용자-약관버전 매핑 테이블이 필요합니다.

### marketing_consent_histories

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | uuid pk | 마케팅 수신 동의 이력 ID |
| user_id | uuid fk -> `users.id` | 사용자 ID |
| consented | boolean | 동의 여부 |
| changed_at | timestamptz | 변경 시각 |
| source | varchar(40) null | 변경 경로 |
| note | text null | 비고 |

인덱스:

- index (`user_id`, `changed_at desc`)

변경 이유:

- 마케팅 수신 동의/철회는 시점 단위 이력 관리가 필요할 가능성이 큽니다.
- 현재 동의 여부만 빠르게 조회해야 한다면 `users`에 캐시 컬럼을 둘 수도 있지만, 원본 이력은 이 테이블을 기준으로 합니다.
- 세부 법무 정책은 기획팀 조사 후 확정이 필요합니다.

---

## 보류 또는 추가 확인 필요 항목

### 탈퇴 데이터 처리 정책

현재 반영:

- `users.status = withdrawn`
- `users.withdrawn_at`
- 게시글/댓글은 `deleted_at` 기반 soft delete 가능

추가 확인 필요:

- 게시글/댓글/투표를 삭제할지 익명화할지
- 구매결정/예산 데이터를 삭제할지 일정 기간 보관할지
- 통계 목적 데이터 잔존을 허용할지

이유:

- 기획 문서에서 탈퇴 시 데이터 처리 정책은 중간고사 이후 조사 후 확정 예정으로 되어 있습니다.
- 지금 스키마에서 강하게 확정하면 나중에 정책 변경 시 마이그레이션 비용이 커질 수 있습니다.

### 예산 초과 시 너굴 상태

현재 반영:

- MVP 기본 4종 상태만 반영
- 예산 초과 전용 상태는 미반영

추가 확인 필요:

- 예산 초과 상태를 MVP에 넣을지
- 기존 4종과 별도 상태로 둘지
- 문구와 이미지가 필요한지

이유:

- 기획에서 예산 초과 시 마스코트 변화는 별도 검토 예정이라고 되어 있습니다.

### 투표 취소

현재 반영:

- `post_votes.canceled_at`으로 취소 가능 구조를 제안

추가 확인 필요:

- MVP에서 투표 취소를 실제로 지원할지
- 변경만 허용하고 취소는 제외할지

이유:

- 기획에서 투표 변경/취소는 개발 복잡도에 따라 결정하고자 한다고 되어 있습니다.

### 추가 알림 종류

현재 반영:

- 7일 후 후회 여부 알림은 확정
- 위시 장기 미결정, 예산 초과 임박, 소셜 투표 결과 알림은 설정 컬럼만 확장 가능하게 제안

추가 확인 필요:

- 각 알림의 정확한 발송 조건
- 알림 문구
- 클릭 시 이동 경로

이유:

- 기획에서 7일 후 후회 여부 알림 외에는 추가 고려 대상으로 정리되어 있습니다.
