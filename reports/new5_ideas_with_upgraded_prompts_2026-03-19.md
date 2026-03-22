# 개선된 프롬프트 기반 신규 5개 아이디어 실험

작성일: 2026-03-19

실행 방식:
- 기존 3~10회차 분석을 바탕으로 개선된 1번/2번/3번 프롬프트를 적용했다.
- 이번 실험은 신규 5개 아이디어에 대해 `1번 원안 -> 2번 검증 -> 3번 보강 -> 1번 수정안` 1회 완전 루프를 우선 수행했다.
- 목적은 프롬프트 개선 후 아이디어가 더 빨리 좁혀지고, `살릴 것`과 `죽일 것`이 더 선명해지는지 확인하는 것이다.

## 최종 요약

최종 생존 결과:
1. `CleanQC Retainer`
2. `DentalLab Relay`
3. `VetRx Queue`
4. `TaxClose Chaser`
5. `BrokerPacket Shield` -> 보류

공통 관찰:
- 개선된 1번 프롬프트는 처음부터 타겟 직무와 반복 pain을 더 선명하게 잡았다.
- 개선된 2번 프롬프트는 `왜 안 살 수도 있는지`와 `생존 판정`이 확실해져서, 약한 아이디어를 억지로 살리지 않았다.
- 개선된 3번 프롬프트는 `첫 고객 확보 채널`과 `6주 실험 KPI`를 강제하면서 전략이 훨씬 실무적으로 바뀌었다.

---

## 1. CleanQC Retainer

### 1번 원안
- 아이디어명: `CleanQC Retainer`
- 한 문장 pain: 상업용 청소업체는 현장 품질 점검 결과가 나빠져도 시정조치가 느려서 계정을 잃는다.
- 구체 타겟: 20~200개 사이트를 관리하는 상업용 청소업체의 운영매니저, QA 슈퍼바이저, 계정관리자
- 반복 사용 이유: 현장 점검, 클레임, 재점검, 고객 보고가 매주 반복된다.
- 돈을 낼 이유: 고객 이탈 1건의 손실이 크고, 품질 클레임 대응 시간이 바로 계약 유지와 연결된다.
- 의도적으로 버린 범위: 인력 스케줄링, 급여, 청구, 전체 janitorial ERP
- 6주 파일럿형 MVP: 점검 체크리스트, 사진 증빙, 시정조치 큐, 계정 위험 경고, 고객 공유 리포트

### 2번 검증
- 유사 서비스/대체재: [OrangeQC](https://www.orangeqc.com/), [Clean Smarts](https://www.cleansmarts.com/features/inspections), [Smart Inspect](https://mysmartinspect.com/), [FacilityCare IQ](https://facilitycareiq.com/), [MyCCSApp](https://myccsapp.com/industries/janitorial-software/)
- 수요가 강한 근거: 청소 품질 점검 소프트웨어 카테고리 자체가 오래 존재하고, 고객 공유용 리포트와 사진 증빙이 핵심 가치로 반복적으로 제시된다.
- 반복 사용 가능성 평가: 매우 높음
- 돈을 낼 이유: 계정 유지, 클레임 감소, 현장 감독 시간 절감
- 돈을 안 낼 이유: 이미 inspection 기능이 포함된 janitorial 소프트웨어를 쓰는 업체는 별도 구매를 꺼릴 수 있다.
- 치명적 리스크 3개:
  - inspection app과 차별화가 약하면 대체된다.
  - 현장 직원 입력 저항이 생길 수 있다.
  - 고객 포털이 약하면 retention value가 체감되지 않는다.
- 생존 판정: 살림
- 3번 에이전트로 넘길 메모: `점검 앱`이 아니라 `계정 이탈 방지와 시정조치 속도 개선`으로 재정의해야 한다.

### 3번 보강
- 유지할 강점: 품질 실패가 계약 유지와 직접 연결된다.
- 버릴 요소: 청소업 운영 전반, 배치/급여, 범용 inspection platform 포지션
- 차별화 포인트:
  - `낮은 점수 -> 시정조치 -> 고객 회신` 한 흐름을 한 화면으로 묶는다.
  - 계정별 위험 점수와 미해결 이슈 SLA를 보여준다.
  - 고객에게 보내는 증빙 리포트를 자동 생성한다.
- 첫 고객 확보 채널: 청소업 컨설턴트, BSC 대표 커뮤니티, 이미 inspection을 쓰지만 계정 이탈이 잦은 업체 직접 영업
- 6주 실험 KPI:
  - 48시간 내 시정조치 완료율
  - 재점검 통과율
  - 고객 불만 티켓 감소율
  - 계정 매니저 보고 시간 감소
- 수익화 방향: 사이트 수 기반 월 구독 + 고객 포털/리포트 기능 업셀
- 살리는 조건 또는 포기 이유: `inspection`이 아니라 `retention workflow`로 세일즈 메시지를 바꾸면 살릴 수 있다.
- 1번 에이전트에게 줄 질문:
  - 첫 타겟을 병원/오피스/학교 중 어디로 고정할 것인가?
  - 고객이 가장 싫어하는 지연은 점검 자체인가, 시정조치 후속인가?

### 1번 수정안
- 최종 형태: `상업용 청소업체의 계정 이탈 방지용 품질 시정조치 OS`
- 지킨 것: 현장 품질, 사진 증빙, 반복 점검
- 버린 것: 청소업 ERP, 인력 관리, 청구
- 6주 파일럿 MVP:
  - 실패 점검 큐
  - 시정조치 SLA
  - 사진 전후 증빙
  - 고객 공유 리포트
  - 계정 위험 라벨

---

## 2. DentalLab Relay

### 1번 원안
- 아이디어명: `DentalLab Relay`
- 한 문장 pain: 치과는 외부 기공소가 여러 곳일수록 케이스 지연과 리메이크를 전화/카톡으로 쫓느라 체어타임을 잃는다.
- 구체 타겟: 월 150건 이상 보철/교정/임플란트 케이스를 돌리는 3~20체어 치과의 실장, 코디네이터, 원장
- 반복 사용 이유: 기공 케이스는 매일 생성되고, due date와 상태 확인이 반복된다.
- 돈을 낼 이유: 리메이크, 지연, 환자 reschedule 비용이 크다.
- 의도적으로 버린 범위: 치과 PMS, 청구/수납, 기공소 생산관리 전체
- 6주 파일럿형 MVP: 케이스 접수, multi-lab due date 보드, 지연 위험 경고, remake 원인 기록, 환자 일정 영향 표시

### 2번 검증
- 유사 서비스/대체재: [CloudLab](https://cloudlab.dental/), [LabSquare](https://labsquare.app/), [DentConnect](https://www.thedentconnect.com/), [Dentatrak](https://dentatrak.com/), [Dental Lab Guru](https://www.dentallabguru.com/)
- 수요가 강한 근거: 기공소용 case tracking과 dentist portal 소프트웨어가 이미 다수 존재하고, `real-time case visibility`와 `case tracking`을 핵심 가치로 제시한다.
- 반복 사용 가능성 평가: 높음
- 돈을 낼 이유: due date miss 감소, 환자 reschedule 감소, 실장/데스크 문의 시간 감소
- 돈을 안 낼 이유: 이미 특정 기공소 포털을 쓰는 치과는 별도 도구를 싫어할 수 있다.
- 치명적 리스크 3개:
  - 기공소 소프트웨어와 기능 중복
  - 여러 기공소가 같이 써주지 않으면 네트워크 효과가 약함
  - PMS/스캐너 연동 요구가 빠르게 나올 수 있음
- 생존 판정: 조건부 살림
- 3번 에이전트로 넘길 메모: 기공소용 툴이 아니라 `치과 측 multi-lab exception queue`로 완전히 포지셔닝해야 산다.

### 3번 보강
- 유지할 강점: due date miss와 remake는 치과 운영에 직접적인 손실을 준다.
- 버릴 요소: 기공소 생산 관리, 일반 case tracking, 범용 dentist portal
- 차별화 포인트:
  - 여러 기공소를 한 화면에서 보는 치과 측 예외 큐
  - 환자 예약 일정과 기공 due date를 연결
  - 리메이크 원인을 누적해 lab별 리스크를 보여줌
- 첫 고객 확보 채널: 치과 실장 커뮤니티, 기공소 컨설턴트, multi-lab 운영 치과 직접 영업
- 6주 실험 KPI:
  - overdue case 비율
  - 케이스 상태 확인 전화/카톡 건수
  - remake 추적 누락 건수
  - 환자 reschedule 감소율
- 수익화 방향: 치과당 월 구독 + 고급 리포트/연동 업셀
- 살리는 조건 또는 포기 이유: `기공소 툴`이 아니라 `치과 측 multi-lab 예외처리 레이어`로 고정하면 살 수 있다.
- 1번 에이전트에게 줄 질문:
  - 첫 세그먼트를 보철 중심 일반치과와 교정/임플란트 치과 중 어디로 잡을 것인가?
  - 네트워크 참여 없이도 치과 단독으로 얻는 첫 가치는 무엇인가?

### 1번 수정안
- 최종 형태: `치과 실장용 multi-lab due date / remake exception OS`
- 지킨 것: due date, remake, 외부 기공소 커뮤니케이션
- 버린 것: 기공소 ERP, 생산관리, 범용 포털
- 6주 파일럿 MVP:
  - 케이스 due date 보드
  - 지연/리메이크 위험 라벨
  - 환자 일정 영향 큐
  - lab별 이슈 로그
  - 실장용 follow-up 템플릿

---

## 3. VetRx Queue

### 1번 원안
- 아이디어명: `VetRx Queue`
- 한 문장 pain: 동물병원은 외부 약국과 원내 조제를 오가는 처방 리필 요청이 전화, 팩스, 포털에 흩어져 승인 대기열이 꼬인다.
- 구체 타겟: 하루 10건 이상 처방 리필 또는 chronic med 승인 요청을 처리하는 중대형 동물병원의 수의사, 테크니션 리드, 리셉션 매니저
- 반복 사용 이유: 만성약 리필, 외부 약국 승인, 예외 승인 요청이 거의 매일 반복된다.
- 돈을 낼 이유: staff hour 절감, 재확인 전화 감소, 승인 turnaround 개선
- 의도적으로 버린 범위: PIMS 전체, 예약/결제, full e-prescribing 플랫폼
- 6주 파일럿형 MVP: refill inbox, approval rules, outside pharmacy exception queue, owner follow-up status, ready-for-pickup 알림

### 2번 검증
- 유사 서비스/대체재: [InstinctScripts](https://www.instinctscripts.vet/), [VetWay](https://www.vetway.com/veterinary-eprescribing), [PawScripts](https://www.getpawscripts.com/), [Oliver](https://getoliver.com/pharmacy), [Vetsource Prescription Management](https://vetsource.com/products/prescription-management/)
- 수요가 강한 근거: 이미 처방 승인/리필/외부 약국 연동을 다루는 vet software가 많고, 실제 운영 효율을 전면에 내세운다.
- 반복 사용 가능성 평가: 높음
- 돈을 낼 이유: 리필 승인 시간 절감, staff stress 감소, 고객 응대 개선
- 돈을 안 낼 이유: 기존 PIMS 또는 처방 솔루션에 묻혀 있으면 독립 제품으로 안 살 수 있다.
- 치명적 리스크 3개:
  - PIMS 통합 없이는 수작업이 남는다.
  - e-prescribing 플레이어가 이미 강하다.
  - 지역 규제/약국 연동 요건이 복잡하다.
- 생존 판정: 조건부 살림
- 3번 에이전트로 넘길 메모: 일반 처방툴이 아니라 `outside pharmacy / refill exception queue`만 잡아야 한다.

### 3번 보강
- 유지할 강점: 처방 리필 승인 지연은 실제로 staff burden과 client dissatisfaction를 만든다.
- 버릴 요소: full e-prescribing, 예약/결제, 전체 병원 운영툴 포지션
- 차별화 포인트:
  - 외부 약국/원내 조제/리필 예외 요청을 한 큐로 통합
  - doctor approval rules와 technician pre-check를 분리
  - `왜 아직 승인 안 됐는지`를 owner-facing 상태로 보여줌
- 첫 고객 확보 채널: 중대형 동물병원 체인, veterinary ops 컨설턴트, PIMS 파트너 앱마켓
- 6주 실험 KPI:
  - refill turnaround time
  - pharmacy callback 건수
  - 승인 누락 건수
  - staff 처리 시간 절감
- 수익화 방향: 병원당 월 구독 + 처방량 구간 과금 또는 PIMS add-on 과금
- 살리는 조건 또는 포기 이유: PIMS를 대체하지 않고 `리필 승인 예외 큐`에 집중하면 살릴 수 있다.
- 1번 에이전트에게 줄 질문:
  - 첫 시장을 원내 조제가 많은 병원과 외부 약국 비중이 높은 병원 중 어디로 잡을 것인가?
  - 규제 부담이 큰 국가 대신 초기 런칭 지역을 어떻게 좁힐 것인가?

### 1번 수정안
- 최종 형태: `동물병원 처방 리필 승인 예외처리 OS`
- 지킨 것: refill queue, doctor approval, pharmacy handoff
- 버린 것: full e-prescribing, general client engagement
- 6주 파일럿 MVP:
  - refill inbox
  - tech pre-check rules
  - doctor approval queue
  - owner status updates
  - callback / delay dashboard

---

## 4. TaxClose Chaser

### 1번 원안
- 아이디어명: `TaxClose Chaser`
- 한 문장 pain: SMB 회계/기장 대행사는 월마감에 필요한 증빙과 확인 답변을 클라이언트에게 제때 못 받아 close가 계속 밀린다.
- 구체 타겟: 월 20~200개 고객사를 맡는 회계/기장 대행사의 파트너, 팀장, 시니어 북키퍼
- 반복 사용 이유: 월말 close와 자료 요청은 고객사마다 매달 반복된다.
- 돈을 낼 이유: close lead time 단축, 북키퍼 생산성 상승, 고객사 커뮤니케이션 누락 감소
- 의도적으로 버린 범위: full practice management, 세무 신고 전반, 문서 보관 전부
- 6주 파일럿형 MVP: pre-close request queue, client-specific reminder flows, exception bucket, response status board

### 2번 검증
- 유사 서비스/대체재: [Karbon month-end close template](https://karbonhq.com/templates/month-end-close-auto-send-core), [SmartVault](https://www.smartvault.com/solutions/industries/accounting-tax-firms/), [TaxCaddy](https://tax.thomsonreuters.com/en/products/taxcaddy), [Neudash](https://neudash.com/solutions/accounting/month-end-close-automation), [Intuit document management](https://accountants.intuit.com/tax-accounting-workflow-software/tax-form-management-system/)
- 수요가 강한 근거: month-end close automation과 client document chase는 이미 별도 카테고리와 템플릿이 존재하고, close lead time 단축이 명확한 pain으로 제시된다.
- 반복 사용 가능성 평가: 높음
- 돈을 낼 이유: close 속도, staff time, client response 관리
- 돈을 안 낼 이유: 이미 Karbon/SmartVault 같은 practice stack을 쓰는 firm은 새 제품을 추가하지 않을 수 있다.
- 치명적 리스크 3개:
  - practice management suites와 겹칠 수 있다.
  - 고객사도 새로운 포털을 싫어할 수 있다.
  - 한국 시장에선 카카오톡/메일 혼합 워크플로가 더 강할 수 있다.
- 생존 판정: 조건부 살림
- 3번 에이전트로 넘길 메모: 포털 SaaS가 아니라 `client chase automation layer`로 가야 한다.

### 3번 보강
- 유지할 강점: 월말 close delay는 반복적이고 비용이 바로 내부 생산성으로 계산된다.
- 버릴 요소: full PM suite, 세무신고 전체, 문서 영구보관
- 차별화 포인트:
  - 고객별 close exception list를 자동으로 생성
  - 카카오톡/이메일/포털 응답을 한 큐에서 추적
  - `누가 막고 있는지`를 팀장에게 보여줌
- 첫 고객 확보 채널: 소형 회계법인/기장 대행사 네트워크, bookkeeping communities, accounting consultants
- 6주 실험 KPI:
  - close lead time
  - Day-1 missing item count
  - 고객 응답 속도
  - 북키퍼 수작업 추적 시간
- 수익화 방향: 고객사 수 기반 월 구독 + 고급 reminder automation 업셀
- 살리는 조건 또는 포기 이유: 한국형 `카카오/메일 chase layer`로 좁히면 살 수 있고, full PM으로 가면 바로 묻힌다.
- 1번 에이전트에게 줄 질문:
  - 첫 고객군을 회계법인보다 기장 대행사로 더 좁힐 것인가?
  - 포털 강제 대신 기존 메일/카카오 흐름 위에 올라가는 구조가 가능한가?

### 1번 수정안
- 최종 형태: `월마감 자료 요청 지연을 줄이는 회계 대행사용 chase automation layer`
- 지킨 것: month-end close, document chase, client reminder
- 버린 것: full practice management, full DMS
- 6주 파일럿 MVP:
  - missing items queue
  - client reminder automations
  - response status board
  - exception owner tracking
  - team lead escalation view

---

## 5. BrokerPacket Shield

### 1번 원안
- 아이디어명: `BrokerPacket Shield`
- 한 문장 pain: 중소 freight broker는 신규 carrier packet, 보험 확인, fraud re-vetting을 여러 툴과 메일로 돌리다가 사기와 onboarding 지연을 동시에 맞는다.
- 구체 타겟: 전담 fraud/compliance 팀이 없는 소형 freight broker/3PL의 carrier onboarding 담당자와 ops lead
- 반복 사용 이유: 신규 carrier onboarding과 재검증이 계속 반복된다.
- 돈을 낼 이유: fraud loss 방지, onboarding 속도 개선
- 의도적으로 버린 범위: full TMS, load booking, pricing
- 6주 파일럿형 MVP: onboarding packet intake, re-vet queue, carrier change alerts, broker action queue

### 2번 검증
- 유사 서비스/대체재: [Highway](https://highway.com/press-releases/highway-and-carrier1-partner-to-streamline-carrier-onboarding-and-security), [Descartes MyCarrierPortal](https://www.mycarrierportal.com/services/carrier-onboarding-service), [RMIS](https://www.prweb.com/releases/RMIS_Announces_Its_Onboarding_and_Compliance_Management_Services_Now_Integrate_with_RevenovaTMS/prweb16465137.htm), [Carrier Assure](https://www.carrierassure.com/), [MyCarrierPackets/MyCarrierPortal](https://www.mycarrierportal.com/wp-content/uploads/2025/04/9c3769_76abcf84482a46beb71924e39de94f8d.pdf)
- 수요가 강한 근거: carrier onboarding/fraud 방지 카테고리는 매우 크고, 실제 산업에서 dedicated tools가 강하게 자리잡아 있다.
- 반복 사용 가능성 평가: 높음
- 돈을 낼 이유: fraud loss는 매우 크고 onboarding 속도도 중요하다.
- 돈을 안 낼 이유: 이미 Highway/RMIS/MyCarrierPortal 같은 incumbents가 너무 강하고 데이터 moat가 깊다.
- 치명적 리스크 3개:
  - 데이터/identity network moat 부족
  - broker가 새 툴을 도입할 이유가 약함
  - compliance 신뢰를 확보하기 어렵다
- 생존 판정: 보류
- 3번 에이전트로 넘길 메모: 정면승부는 피해야 하고, 별도 wedge가 안 보이면 접는 편이 낫다.

### 3번 보강
- 유지할 강점: pain 자체는 매우 강하다.
- 버릴 요소: full carrier onboarding 경쟁, fraud master-data 경쟁, 범용 broker platform 포지션
- 차별화 포인트:
  - 없음에 가깝다. 현 시점에서는 정면 경쟁이 불리하다.
  - 굳이 살리려면 `한국 포워더/북미 truck brokerage 사이 cross-border carrier doc handoff` 같은 매우 좁은 niche가 필요하다.
- 첫 고객 확보 채널: niche cross-border broker가 아니면 현실적으로 불명확
- 6주 실험 KPI:
  - cross-border doc rejection rate
  - manual review time
  - onboarding completion speed
- 수익화 방향: niche compliance add-on 과금 외에는 뚜렷하지 않음
- 살리는 조건 또는 포기 이유: 강력한 niche 없이 일반 freight broker onboarding 시장으로 가면 포기하는 편이 맞다.
- 1번 에이전트에게 줄 질문:
  - 정말 cross-border niche에서만 출발할 의향이 있는가?
  - 네트워크/데이터 moat 없이 왜 broker가 기존 툴을 버리고 이걸 써야 하는가?

### 1번 수정안
- 최종 형태: `일반 freight broker onboarding 시장에서는 보류`
- 지킨 것: fraud / onboarding pain 인식
- 버린 것: 범용 broker compliance OS
- 6주 파일럿 MVP: 없음. niche를 다시 정의하기 전까지 보류

---

## 최종 결론

가장 바로 다음 실험으로 넘길 신규 아이디어는 아래 3개다.
1. `CleanQC Retainer`
2. `DentalLab Relay`
3. `VetRx Queue`

조건부로 더 좁혀서 볼 수 있는 후보:
- `TaxClose Chaser`

이번 라운드에서 죽이거나 보류하는 게 맞는 후보:
- `BrokerPacket Shield`

## 주요 참고 링크

- Commercial cleaning software: [OrangeQC](https://www.orangeqc.com/), [Clean Smarts](https://www.cleansmarts.com/features/inspections), [Smart Inspect](https://mysmartinspect.com/), [FacilityCare IQ](https://facilitycareiq.com/)
- Dental lab workflow software: [CloudLab](https://cloudlab.dental/), [LabSquare](https://labsquare.app/), [DentConnect](https://www.thedentconnect.com/), [Dentatrak](https://dentatrak.com/)
- Veterinary prescription/refill tools: [InstinctScripts](https://www.instinctscripts.vet/), [VetWay](https://www.vetway.com/veterinary-eprescribing), [PawScripts](https://www.getpawscripts.com/), [Oliver](https://getoliver.com/pharmacy), [Vetsource](https://vetsource.com/products/prescription-management/)
- Accounting close / document chase: [Karbon](https://karbonhq.com/templates/month-end-close-auto-send-core), [SmartVault](https://www.smartvault.com/solutions/industries/accounting-tax-firms/), [TaxCaddy](https://tax.thomsonreuters.com/en/products/taxcaddy), [Neudash](https://neudash.com/solutions/accounting/month-end-close-automation)
- Freight broker onboarding/fraud tools: [Highway](https://highway.com/press-releases/highway-and-carrier1-partner-to-streamline-carrier-onboarding-and-security), [Descartes MyCarrierPortal](https://www.mycarrierportal.com/services/carrier-onboarding-service), [RMIS](https://www.prweb.com/releases/RMIS_Announces_Its_Onboarding_and_Compliance_Management_Services_Now_Integrate_with_RevenovaTMS/prweb16465137.htm), [Carrier Assure](https://www.carrierassure.com/)
