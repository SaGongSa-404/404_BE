# 에이전트 평가, 프롬프트 업그레이드, 신규 아이디어 5개 실험

작성일: 2026-03-19  
기준 문서:
- `reports/idea_agent_report_3cycles_2026-03-18.md`
- `reports/loop10/agent1_idea_evolution.md`
- `reports/loop10/agent2_market_validation.md`
- `reports/loop10/agent3_strategy_refinement.md`
- `reports/loop10/aggregate_10cycles_report.md`

## 1. 에이전트 평가

### 1번 에이전트 평가

강점:
- 3회차부터 10회차까지 갈수록 아이디어를 `올인원`에서 `반복 pain이 강한 단일 워크플로우`로 잘 좁혔다.
- MVP 범위를 점점 잘라내면서 실제 파일럿 가능한 수준으로 만드는 능력이 좋았다.
- 누가 돈을 내는지, 실사용자가 누구인지 분리하는 과정이 좋았다.

약점:
- 중간 회차에서 다시 기능이 넓어지는 흔들림이 있었다.
- `무엇을 과감히 버렸는지`가 회차별로 항상 명시되지는 않았다.
- GTM 채널과 가격 구조는 3번 에이전트 도움에 의존하는 편이었다.

개선 포인트:
- 매 회차 `지킨 것 / 버린 것 / 이번 회차에 새로 추가한 것`을 강제한다.
- 6주 파일럿 기준으로 MVP를 다시 자르게 한다.
- 제품 설명보다 `한 문장 pain`과 `돈 내는 이유`를 먼저 쓰게 한다.

### 2번 에이전트 평가

강점:
- 시장에 이미 대체재가 있는지, 수요가 강한지, 반복 사용성이 있는지 판정하는 능력이 안정적이었다.
- 기존 강자와의 충돌, 연동 부담, 세일즈 무게 같은 현실 리스크를 잘 짚었다.
- 수요 강도가 높아 보이더라도 `왜 안 살 수도 있는지`를 어느 정도 잘 적어줬다.

약점:
- 꽤 많은 아이디어를 `조건부로 살릴 수 있음` 쪽으로 남겨두는 경향이 있었다.
- 로컬 시장 데이터보다 글로벌 유사 서비스 관찰에 기댄 회차가 있었다.
- `폐기` 기준이 조금 더 강해야 했다.

개선 포인트:
- 각 아이디어마다 `살림 / 조건부 살림 / 보류 / 폐기`를 강제한다.
- `안 사는 이유`를 `사는 이유`만큼 길게 쓰게 한다.
- source-backed validation을 기본으로 하되, 한국 로컬 workflow 미스매치도 반드시 따로 적게 한다.

### 3번 에이전트 평가

강점:
- 차별화 포인트를 `말빨`이 아니라 `첫 고객 확보 채널`과 `실험 KPI`로 연결한 것이 좋았다.
- 1번 에이전트가 다음 회차에 더 잘 좁힐 수 있도록 피드백 질문을 잘 남겼다.
- 수익화와 판매 채널을 아이디어 성격에 맞게 현실적으로 설계했다.

약점:
- 일부 회차에서는 차별화가 전략 문구 수준에 머무는 경우가 있었다.
- 어떤 기능을 버려야 하는지보다 무엇을 붙일지에 조금 더 치우친 구간이 있었다.
- 실험 KPI가 있어도 `첫 10명 고객` 구성이 항상 구체적이지는 않았다.

개선 포인트:
- 반드시 `버릴 요소`와 `처음 팔 채널`을 적게 한다.
- `6주 실험 KPI`를 수치로 강제한다.
- `살릴 조건 / 포기 이유`를 분명히 적게 한다.

## 2. 개선된 프롬프트

### 개선 프롬프트: 1번 에이전트

```text
당신은 개선된 1번 에이전트입니다. 역할은 수요가 강한 vertical software 아이디어를 발굴하고, 2번/3번의 피드백을 받아 더 좁고 더 잘 팔리는 형태로 진화시키는 것입니다.

규칙:
- 범용 툴, 올인원 플랫폼, 취미성 앱 금지
- 첫 문단에서 반드시 누가 / 언제 반복적으로 / 왜 돈을 내는지 / 한 문장 pain을 명시
- 매 회차마다 반드시 지킨 것 / 버린 것 / 새로 추가한 것을 분리
- MVP는 6주 파일럿 가능 범위로 제한
- 기존 상위 아이디어와 겹치지 않는 새 vertical만 허용

출력 형식:
- 아이디어명
- 한 문장 pain
- 구체적 타겟 조직/직무
- 반복 사용 이유
- 돈을 낼 이유
- 이번 회차에 의도적으로 버린 범위
- 6주 파일럿형 MVP
```

### 개선 프롬프트: 2번 에이전트

```text
당신은 개선된 2번 에이전트입니다. 역할은 시장 검증, 대체재 확인, 수요 강도 판정, 폐기 판단입니다.

규칙:
- 반드시 웹 검색 기반 검증 수행
- 각 아이디어마다 왜 살 수 있는지와 왜 안 살 수도 있는지를 둘 다 강하게 적기
- 한국 로컬 시장 적합성과 글로벌 대체재 상황을 분리해서 보기
- 최종적으로 살림 / 조건부 살림 / 보류 / 폐기 중 하나를 명시

출력 형식:
- 아이디어명
- 유사 서비스/대체재 3~6개
- 수요가 강한 근거
- 반복 사용 가능성 평가
- 돈을 낼 이유 / 안 낼 이유
- 치명적 리스크 3개
- 생존 판정
- 3번 에이전트로 넘길 메모
```

### 개선 프롬프트: 3번 에이전트

```text
당신은 개선된 3번 에이전트입니다. 역할은 차별화, 초기 수요 확보, 수익화 전략 보강입니다.

규칙:
- 예쁜 문구보다 첫 고객 확보 경로와 6주 실험 KPI를 우선
- 버릴 요소와 붙일 요소를 분리
- 조건부 살림 아이디어는 살리는 조건을, 보류/폐기 아이디어는 포기 이유를 분명히 적기
- 1번 에이전트가 다음 회차에 바로 수정할 수 있도록 질문을 2개 이상 남기기

출력 형식:
- 아이디어명
- 유지할 강점
- 버릴 요소
- 차별화 포인트
- 첫 고객 확보 채널
- 6주 실험 KPI
- 수익화 방향
- 살리는 조건 또는 포기 이유
- 1번 에이전트에게 줄 질문 2개 이상
```

## 3. 개선 프롬프트 기준 신규 아이디어 5개 실험

가정:
- 이번 신규 실험은 `개선 프롬프트가 실제로 더 선명한 vertical wedge를 만들 수 있는지`를 보는 1차 라운드다.
- 따라서 5개 모두에 대해 `1번 원안 -> 2번 검증 -> 3번 보강 -> 1번 수정안` 1회 루프를 먼저 수행했다.
- 기존 10개 아이디어와 중복되는 도메인은 제외했다.

### 1. SampleLoop for K-Beauty OEM/ODM

`개선 1번 원안`
- 한 문장 pain: K-뷰티 브랜드와 OEM/ODM 공장 사이의 샘플 수정, 패키지 proof, 승인 코멘트가 카카오톡/메일/엑셀에 흩어져 출시 일정이 계속 밀린다.
- 타겟: 한국 화장품 OEM/ODM 공장 영업 PM, 인디 브랜드 상품기획 PM
- 반복 사용 이유: SKU마다 샘플, 패키지, 문구 수정과 승인 루프가 반복된다.
- 돈을 낼 이유: 출시 지연과 재작업 비용이 바로 돈이기 때문이다.
- 의도적으로 버린 범위: 전체 PLM, 원가 계산, 생산관리
- 6주 MVP: 샘플 버전 관리, 코멘트 추적, 승인 큐, 패키지 proof 비교, 최종 승인 로그

`개선 2번 검증`
- 유사 서비스/대체재: [Trace One PLM](https://www.traceone.com/plm-software), [Aptean PLM Lascom Cosmetics](https://lascom.com/en/plm-cosmetics/), [ManageArtworks Cosmetics](https://www.manageartworks.com/solutions/cosmetics-artwork-management), [Kallik Cosmetics Artwork](https://www.kallik.com/industries/cosmetics)
- 수요가 강한 근거: 화장품/패키지 승인 workflow 자체는 이미 별도 소프트웨어 카테고리다. 다만 한국 OEM/ODM 실무는 PLM 대신 카카오톡/메일 조합으로 버티는 경우가 많아 mid-market wedge가 있다.
- 반복 사용 가능성: 높음
- 돈을 낼 이유 / 안 낼 이유:
  - 낼 이유: 출시 일정 지연, 재작업, 버전 혼선 비용이 큼
  - 안 낼 이유: 대형사는 이미 PLM이 있고, 소형 브랜드는 메신저로 버틸 수 있음
- 치명적 리스크: 기존 PLM과 비교당할 위험, 고객사별 승인 프로세스 편차, 디자인 파일/패키지 proof 연동 복잡도
- 생존 판정: 조건부 살림
- 3번 메모: PLM 대체가 아니라 `카카오톡/메일 기반 sample approval chaos` 해결로 포지셔닝해야 함

`개선 3번 보강`
- 유지 강점: 출시 지연이 바로 손실로 이어진다.
- 버릴 요소: 개발/R&D 전 과정, 규제 전체 관리
- 차별화 포인트: 카카오/메일 코멘트 흡수, proof 버전 비교, 브랜드-공장 공동 승인 타임라인
- 첫 고객 확보 채널: 화장품 OEM 영업조직, 인디 브랜드 개발 대행사, 패키지 디자인 에이전시
- 6주 실험 KPI: 승인 리드타임 20% 감소, 버전 혼선 건수 감소, 재작업 요청 감소
- 수익화 방향: 팀 구독 + SKU/프로젝트 수 과금
- 살리는 조건: PLM 대체가 아니라 `sample approval layer`로 좁혀야 함
- 1번 질문: 첫 고객을 공장 쪽으로 잡을지 브랜드 쪽으로 잡을지? 디자인 파일 업로드 없이도 첫 가치를 줄 수 있는가?

`개선 1번 수정안`
- 최종 형태: `K-뷰티 OEM/ODM sample approval OS`
- 최종 MVP: 샘플 버전 관리, 코멘트 쓰레드, approval queue, 패키지 proof compare, 최종 승인 로그
- 지킨 것: 반복 승인 pain, 출시 지연 비용
- 버린 것: 전체 PLM, 규제/원가/생산관리

### 2. RevisitGuard for Field Service

`개선 1번 원안`
- 한 문장 pain: HVAC·가전 A/S 업체는 첫 방문 때 진단/부품/고객 일정이 어긋나 재방문이 늘고, 그만큼 기사 시간과 마진이 녹는다.
- 타겟: 20~200명 규모 HVAC·가전 A/S 운영팀, dispatch manager
- 반복 사용 이유: 매일 출동 스케줄과 부품/증상 확인이 반복된다.
- 돈을 낼 이유: 재방문 한 번이 곧 인건비와 SLA 손실이다.
- 버린 범위: 견적, 청구, 회계, CRM 전반
- 6주 MVP: 증상 intake, 첫 방문 체크리스트, 부품 사전확인, 재방문 원인 라벨, first-time-fix 대시보드

`개선 2번 검증`
- 유사 서비스/대체재: [ServiceTitan](https://www.servicetitan.com/), [Housecall Pro](https://www.housecallpro.com/), [FieldAware](https://www.fieldaware.com/), [Jobber](https://getjobber.com/)
- 수요가 강한 근거: field service management는 이미 큰 카테고리이고, first-time-fix와 revisit 감소는 핵심 KPI다.
- 반복 사용 가능성: 매우 높음
- 돈을 낼 이유 / 안 낼 이유:
  - 낼 이유: 재방문 감소가 곧 마진 개선
  - 안 낼 이유: 기존 FSM에 이미 일부 기능이 있다고 볼 수 있음
- 치명적 리스크: 기존 FSM 부가기능과 차별화 부족, 기사 입력 저항, 부품 재고 연동 난이도
- 생존 판정: 조건부 살림
- 3번 메모: `FSM 대체`가 아니라 `재방문 감소 레이어`로 잡아야 함

`개선 3번 보강`
- 유지 강점: 재방문은 숫자로 ROI가 명확하다.
- 버릴 요소: 인보이스, 고객 마케팅, 종합 dispatch
- 차별화 포인트: 증상-부품-기사 skill 매칭, first-time-fix KPI, 재방문 원인 자동 분류
- 첫 고객 확보 채널: 지역 HVAC 프랜차이즈 본부, 가전 A/S 운영 대행사
- 6주 실험 KPI: 재방문율 감소, 부품 미지참 건수 감소, 현장 소요시간 감소
- 수익화 방향: 기사 수 과금 + 운영 대시보드 플랜
- 살리는 조건: 기존 FSM과 공존 가능해야 함
- 1번 질문: 첫 타겟을 HVAC로 고정할지 가전 A/S로 고정할지? 기사 입력을 최소화하는 첫 입력 경로는 무엇인가?

`개선 1번 수정안`
- 최종 형태: `first-time-fix 개선용 field service layer`
- 최종 MVP: 증상 intake, 부품/skill 사전 체크, 재방문 원인 라벨, team KPI dashboard
- 지킨 것: 재방문 감소 ROI
- 버린 것: full FSM

### 3. QuoteRelay for Industrial Distributors

`개선 1번 원안`
- 한 문장 pain: 산업재 유통사 견적팀은 고객 RFQ, 내부 영업, 다수 공급사 답변, 납기/가격 수정이 메일과 엑셀에 흩어져 응답 속도와 수주율이 떨어진다.
- 타겟: 산업재 유통사 견적팀, inside sales manager
- 반복 사용 이유: RFQ, 공급사 문의, 견적 수정이 매일 반복된다.
- 돈을 낼 이유: 늦은 답변과 누락된 공급사 확인이 곧 수주 손실이다.
- 버린 범위: CRM 전반, 수주 후 주문관리, 회계
- 6주 MVP: RFQ intake, supplier response board, revision timeline, 납기/가격 비교, due-date alert

`개선 2번 검증`
- 유사 서비스/대체재: [Quote2Order](https://icgtechnology.com/storage/uploads/Quote2Order.pdf/TZ0gw9eL578z39j8L9qgXW2UnXE54qPmkpMDx1kK.pdf), [Interlynx Quote Management](https://www.interlynxsystems.com/manufacturer/quote-for-manufacturer.html), [ISQuote](https://www.isquote.com/), [Advantive Quoting](https://www.advantive.com/solutions/quoting-costing-software/)
- 수요가 강한 근거: 제조/유통 quoting 카테고리는 존재하고, speed-to-quote는 실제 KPI다.
- 반복 사용 가능성: 높음
- 돈을 낼 이유 / 안 낼 이유:
  - 낼 이유: 공급사 응답 지연과 누락이 수주율을 직접 깎음
  - 안 낼 이유: ERP/메일 프로세스로 버티는 중견사가 많고, 세그먼트가 좁아 보일 수 있음
- 치명적 리스크: 메일/ERP 연동 부담, 수요가 견적팀 규모에 따라 제한, 공급사 협업 참여 저조
- 생존 판정: 살림
- 3번 메모: CRM이 아니라 `supplier quote coordination layer`로 잡을 것

`개선 3번 보강`
- 유지 강점: speed-to-quote pain이 매우 선명하다.
- 버릴 요소: 영업 파이프라인, 회의록, 일반 CRM 기능
- 차별화 포인트: 공급사별 회신 SLA, revision timeline, 누락 supplier 경고
- 첫 고객 확보 채널: 산업재 유통사 내부영업팀, 제조 영업 컨설턴트
- 6주 실험 KPI: 견적 응답 리드타임 감소, 공급사 회신 누락 감소, quote win-rate 개선
- 수익화 방향: 견적 담당자 수 과금 + 팀 대시보드 플랜
- 살리는 조건: 공급사 포털 없이도 메일 기반으로 돌아야 함
- 1번 질문: 첫 타겟 업종을 전기자재/산업부품/기계유통 중 어디로 좁힐 것인가? 공급사 미응답을 제품이 어떻게 자동 추적할 것인가?

`개선 1번 수정안`
- 최종 형태: `industrial RFQ coordination OS`
- 최종 MVP: RFQ intake, supplier response board, due-date alert, revision timeline, winning quote summary
- 지킨 것: 견적 누락/지연 pain
- 버린 것: CRM/ERP full replacement

### 4. ColdChain ClaimOps

`개선 1번 원안`
- 한 문장 pain: 식품·제약 콜드체인 운영팀은 온도 이탈이 생겼을 때 원인 파악, 책임 구분, 클레임 자료 정리, 고객 보고가 분절돼 대응 시간이 길어진다.
- 타겟: 냉장·냉동 3PL 운영팀, 품질보증팀
- 반복 사용 이유: 고빈도 사건은 아니지만 온도 이탈이 날 때마다 문서와 책임 추적이 반복된다.
- 돈을 낼 이유: 폐기, 클레임, 거래처 신뢰 하락 비용이 크다.
- 버린 범위: 센서 하드웨어, 전체 TMS/WMS
- 6주 MVP: incident timeline, sensor log import, carrier handoff log, claim packet generator, RCA checklist

`개선 2번 검증`
- 유사 서비스/대체재: [Monnit Cold Chain Monitoring](https://www.monnit.com/applications/cold-chain-monitoring/), [Dickson Cold Chain Monitoring](https://dicksondata.com/cold-chain-monitoring), [Cold Chain Control](https://www.coldchaincontrol.com/), [Sensitech TempTale](https://media.copeland.com/d261a7d0-cf5d-4d0c-997b-b18800ff1b42/16189-In-Transit-Cargo-Monitoring-Brochure-HR2.pdf)
- 수요가 강한 근거: 모니터링 솔루션은 많고 규제/품질 부담도 분명하지만, `사고 이후 claim workflow`는 상대적으로 덜 제품화돼 있다.
- 반복 사용 가능성: 중간
- 돈을 낼 이유 / 안 낼 이유:
  - 낼 이유: 한 번의 이탈이 손실이 크고, 감사 대응 자료화가 어렵다
  - 안 낼 이유: 사건 발생 빈도가 낮아 상시 SaaS로 느껴지지 않을 수 있다
- 치명적 리스크: 모니터링 솔루션의 부가 기능으로 흡수 가능, 센서 데이터 통합 난이도, 고객당 사건 빈도 차이
- 생존 판정: 조건부 살림
- 3번 메모: `모니터링`이 아니라 `사고 후 책임/클레임 대응 OS`로 좁혀야 함

`개선 3번 보강`
- 유지 강점: 1건당 손실이 크다.
- 버릴 요소: 온도 모니터링 하드웨어, 일반 traceability
- 차별화 포인트: incident timeline 자동 정리, claim packet auto-compile, carrier/warehouse handoff 책임 로그
- 첫 고객 확보 채널: 냉장식품 3PL, 제약물류 QA 컨설팅사
- 6주 실험 KPI: 이탈 사고 보고서 작성시간 감소, claim close time 감소, RCA 누락 감소
- 수익화 방향: 사업장 구독 + incident volume 과금
- 살리는 조건: 사건 빈도가 높은 고객군부터 들어가야 함
- 1번 질문: 첫 시장을 식품 3PL로 잡을지 pharma QA로 잡을지? 센서 import 없이 CSV만으로도 시작 가능한가?

`개선 1번 수정안`
- 최종 형태: `cold-chain excursion claim OS`
- 최종 MVP: incident timeline, sensor import, claim packet, RCA checklist, handoff log
- 지킨 것: high-loss incident workflow
- 버린 것: hardware, full traceability

### 5. ReconFlow for Used-Car Dealers

`개선 1번 원안`
- 한 문장 pain: 중형 중고차 딜러는 매입 차량이 점검-수리-광택-사진-등록 단계에서 오래 묶일수록 가치가 떨어지는데, recon 진행 상황이 전화와 종이에 흩어져 inventory turn이 느려진다.
- 타겟: 중형 중고차 딜러 운영팀, used car inventory manager
- 반복 사용 이유: 차량이 매입될 때마다 recon 단계와 외주 vendor 조정이 반복된다.
- 돈을 낼 이유: 차량 한 대가 recon에 묶인 하루하루가 바로 carrying cost다.
- 버린 범위: DMS 전반, 판매 CRM, 금융
- 6주 MVP: recon queue, vendor assignment, stage SLA, photo-ready alert, days-to-list dashboard

`개선 2번 검증`
- 유사 서비스/대체재: [vAuto Provision + iRecon](https://www.vauto.com/products/provision/), [Simple Recon](https://simplerecon.com/used-car-recon-tracking-software/), [AMT Recon Software](https://amt.company/dealership-software-and-operations/), [Rapid Recon](https://www.rapidrecon.com/wp-content/uploads/2019/01/4-5-Rapid-Recon-Featurev3.pdf)
- 수요가 강한 근거: used-car recon은 이미 독립 카테고리고, inventory turn 개선은 직접적인 KPI다.
- 반복 사용 가능성: 높음
- 돈을 낼 이유 / 안 낼 이유:
  - 낼 이유: days-to-list 단축이 바로 이익
  - 안 낼 이유: 기존 dealer software 생태계가 이미 강하고 한국 현지 유통 구조 파악이 더 필요
- 치명적 리스크: 국내 로컬 dealer workflow 확인 필요, 기존 DMS와의 비교, 외주 정비소/vendor 협업 참여
- 생존 판정: 조건부 살림
- 3번 메모: `inventory management`가 아니라 `reconditioning turnaround`로 더 좁혀야 함

`개선 3번 보강`
- 유지 강점: time-to-list가 돈과 직결된다.
- 버릴 요소: 판매 CRM, 가격 추천, 금융
- 차별화 포인트: recon stage SLA, vendor assignment, days-to-list KPI, photo-ready handoff
- 첫 고객 확보 채널: 중형 딜러 그룹, recon outsourcing 업체, 딜러십 운영 컨설턴트
- 6주 실험 KPI: days-to-list 감소, 차량당 vendor ping-pong 감소, recon stage lead time 감소
- 수익화 방향: 점포 구독 + 차량 볼륨 구간 과금
- 살리는 조건: 한국 로컬 딜러 운영 플로우 검증이 선행돼야 함
- 1번 질문: 첫 고객을 독립 딜러로 볼지 딜러 그룹으로 볼지? 정비 외주 vendor 앱 없이도 가치가 나는가?

`개선 1번 수정안`
- 최종 형태: `used-car recon turnaround OS`
- 최종 MVP: recon queue, vendor assignment, stage SLA, photo-ready alert, days-to-list dashboard
- 지킨 것: carrying cost pain
- 버린 것: DMS/CRM/finance

## 4. 신규 아이디어 5개 우선순위

1. `SampleLoop for K-Beauty OEM/ODM`
2. `QuoteRelay for Industrial Distributors`
3. `RevisitGuard for Field Service`
4. `ReconFlow for Used-Car Dealers`
5. `ColdChain ClaimOps`

## 5. 왜 이 순위인가

- `SampleLoop`는 한국형 OEM/ODM workflow 미스매치가 강하고, SKU 출시 지연 비용이 직접적이라 첫 wedge가 선명하다.
- `QuoteRelay`는 산업재 유통팀의 반복 RFQ pain이 명확하고, 기존 CRM/ERP를 안 바꾸고도 들어갈 틈이 있다.
- `RevisitGuard`는 ROI는 좋지만 FSM incumbents와의 경계 설정이 관건이다.
- `ReconFlow`는 카테고리 자체는 검증됐지만 한국 로컬 workflow 확인이 한 번 더 필요하다.
- `ColdChain ClaimOps`는 사고 1건당 pain은 강하나, 사건 빈도 기반의 제품성 검증이 먼저 필요하다.

## 6. 다음 액션

1. 신규 5개 중 `SampleLoop`, `QuoteRelay`, `RevisitGuard`만 먼저 2회차로 올린다.
2. `ReconFlow`, `ColdChain ClaimOps`는 로컬 인터뷰 5건씩으로 먼저 존재 증명을 한다.
3. 다음 라운드부터는 개선 프롬프트를 그대로 유지하되, `2번 에이전트 폐기 기준`을 더 강하게 잡는다.
