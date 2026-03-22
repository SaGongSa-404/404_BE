# 10회차 종합본 기반 점수표 재산정

작성일: 2026-03-19

기준 문서:
- `reports/loop10/aggregate_10cycles_report.md`

평가 방식:
- 각 항목은 10점 만점이다.
- `세일즈 난이도`, `구현 난이도`는 난도가 아니라 `유리함` 기준으로 점수화했다. 즉, 높을수록 더 유리하다.
- `최종 실행 우선도`는 지금 바로 인터뷰/파일럿/과금실험으로 넘길 가치 기준이다.

평가 항목:
1. 시장성
2. 반복 사용성
3. 지불 의사
4. 도입 속도
5. 세일즈 유리함
6. 구현 유리함
7. 한국 적합성
8. 글로벌 확장성
9. 차별화 가능성
10. 최종 실행 우선도

## 재산정 결과표

| 순위 | 아이디어 | 시장성 | 반복 사용성 | 지불 의사 | 도입 속도 | 세일즈 유리함 | 구현 유리함 | 한국 적합성 | 글로벌 확장성 | 차별화 가능성 | 최종 실행 우선도 | 총점 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | ExportDoc Copilot | 9 | 9 | 9 | 7 | 7 | 6 | 8 | 9 | 8 | 9 | 81 |
| 2 | GlobalWorker Desk | 8 | 8 | 8 | 8 | 7 | 7 | 10 | 7 | 8 | 8 | 79 |
| 3 | HakwonOps | 9 | 9 | 7 | 9 | 8 | 8 | 10 | 5 | 6 | 8 | 79 |
| 4 | ShiftGuard for Franchises | 8 | 9 | 8 | 6 | 6 | 6 | 9 | 6 | 7 | 7 | 72 |
| 5 | PostShip Recovery | 8 | 8 | 7 | 6 | 6 | 6 | 8 | 7 | 6 | 7 | 69 |
| 6 | Resident MaintOps | 8 | 8 | 7 | 5 | 5 | 6 | 9 | 5 | 6 | 6 | 65 |
| 7 | RecallOS for Dental / Aesthetic Clinics | 7 | 8 | 8 | 6 | 6 | 6 | 8 | 5 | 6 | 5 | 65 |
| 8 | Supplier CAPA Hub | 7 | 6 | 8 | 4 | 4 | 5 | 7 | 7 | 7 | 5 | 60 |
| 9 | SalesFollow OS | 6 | 7 | 6 | 5 | 5 | 6 | 6 | 6 | 5 | 5 | 57 |
| 10 | InterviewFlow | 5 | 6 | 5 | 6 | 5 | 7 | 6 | 5 | 4 | 4 | 53 |

## 순위 변동 해석

### 1. ExportDoc Copilot
- 유지 이유: `반복 빈도`, `손실 비용`, `글로벌 확장성`, `한국형 실무 UX`가 동시에 강하다.
- 점수 메모: 구현은 아주 쉽지 않지만, 그것을 상쇄하고도 남는 지불 의사와 실행 우선도가 있다.

### 2. GlobalWorker Desk
- 유지 이유: 한국 시장 적합성이 압도적으로 높고, 현장 관리자 pain이 직접적이다.
- 점수 메모: 장기 글로벌 SaaS 확장성은 ExportDoc보다 약하지만, 국내 초기 시장 진입력은 매우 좋다.

### 3. HakwonOps
- 유지 이유: 반복 사용성과 파일럿 속도는 최상급이다.
- 점수 메모: GlobalWorker Desk와 총점은 같지만, ARPU와 글로벌 확장성에서 다소 밀려 3위로 둔다.

### 4. ShiftGuard for Franchises
- 유지 이유: 본사 예산으로 팔 수 있고, 인건비/노무 리스크라는 명확한 pain이 있다.
- 점수 메모: 제품 강도는 높지만 연동과 의사결정 구조가 무거워 상위 3개보다 느리다.

### 5. PostShip Recovery
- 유지 이유: 회수 매출이 직접적이고 운영 빈도가 높다.
- 점수 메모: 다만 기존 post-purchase / OMS / WMS 플레이어의 흡수 위험이 남아 있다.

### 6. Resident MaintOps
- 변동 이유: 한국 현장 pain은 강하지만, 기존 플레이어와의 포지션 싸움과 느린 영업 구조 때문에 실행 우선도가 내려갔다.

### 7. RecallOS for Dental / Aesthetic Clinics
- 변동 이유: 객단가와 매출 회수 logic은 강하지만, 규제/개인정보/기존 PMS 공존 이슈 때문에 확장성이 제한된다.

### 8. Supplier CAPA Hub
- 변동 이유: pain과 계약 단가는 좋지만 엔터프라이즈 성격이 강하고 세일즈 사이클이 길다.

### 9. SalesFollow OS
- 변동 이유: 문제는 실재하지만 세그먼트가 좁고, CRM/노트/영업툴과 계속 비교된다.

### 10. InterviewFlow
- 유지 이유: 병목은 있으나 독립 카테고리 힘이 약하고 시즌성 영향을 크게 받는다.

## 재산정 후 실행 권고

### 바로 파일럿으로 넘길 3개
1. ExportDoc Copilot
2. GlobalWorker Desk
3. HakwonOps

### 인터뷰는 하되 MVP 착수는 한 템포 늦출 2개
1. ShiftGuard for Franchises
2. PostShip Recovery

### 추가 좁힘 없이는 보류할 5개
1. Resident MaintOps
2. RecallOS for Dental / Aesthetic Clinics
3. Supplier CAPA Hub
4. SalesFollow OS
5. InterviewFlow
