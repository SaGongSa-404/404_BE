# NF-49 JaCoCo Coverage Baseline

## 목적

NF-49에서는 테스트 커버리지 기준선을 확인할 수 있도록 JaCoCo 리포트 생성을 추가한다.
이번 범위에서는 커버리지 미달 시 빌드를 실패시키는 gate는 적용하지 않는다.

## 실행

```bash
./gradlew test jacocoTestReport
```

## 산출물

- HTML: `build/reports/jacoco/test/html/index.html`
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`
- CI artifact: `jacoco-report`

## 최초 기준선

`./gradlew test jacocoTestReport --console=plain` 기준:

| Counter | Covered / Total | Coverage |
| --- | ---: | ---: |
| Instruction | 12,138 / 16,173 | 75.05% |
| Branch | 548 / 970 | 56.49% |
| Line | 2,239 / 2,962 | 75.59% |
| Complexity | 758 / 1,249 | 60.69% |
| Method | 578 / 763 | 75.75% |
| Class | 196 / 218 | 89.91% |

## 후속 검토

- NF-38 회귀 테스트 대상 패키지의 라인/브랜치 커버리지 확인
- 80% 기준 적용 여부 검토
- 필요 시 `jacocoTestCoverageVerification` 기준 추가
