# job polling 지연 차이 원인 검증

## 결론

앞선 별도 JVM·짧은 준비 호출 비교에서 관찰한 p95 +0.032~0.107ms는 통제된 교차 비교에서 재현되지 않았다. **측정 조건의 차이가 크게 작용했다는 추론**이 가장 잘 맞는다. 워밍업/JIT/OS/DB 상태를 각각 독립 조작한 실험은 아니므로 특정 요소를 확정 원인으로 지목하지 않는다.

현재 코드의 1~3μs 수준 추가 비용이 전혀 없다고 증명한 것도 아니다. 동일 코드 대조군의 변동 규모와 비교할 때 앞선 수치만으로 회귀를 판정할 근거는 부족하다. 사용자가 선택한 로컬 비교 범위에서는 기존 지연 의심에 대한 병합 보류를 해제한다. 운영 부하·전체 API·등록 처리량을 보장하는 판정은 아니다.

## 확인된 사실

- `LegacyJobReadPath`의 get/readResult/personalize/readError는 `f565eea`의 메서드 본문과 정확히 일치한다. 테스트 전용 복사이며 production 코드는 이번 원인 검증에서 바꾸지 않았다.
- 이전 get과 현재 `ShoppingImportJobQueries.findOwned`의 SQL은 공백 정규화 후 동일하다. 같은 JdbcTemplate, ObjectMapper, 커넥션 풀, PostgreSQL 컨테이너와 행을 사용한다.
- 현재 서비스와 조회 bean은 실제 Spring AOP proxy가 아님을 테스트에서 확인한다. 현재 조회 지연을 트랜잭션 프록시 탓으로 설명할 수 없다. 예전 통합 저장소의 프록시 비용은 별도로 정량화하지 않았다.
- 완료 job은 SHARE 요청 URL과 브랜드 개인화, item/saveRequest JSON 처리를 포함하며 이전·현재 응답 일치를 검증한다.
- 최초 신규 fixture 실행은 completed_at 누락으로 DB 제약에 실패했다. fixture 수정 뒤 아래 3회 실험을 실행했다. 운영 코드 결함이 아니며 실패 실행의 측정 결과는 없다.

## 실험 설계와 결과

독립 JVM 3회. 각 JVM에서 pending, completed, 동일 코드 대조군(legacy/legacy)을 순서대로 비교한다. 각 시나리오는 경로별 2,000회 준비 호출 후 20블록 × 경로별 500회 측정한다. 호출 쌍마다 AB/BA 순서를 번갈아 바꾸고 블록 시작 순서도 바꾼다. 총 측정 180,000회, 준비 호출 36,000회다.

아래 값은 **같은 블록의 candidate p95 − baseline p95 차이 20개의 중앙값**이다. 전체 호출을 합친 p95나 신뢰구간이 아니다. 직렬 호출이며 블록 간 독립성도 보장하지 않으므로 통계적 동등성 검정 통과라고 표현하지 않는다.

| JVM 실행 | pending 차이(ms) | completed 차이(ms) | 동일 코드 대조군 차이(ms) |
|---|---:|---:|---:|
| 1 | +0.002792 | -0.000897 | -0.006132 |
| 2 | +0.002886 | -0.000114 | +0.001771 |
| 3 | +0.001105 | -0.003310 | -0.000743 |

pending의 작은 양수 차이는 3회 모두 남았으므로 완전한 무비용이라고 주장하지 않는다. completed에는 같은 방향의 악화가 없었고 대조군에서도 수 μs 차이가 발생했다. 원래 의심한 수십~100μs의 회귀는 재현되지 않았다.

## 재현 자료

후속 전체 회귀 `test jacocoTestReport bootJar` 성공: 720개, 실패·오류 0, 건너뜀 12(기존 9 + 선택 성능 검사 3). 선택 교차 검사는 위와 같이 별도 JVM 3회에서 실제 실행했다.

- [JVM 1 원시 블록 결과](performance/JOB_PAIRED_fork1_2026-09-08.csv)
- [JVM 2 원시 블록 결과](performance/JOB_PAIRED_fork2_2026-09-08.csv)
- [JVM 3 원시 블록 결과](performance/JOB_PAIRED_fork3_2026-09-08.csv)
- `ShoppingImportJobPairedBenchmarkTest`: 선택 실행. 기본 CI에서는 건너뛴다.
- `scripts/analyze-job-paired.cjs`: 블록별 차이와 중앙값 재계산.

```bash
RUN_BACKEND_BENCHMARK=true ./gradlew test --tests '*ShoppingImportJobPairedBenchmarkTest' --rerun-tasks --no-daemon
node scripts/analyze-job-paired.cjs build/reports/job-paired-benchmark.csv
```

각 JVM 결과를 별도 파일로 보존한 뒤 다음 실행을 시작한다. 다른 부하 시험과 동시에 실행하지 않는다. 이전 측정 자료는 삭제하지 않았다.
