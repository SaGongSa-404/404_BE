# NF-84 쇼핑 링크 비동기 전후 부하 테스트

동일 QA 서버에서 기존 동기 API와 신규 비동기 API를 같은 사용자 비율과 URL 세트로 비교한다. 실행기는 Node.js 20 이상만 사용하며 외부 패키지와 k6 설치가 필요 없다.

## 측정 시나리오

| 시나리오 | 전체 VU | 일반 API | 크롤링 | 목적 |
| --- | ---: | ---: | ---: | --- |
| `baseline` | 100 | 90 | 10 | 현실적인 혼합 부하 |
| `peak` | 100 | 80 | 20 | 크롤링 비중 증가 |
| `backpressure` | 100 | 0 | 100 VU + 경계 요청 1회 | 100개 상한 직후 429 확인 |

일반 API는 기본적으로 `GET /api/auth/me`를 호출한다. 동기 모드는 `POST /api/v1/items/import-link`, 비동기 모드는 작업 접수 후 상태 조회까지 수행한다.

## 안전 원칙

- 운영 서버에서는 실행하지 않는다. 기본 허용 대상은 QA `34-66-55-165.sslip.io`와 localhost뿐이다.
- 실제 실행에는 `CONFIRM_QA_LOAD_TEST=YES`가 필요하다.
- 크롤링 20건 초과에는 `CONFIRM_EXTERNAL_TRAFFIC=YES`가 추가로 필요하다.
- baseline/peak 기본 ramp-up은 30초이며 크롤링 요청은 VU당 1건이다. backpressure만 상한 경계를 보기 위해 1초에 101회 제출한다.
- 토큰은 환경변수에서만 읽고 결과 파일에 저장하지 않는다.
- 실제 상품 URL과 테스트 계정 사용은 QA 담당자 및 외부 사이트 보호 조건을 확인한 뒤 진행한다.
- 같은 사용자와 같은 URL의 진행 중 요청은 하나의 job으로 합쳐질 수 있다. baseline은 최소 4명, peak은 최소 7명, backpressure는 최소 34명의 서로 다른 테스트 사용자 토큰과 서로 다른 URL 세트가 필요하다. 단순히 같은 사용자의 JWT를 여러 개 발급한 것은 서로 다른 사용자로 계산되지 않는다.

## 사전 검증

```bash
cd load-tests/shopping-import
npm test
npm run dry-run
```

`urls.example.txt`를 복사해 승인된 고정 URL 파일을 만든다. 토큰이나 개인정보는 파일에 넣지 않는다.

## 실행 예시

동기 baseline:

```bash
CONFIRM_QA_LOAD_TEST=YES \
ACCESS_TOKEN='<QA access token>' \
URL_FILE='./urls.qa.txt' \
SCENARIO=baseline \
IMPORT_MODE=sync \
node run.mjs
```

비동기 baseline:

```bash
CONFIRM_QA_LOAD_TEST=YES \
ACCESS_TOKEN='<QA access token>' \
URL_FILE='./urls.qa.txt' \
SCENARIO=baseline \
IMPORT_MODE=async \
node run.mjs
```

peak은 `SCENARIO=peak`으로 바꾼다. backpressure는 충분한 테스트 토큰을 `ACCESS_TOKENS`에 쉼표로 전달하고 `CONFIRM_EXTERNAL_TRAFFIC=YES`를 추가한다.

동기 실행 후 Chromium과 queue가 안정 상태로 돌아온 것을 확인하고 같은 조건으로 비동기를 실행한다. 결과 JSON에는 일반 API p50/p95/p99, 제출·polling 지연, queue wait, 처리시간, 전체 완료시간, 성공/실패/429가 기록된다.

## 서버 지표

QA VM에서 부하 실행과 같은 시간대에 다음 수집기를 실행한다.

```bash
cd load-tests/shopping-import
INTERVAL_SECONDS=5 DURATION_SECONDS=180 ./collect-server-metrics.sh
```

Java/Chromium CPU와 RSS는 항상 기록된다. DB 접속용 `PGHOST`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`가 이미 설정된 경우에만 `PENDING/RUNNING` 수도 함께 기록한다. 비밀번호는 CSV에 저장하지 않는다.

## 판정

실행 후 `RESULT_TEMPLATE.md`에 동기/비동기 결과와 서버 CSV 값을 옮긴다.

- 일반 API p95와 오류율이 크롤링 중 안정적으로 유지되는가
- backend/Chromium OOM 또는 서비스 재시작이 없는가
- 비동기 제출 지연이 외부 사이트 응답시간과 분리되는가
- queue wait이 제품 허용시간을 만족하는가
- 100개 제출에서 202/429 backpressure가 설정대로 동작하는가

목표를 만족하면 ADR-001을 유지하고 worker와 polling 권장값을 기록한다. 미달이면 crawler 프로세스 분리, 제한 병렬화, 외부 broker 순으로 재검토한다.
