# NF-84 쇼핑 링크 비동기 전후 부하 테스트 보고서

- 상태: 실행 준비 완료, QA 측정 대기
- 기준 backend commit: `ce4c362` 이후 `develop`
- 기능 PR: #191
- 테스트 이슈: #190

## 현재까지 확인한 내용

- QA `/health`가 `UP`을 반환한다.
- 신규 `POST /api/v1/items/import-jobs`와 `GET /api/v1/items/import-jobs/{jobId}`는 배포되어 있으며 미인증 요청은 `401`로 보호된다.
- 동기/비동기 동일 조건 비교 실행기, 서버 지표 수집기, 결과 템플릿을 `load-tests/shopping-import`에 추가했다.
- 실행기는 access token과 실제 URL을 결과에 저장하지 않는다.
- 외부 사이트 보호를 위해 QA 대상 확인, 20건 초과 크롤링 추가 확인, ramp-up, placeholder URL 거부를 적용했다.
- QA reviewer 계정 하나로도 측정할 수 있도록 임시 사용자별 상한 적용·자동 복구, 서버 내부 토큰 발급, GitHub Actions 실행과 결과 artifact 수집 경로를 추가했다.

## 실제 측정 전 필요한 입력

- QA 승인된 고정 상품 URL 세트
- baseline: 서로 다른 테스트 사용자 access token 최소 4개
- peak: 서로 다른 테스트 사용자 access token 최소 7개
- backpressure: 서로 다른 테스트 사용자 access token 최소 34개
- QA VM에서 서버 지표 수집기를 실행할 터미널

서로 다른 테스트 사용자를 준비할 수 없으면 `Configure NF-84 QA Load Test`와 `Run NF-84 QA Load Test` workflow를 사용한다. 이 경로는 reviewer access token을 로그에 노출하지 않고 사용자별 활성 상한을 101로 임시 적용하며, async 실행 종료 후 원래 설정으로 복구·재배포한다. 이 결과는 인증 사용자 다양성이 아니라 크롤링 CPU/RSS, queue, 일반 API 장애 격리 판단에 사용한다.

동일 사용자는 활성 작업이 3개로 제한되고 동일 URL의 진행 중 요청은 합쳐진다. 따라서 하나의 토큰만 사용하면 100명 부하가 아니라 사용자별 제한을 측정하게 되어 비교 결과가 왜곡된다.

## 측정 결과

인증 토큰과 승인 URL이 준비된 뒤 `RESULT_TEMPLATE.md` 형식으로 동기/비동기 값을 기록한다. 현재는 실제 부하를 실행하지 않았으므로 성능 향상률이나 ADR 유지 여부를 확정하지 않는다.

## ADR 판단 상태

- ADR-001 상태: `Accepted` 유지
- 측정 결론: 대기
- 재검토 판단: 실제 동기/비동기 및 서버 지표 확보 후 결정
