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
- 토큰은 환경변수 또는 권한 `600`의 `ACCESS_TOKEN_FILE`에서만 읽고 결과 파일에 저장하지 않는다.
- 실제 상품 URL과 테스트 계정 사용은 QA 담당자 및 외부 사이트 보호 조건을 확인한 뒤 진행한다.
- 같은 사용자와 같은 URL의 진행 중 요청은 하나의 job으로 합쳐질 수 있다. baseline은 최소 4명, peak은 최소 7명, backpressure는 최소 34명의 서로 다른 테스트 사용자 토큰과 서로 다른 URL 세트가 필요하다. 단순히 같은 사용자의 JWT를 여러 개 발급한 것은 서로 다른 사용자로 계산되지 않는다.
- 계정 여러 개를 준비할 수 없으면 기존 QA reviewer 계정 하나를 사용할 수 있다. 이 경우 `SINGLE_USER_MODE=YES`를 명시하고 QA의 `SHOPPING_IMPORT_JOB_MAX_ACTIVE_PER_USER`를 시나리오 요청 수 이상으로 임시 변경해야 한다. 인증·사용자 다양성 측정에는 사용할 수 없지만 크롤링 CPU/RSS, queue와 일반 API 격리 측정에는 사용할 수 있다.
- 단일 사용자 모드에서 `EXPAND_SINGLE_USER_URLS=YES`를 사용하면 승인 URL에 `nf84_request_id` query를 붙여 요청 수만큼 고유 job을 만든다. 외부 사이트가 이 query를 무시해도 되는지 확인한 URL에서만 사용한다.
- 실제 실행에는 `EXPECTED_RESULTS_FILE`이 필수다. 원본 페이지에서 확인한 `title`, `listedPrice`, `imageUrl`이 세 값 모두 정확히 일치해야 성공으로 집계되며 하나라도 다르면 실행이 실패한다.

## 사전 검증

```bash
cd load-tests/shopping-import
npm test
npm run dry-run
```

`urls.example.txt`를 복사해 승인된 고정 URL 파일을 만든다. 같은 URL별 원본 기대값은 아래 형식의 JSON 배열로 별도 저장한다. 토큰이나 개인정보는 파일에 넣지 않는다.

```json
[
  {
    "url": "https://shop.example/products/1",
    "title": "원본 상품명",
    "listedPrice": 125000,
    "imageUrl": "https://images.example/products/1.jpg"
  }
]
```

## QA reviewer 토큰 준비

QA 서버에는 비밀값으로 보호된 고정 reviewer 계정이 있다. 다음 스크립트는 SSH 서버 안에서 reviewer secret을 읽어 JWT를 발급하며, secret 자체는 로컬이나 출력에 노출하지 않는다.

```bash
cd load-tests/shopping-import
./fetch-qa-reviewer-token.sh
```

기본 출력은 Windows 공유 드라이브가 아닌 WSL 홈의 `$HOME/.secrets/wigul-nf84-qa-reviewer.token`이며 권한은 `600`이다. 발급된 access token의 기본 유효기간은 2시간이므로 실제 실행 직전에 발급한다. 테스트 후 파일을 삭제한다. 실행기는 토큰 파일에 group/other 권한이 있으면 거부한다.

단일 reviewer 계정으로 비동기 테스트를 실행하기 전에는 다음 명령으로 QA의 사용자별 상한을 101, worker 동시성을 3으로 임시 적용하고 QA 배포 workflow가 성공할 때까지 기다린다.

```bash
./configure-qa-single-user-mode.sh apply
```

스크립트는 `.env.systemd`를 권한 `600`의 백업 파일로 보존하고 GitHub Actions QA 배포를 요청한다. 테스트 종료 후에는 실패 여부와 무관하게 반드시 복구 배포를 실행한다.

```bash
./configure-qa-single-user-mode.sh restore
rm -f "$HOME/.secrets/wigul-nf84-qa-reviewer.token"
```

로컬 SSH 키나 토큰 복사가 어려우면 GitHub Actions에서 다음 순서로 실행한다.

1. async 측정이면 `Configure NF-84 QA Load Test` workflow를 `action=apply`로 실행하고 QA 배포 성공을 확인한다. sync 측정은 이 단계가 필요 없다.
2. `Run NF-84 QA Load Test` workflow에 scenario, sync/async, 승인된 상품 URL과 원본 기대값 JSON을 입력한다. `backpressure + sync`는 reviewer 토큰 하나로 약 100개의 동기 크롤링 요청을 직접 발생시킨다.
3. workflow가 서버 내부 reviewer secret으로 access token을 발급하고, 서버 지표와 부하 결과를 artifact로 저장한다.
4. async 실행은 성공·실패와 관계없이 사용자별 상한을 자동 복구하고 QA 재배포까지 기다린다.

workflow 로그와 artifact에는 reviewer secret, access token, 실제 URL 값이 저장되지 않는다. artifact에는 정리된 결과 JSON과 서버 지표 CSV만 7일간 보관한다.

## 실행 예시

동기 baseline:

```bash
CONFIRM_QA_LOAD_TEST=YES \
ACCESS_TOKEN='<QA access token>' \
URL_FILE='./urls.qa.txt' \
EXPECTED_RESULTS_FILE='./expected-results.qa.json' \
SCENARIO=baseline \
IMPORT_MODE=sync \
node run.mjs
```

비동기 baseline:

```bash
CONFIRM_QA_LOAD_TEST=YES \
ACCESS_TOKEN='<QA access token>' \
URL_FILE='./urls.qa.txt' \
EXPECTED_RESULTS_FILE='./expected-results.qa.json' \
SCENARIO=baseline \
IMPORT_MODE=async \
node run.mjs
```

단일 QA reviewer 계정을 사용하는 비동기 baseline:

```bash
CONFIRM_QA_LOAD_TEST=YES \
ACCESS_TOKEN_FILE="$HOME/.secrets/wigul-nf84-qa-reviewer.token" \
SINGLE_USER_MODE=YES \
EXPAND_SINGLE_USER_URLS=YES \
EXPECTED_MAX_ACTIVE_PER_USER=101 \
URL_FILE='./urls.qa.txt' \
EXPECTED_RESULTS_FILE='./expected-results.qa.json' \
SCENARIO=baseline \
IMPORT_MODE=async \
node run.mjs
```

backpressure 실행에는 위 설정에 `SCENARIO=backpressure`와 `CONFIRM_EXTERNAL_TRAFFIC=YES`를 추가한다. 단일 사용자 결과는 실제 사용자 100명의 인증·데이터 분산을 재현하지 않으므로 결과 보고서에 해당 한계를 남긴다.

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
- 완료된 각 작업의 상품명·가격·대표 이미지가 원본 기대값과 정확히 일치하는가

목표를 만족하면 ADR-001을 유지하고 worker와 polling 권장값을 기록한다. 미달이면 crawler 프로세스 분리, 제한 병렬화, 외부 broker 순으로 재검토한다.
