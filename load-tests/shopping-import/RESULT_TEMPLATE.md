# NF-84 쇼핑 링크 비동기 전후 부하 테스트 결과

- 실행일:
- QA backend commit:
- QA VM 사양:
- URL 세트 설명:
- 테스트 계정 수:
- 실행자:

## 실행 조건

| 항목 | 동기 | 비동기 |
| --- | --- | --- |
| 시나리오 | | |
| 전체/일반/크롤링 VU | | |
| ramp-up / duration | | |
| worker 설정 | 해당 없음 | |
| polling 간격 | 해당 없음 | |
| 인증 사용자 구성 | | |
| 임시 사용자별 활성 상한 | 해당 없음 | |

## 클라이언트 측 결과

| 지표 | 동기 | 비동기 | 변화율 |
| --- | ---: | ---: | ---: |
| 일반 API p50 | | | |
| 일반 API p95 | | | |
| 일반 API p99 | | | |
| 일반 API 오류율 | | | |
| import 제출 p95 | | | |
| queue wait p95 | 해당 없음 | | |
| 처리시간 p95 | import 응답에 포함 | | |
| 전체 완료시간 p95 | | | |
| 성공 / 실패 / timeout | | | |
| 202 / 429 | 해당 없음 | | |

## 서버 측 결과

| 지표 | 동기 | 비동기 | 변화율 |
| --- | ---: | ---: | ---: |
| backend CPU 최대 | | | |
| backend RSS 최대 | | | |
| Chromium CPU 최대 | | | |
| Chromium RSS 최대 | | | |
| PENDING 최대 | 해당 없음 | | |
| RUNNING 최대 | 해당 없음 | | |
| 비정상 재시작 / OOM | | | |

## 장애·복구 확인

- 외부 timeout/429/5xx 시 다른 API 영향:
- 작업 처리 중 서버 재시작 후 PENDING/RUNNING 복구:
- stale 재시도 및 최대 시도 초과 처리:

## 결론

- 품질 목표 충족 여부:
- 권장 worker 수:
- 권장 polling 간격:
- queue/user 상한 조정 필요 여부:
- 비용 영향:
- 단일 reviewer 계정 사용 여부와 측정 한계:

## ADR-001 판단

- [ ] 유지: PostgreSQL queue + 단일 worker가 현재 목표를 충족함
- [ ] 재검토: worker 프로세스 분리 필요
- [ ] 재검토: 독립 Playwright 제한 병렬화 필요
- [ ] 재검토: 외부 broker 필요

근거:
