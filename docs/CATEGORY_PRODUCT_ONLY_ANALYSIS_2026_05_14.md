# 상품 상세 URL만 사용한 카테고리 측정

실행일: 2026-05-14T13:07:10.149Z
브라우저: Playwright Chromium visible/headed
렌더 대기: 1200ms

## 요약

- 전체 후보 샘플: 70
- 상품 상세 URL 확보: 58/70
- 상품 상세 샘플: 58
- 추출 성공: 58/58 (100.0%)
- weighted content-only 정확도: 47/58 (81.0%)
- weighted url-assisted 정확도: 47/58 (81.0%)
- 상품 상세 URL 미확보: 12

## 해석 기준

- `58/58`은 전체 후보 70개 중 성공했다는 뜻이 아니라, 상품 상세 URL을 확보한 58개를 대상으로 렌더링/추출이 모두 성공했다는 뜻이다.
- 상품 상세 URL을 확보하지 못한 12개는 분류 정확도 계산에서 제외했다. 따라서 운영 판단에는 `상품 상세 URL 확보율 58/70`과 `확보된 상품 상세 URL 기준 정확도 47/58`을 함께 봐야 한다.
- 검색/카테고리 URL은 상품 상세 링크를 찾는 보조 수단으로만 사용했고, 최종 정확도 평가는 실제 상품 상세 URL만 대상으로 했다.
- 이 수치는 자동 선택값을 확정값으로 쓰기 위한 근거가 아니라, 유저가 수정할 수 있는 추천값으로 제공할 때의 보수적 기준선이다.

## 재현 자료

- 실행 환경: Playwright Chromium visible/headed, 렌더 대기 1200ms, 실행일 `2026-05-14T13:07:10.149Z`
- 샘플 입력: `docs/category-experiment/NF-35_SAMPLE_INPUTS_2026_05_14.csv`
- 실행 스크립트/커맨드: `docs/category-experiment/NF-35_CATEGORY_EXPERIMENT_REPRODUCIBILITY_2026_05_14.md`
- 분류 규칙 버전: `docs/category-experiment/NF-35_CLASSIFIER_RULESET_2026_05_14.md`
- RAW 결과: `docs/category-experiment/nf35-category-experiment/results/product-only-category-analysis-2026-05-14.json`
- 비교용 검색/카테고리 URL 포함 RAW 결과: `docs/category-experiment/nf35-category-experiment/results/visible-browser-category-analysis-2026-05-14.json`

## 사이트별

| 사이트 | 샘플 | 추출 성공 | content-only | url-assisted |
|---|---:|---:|---:|---:|
| 무신사 | 8 | 8/8 | 8/8 | 8/8 |
| 29CM | 10 | 10/10 | 5/10 | 5/10 |
| 올리브영 | 9 | 9/9 | 8/9 | 8/9 |
| 지그재그 | 10 | 10/10 | 9/10 | 9/10 |
| 에이블리 | 1 | 1/1 | 1/1 | 1/1 |
| 당근 | 10 | 10/10 | 7/10 | 7/10 |
| 번개장터 | 10 | 10/10 | 9/10 | 9/10 |

## 문제 샘플

| 구분 | 사이트 | 기대 | HTTP | content-only | url-assisted | 제목/오류 | 비고 | URL |
|---|---|---|---:|---|---|---|---|---|
| 분류 오답 | 29CM | FASHION | 200 | BEAUTY | BEAUTY | 감도 깊은 취향 셀렉트샵 29CM | 백팩 | https://product.29cm.co.kr/catalog/1604960 |
| 분류 오답 | 29CM | LIVING | 200 | BEAUTY | BEAUTY | 브레빌 바리스타 익스프레스 임프레스 BES876 - 감도 깊은 취향 셀렉트샵 29CM | 검색: 커피머신 | https://product.29cm.co.kr/catalog/2746240 |
| 분류 오답 | 29CM | DIGITAL | 200 | HOBBY | HOBBY | 산리오캐릭터즈 피규어 키캡 키링 2구 클리커 - 감도 깊은 취향 셀렉트샵 29CM | 검색: 키보드 | https://product.29cm.co.kr/catalog/3918491 |
| 분류 오답 | 29CM | HOBBY | 200 | BEAUTY | BEAUTY | 헬로키티 & 위글위글 보냉백(S)-Pink Check Garden - 감도 깊은 취향 셀렉트샵 29CM | 검색: 캠핑 | https://product.29cm.co.kr/catalog/3959897 |
| 분류 오답 | 29CM | ETC | 200 | BEAUTY | BEAUTY | [선물추천/커스텀 이니셜] 맥세이프 카드 슬리브 Polka Dot - 버건디 [Port Wine] - 감도 깊은 취향 셀렉트샵 29CM | 검색: 선물카드 | https://product.29cm.co.kr/catalog/3486317 |
| 분류 오답 | 올리브영 | FOOD | 200 | BEAUTY | BEAUTY | [온라인단독]딜라이트 프로젝트 스낵 선물세트 2종 택1(베이글칩/명인부각) / 올리브영 | 푸드 | https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000231594&trackingCd=Cat10000020002_Planshop2_1_PROD&t_page=%EC%B9%B4%ED%85%8C%EA%B3%A0%EB%A6%AC%EA%B4%80&t_click=%EB%A1%A4%EB%A7%81%EB%9D%A0%EB%B0%B0%EB%84%88&t_banner_name=%EB%94%9C%EB%9D%BC%EC%9D%B4%ED%8A%B8 |
| 분류 오답 | 지그재그 | ETC | 200 | FASHION | FASHION | 슈즈앤 여성 스트링 포인트 미들 장화 레인부츠 sn1070 | 검색: 잡화 | https://zigzag.kr/catalog/products/159318460 |
| 분류 오답 | 당근 | DIGITAL | 200 | HOBBY | HOBBY | 게이밍 키보드 / 취미/게임/음반 / 당근 중고거래 | 검색: 키보드 | https://www.daangn.com/kr/buy-sell/%EA%B2%8C%EC%9D%B4%EB%B0%8D-%ED%82%A4%EB%B3%B4%EB%93%9C-wout9h4cfkr8/ |
| 분류 오답 | 당근 | DIGITAL | 200 | HOBBY | HOBBY | 디즈니 랜드 굿즈 일괄 - 미키 마우스, 미니 마우스 (미사용) / 취미/게임/음반 / 당근 중고거래 | 검색: 마우스 | https://www.daangn.com/kr/buy-sell/%EB%94%94%EC%A6%88%EB%8B%88-%EB%9E%9C%EB%93%9C-%EA%B5%BF%EC%A6%88-%EC%9D%BC%EA%B4%84-%EB%AF%B8%ED%82%A4-%EB%A7%88%EC%9A%B0%EC%8A%A4-%EB%AF%B8%EB%8B%88-%EB%A7%88%EC%9A%B0%EC%8A%A4-%EB%AF%B8%EC%82%AC%EC%9A%A9-rgokxsmtn9bz/ |
| 분류 오답 | 당근 | FOOD | 200 | LIVING | LIVING | 밀리타 카페오 솔로 전자동 커피머신 / 생활가전 / 당근 중고거래 | 검색: 커피 | https://www.daangn.com/kr/buy-sell/%EB%B0%80%EB%A6%AC%ED%83%80-%EC%B9%B4%ED%8E%98%EC%98%A4-%EC%86%94%EB%A1%9C-%EC%A0%84%EC%9E%90%EB%8F%99-%EC%BB%A4%ED%94%BC%EB%A8%B8%EC%8B%A0-ugrycd22tes2/ |
| 분류 오답 | 번개장터 | LIVING | 200 | BEAUTY | BEAUTY | RILAKKUMA / 리락쿠마 Rilakkuma Hamburger Re-Ment Coffee Machine #리락쿠마,#리락쿠마리멘트,#리멘트,#푸치샘플 on Bunjang Global Site. | 검색: 커피머신 | https://globalbunjang.com/product/337816518 |

## 상품 상세 URL 미확보

| 사이트 | 기대 | 비고 | 원본 URL |
|---|---|---|---|
| 무신사 | FASHION | 스니커즈 | https://www.musinsa.com/content/mz/65412 |
| 무신사 | FASHION | 스니커즈 | https://www.musinsa.com/content/mz/105496 |
| 올리브영 | DIGITAL | 뷰티 디바이스 | https://www.oliveyoung.co.kr/store/display/getCategoryShop.do?dispCatNo=100000100010009 |
| 에이블리 | FASHION | 검색: 가디건 | https://m.a-bly.com/search?keyword=%EA%B0%80%EB%94%94%EA%B1%B4 |
| 에이블리 | FASHION | 검색: 원피스 | https://m.a-bly.com/search?keyword=%EC%9B%90%ED%94%BC%EC%8A%A4 |
| 에이블리 | FASHION | 검색: 셔츠 | https://m.a-bly.com/search?keyword=%EC%85%94%EC%B8%A0 |
| 에이블리 | FASHION | 검색: 팬츠 | https://m.a-bly.com/search?keyword=%ED%8C%AC%EC%B8%A0 |
| 에이블리 | FASHION | 검색: 스니커즈 | https://m.a-bly.com/search?keyword=%EC%8A%A4%EB%8B%88%EC%BB%A4%EC%A6%88 |
| 에이블리 | FASHION | 검색: 가방 | https://m.a-bly.com/search?keyword=%EA%B0%80%EB%B0%A9 |
| 에이블리 | BEAUTY | 검색: 립 | https://m.a-bly.com/search?keyword=%EB%A6%BD |
| 에이블리 | BEAUTY | 검색: 쿠션 | https://m.a-bly.com/search?keyword=%EC%BF%A0%EC%85%98 |
| 에이블리 | ETC | 검색: 문구 | https://m.a-bly.com/search?keyword=%EB%AC%B8%EA%B5%AC |
