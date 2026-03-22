# 404_BE

저장한 콘텐츠가 오늘의 행동이 되는 백엔드 프로토타입이다.

링크를 저장하면 서버가 즉시 실천 카드 1개를 만들고, 홈 덱과 주간 리포트에서 그 결과를 다시 보여준다.

## 구현 범위

- 링크 저장 시 AI 실천 카드 생성
- 오늘의 실천 카드 덱 조회
- 카드 상세 조회
- 실천 카드 완료 처리
- 주간 실천 리포트 조회

## 기술 스택

- Java 21
- Spring Boot 3.2
- Spring Web
- Spring Security
- Spring JDBC
- Flyway
- H2 기본 내장 DB
- Gradle Wrapper

## 빠른 실행

```bash
./gradlew bootRun
```

기본값으로 H2 메모리 DB를 사용하므로 추가 설정 없이 실행 가능하다.

서버를 띄운 뒤 브라우저에서 `http://localhost:8080/`로 접속하면 링크 하나만 붙여넣는 테스트용 프론트를 바로 쓸 수 있다.

## 환경 변수

지금 당장 꼭 채워야 하는 값은 없다.

다만 실제 AI 카드 생성을 Gemini로 쓰려면 `GEMINI_API_KEY`를 넣는 것이 좋다.
키가 없으면 서버는 규칙 기반 카드 생성기로 자동 fallback 한다.

선택적으로 아래 값을 넣으면 된다.

- `DB_URL`: PostgreSQL 등 외부 DB 연결 시 사용
- `DB_USERNAME`: 외부 DB 계정
- `DB_PASSWORD`: 외부 DB 비밀번호
- `DB_DRIVER`: 외부 DB 드라이버 클래스명
- `GEMINI_API_KEY`: Google Gemini API 키
- `GEMINI_MODEL`: 기본값 `gemini-2.5-flash-lite`
- `GEMINI_BASE_URL`: 기본값 `https://generativelanguage.googleapis.com`
- `SCRAPING_TIMEOUT_MILLIS`: 링크 수집 타임아웃
- `SCRAPING_USER_AGENT`: 링크 수집용 User-Agent
- `OLLAMA_MODEL`: 기본값 `qwen3:4b`
- `OLLAMA_BASE_URL`: 기본값 `http://localhost:11434`
- `OLLAMA_TIMEOUT_SECONDS`: 기본값 `120`
- `OLLAMA_MAX_OUTPUT_TOKENS`: 기본값 `320`
- `OLLAMA_BACKGROUND_ENHANCEMENT_ENABLED`: 기본값 `true`

기본값:

- `DB_URL=jdbc:h2:mem:404;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- `DB_USERNAME=sa`
- `DB_PASSWORD=`
- `DB_DRIVER=org.h2.Driver`
- `GEMINI_MODEL=gemini-2.5-flash-lite`
- `GEMINI_BASE_URL=https://generativelanguage.googleapis.com`
- `SCRAPING_TIMEOUT_MILLIS=7000`
- `OLLAMA_MODEL=qwen3:4b`
- `OLLAMA_BASE_URL=http://localhost:11434`
- `OLLAMA_TIMEOUT_SECONDS=120`
- `OLLAMA_MAX_OUTPUT_TOKENS=320`
- `OLLAMA_BACKGROUND_ENHANCEMENT_ENABLED=true`

윈도우에서 Ollama 앱 또는 `ollama serve`가 떠 있으면 기본값 그대로 동작한다.
링크 저장 응답은 규칙 기반 카드로 먼저 빠르게 끝내고, Ollama는 백그라운드에서 더 구체적인 카드로 업그레이드한다.

만약 서버를 WSL이나 다른 호스트에서 띄웠다면 `.env.properties`에 아래처럼 넣으면 된다.

```properties
OLLAMA_BASE_URL=http://localhost:11434
```

예를 들어 WSL에서 열어둔 11434 포트를 윈도우에서 바라봐야 하면 `http://<호스트IP>:11434` 형태로 바꿔주면 된다.

기본값은 `qwen3:4b`이고, 서버 시작 시 Ollama 연결 확인과 간단한 모델 워밍업을 먼저 시도한다.
로컬 CPU 환경이 느리면 `gemma:2b`처럼 더 가벼운 모델로 내릴 수 있다.
응답이 느리면 `OLLAMA_TIMEOUT_SECONDS`를 더 올리거나 `OLLAMA_MAX_OUTPUT_TOKENS`를 더 줄이면 된다.

## 인증 방식

지금 버전은 로그인 연동 없이 `X-User-Id` 헤더로 사용자를 식별한다.

- 헤더가 있으면 해당 UUID 사용
- 헤더가 없으면 데모 사용자 사용

예시:

```text
X-User-Id: 11111111-1111-1111-1111-111111111111
```

## 핵심 API

### 1. 링크 저장 + 실천 카드 생성

`POST /api/v1/content-links`

```json
{
  "url": "https://example.com/gratitude-video"
}
```

응답에는 저장된 링크와 생성된 실천 카드가 함께 들어간다.

이때 서버는 아래 순서로 처리한다.

1. 링크 HTML 수집
2. 제목, 설명, 본문 일부 추출
3. Gemini로 오늘의 행동 카드 생성
4. Gemini 실패 시 규칙 기반 카드 생성 fallback

### 2. 오늘의 실천 카드 덱

`GET /api/v1/facades/home?date=2026-03-21`

- `openCardCount`
- `completedTodayCount`
- `recommendedCard`
- `cards`

### 3. 실천 완료 처리

`POST /api/v1/practice-cards/{cardId}/complete`

```json
{
  "completionNote": "오늘 감사한 일 세 가지를 적었다"
}
```

### 4. 카드 상세 조회

`GET /api/v1/practice-cards/{cardId}`

- `detailTitle`
- `detailBody`
- `ideaOptions`

### 5. 주간 실천 리포트

`GET /api/v1/facades/reports/weekly?date=2026-03-21`

- `savedCount`
- `completedCount`
- `completionRate`
- `pendingCount`
- `savedCategories`
- `completedCategories`
- `recentCompletions`
- `insightMessage`

## 바로 실험하는 방법

### 1. 서버 실행

```bash
./gradlew bootRun
```

브라우저 실험 UI:

```text
http://localhost:8080/
```

### 2. 링크 하나만 저장

```bash
curl -X POST http://localhost:8080/api/v1/content-links \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 11111111-1111-1111-1111-111111111111' \
  -d '{
    "url": "https://example.com/thanks"
  }'
```

확인 포인트:

- `savedContent.category`가 기대한 카테고리인지
- `practiceCard.actionTitle`이 오늘 당장 할 수 있는 문장인지
- `practiceCard.status`가 `OPEN`인지

### 3. 홈 덱 조회

```bash
curl 'http://localhost:8080/api/v1/facades/home?date=2026-03-21' \
  -H 'X-User-Id: 11111111-1111-1111-1111-111111111111'
```

확인 포인트:

- 카드가 `cards` 배열에 보이는지
- `recommendedCard`가 내려오는지
- `openCardCount`가 맞는지

### 4. 카드 완료 처리

2번 응답에서 받은 `practiceCard.id`를 넣어서 호출한다.

```bash
curl -X POST http://localhost:8080/api/v1/practice-cards/{cardId}/complete \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 11111111-1111-1111-1111-111111111111' \
  -d '{
    "completionNote": "실제로 적어봤다"
  }'
```

확인 포인트:

- `status`가 `DONE`으로 바뀌는지
- `completedAt`이 채워지는지

### 5. 주간 리포트 조회

```bash
curl 'http://localhost:8080/api/v1/facades/reports/weekly?date=2026-03-21' \
  -H 'X-User-Id: 11111111-1111-1111-1111-111111111111'
```

확인 포인트:

- `savedCount`, `completedCount`가 실제 행동과 맞는지
- `insightMessage`가 카테고리 성향을 자연스럽게 설명하는지
- `recentCompletions`가 최근 완료 행동을 보여주는지

## 지금 채워주면 좋은 것

기능은 동작하지만, 카드 품질과 제품 방향을 더 끌어올리려면 아래 입력이 있으면 좋다.

- 카테고리 사전: 자기계발, 운동, 인간관계, 요리 외에 꼭 잡고 싶은 도메인
- 카드 톤 가이드: 말투를 코치형, 친구형, 미니멀형 중 무엇으로 갈지
- 완료 정의: 단순 체크만 할지, 메모를 필수로 받을지
- 홈 덱 정책: 오늘 카드만 보여줄지, 미완료 카드 누적을 허용할지
- 리포트 문장 스타일: 분석형, 동기부여형, 숫자 중심형 중 어떤 방향인지
- 추후 AI 연동 방식: 규칙 기반 유지, 외부 LLM 연결, 혼합형 중 어떤 방향인지

## 자동 테스트

```bash
./gradlew test
```

현재 통합 테스트는 아래 흐름을 검증한다.

- 링크 저장 시 카드 생성
- 홈 덱 조회
- 카드 완료 처리
- 주간 리포트 집계

추가로 아래도 테스트한다.

- 스크래퍼가 HTML에서 제목, 설명, 본문을 추출하는지
- Gemini JSON 응답을 카드로 파싱하는지

## 현재 한계

- Gemini 키가 없으면 규칙 기반으로 fallback 한다
- Instagram은 공개 페이지 기준 메타 태그 중심으로 읽고, 로그인 필요한 정보는 못 가져온다
- YouTube, 인스타, 네이버 블로그, 티스토리는 우선 지원하지만 페이지 구조 변경에는 다시 보정이 필요할 수 있다
- 인증은 임시로 `X-User-Id` 헤더 기반이다
- 프론트 계약서나 OpenAPI 문서는 아직 없다

## 다음 우선순위 제안

- 링크 메타데이터 수집기 추가
- LLM 기반 카드 생성기로 교체 또는 혼합
- 카드 스누즈, 보관, 재추천 정책 추가
- 리포트 인사이트 고도화
- Swagger/OpenAPI 추가
