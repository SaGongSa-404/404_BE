# Shopping Link Validation Playground

이 저장소에는 쇼핑 링크 자동 수집 검증 스크립트와 쿠팡 전용 브라우저 추출기 프로토타입이 들어 있습니다.

## 쿠팡 브라우저 추출기

쿠팡은 서버 요청과 헤드리스 브라우저에서 `403 Access Denied`가 발생해서, 현재는 브라우저 내부 추출 방식으로 우회하는 프로토타입을 만들었습니다.

확장 프로그램 위치:

- `chrome-extension/manifest.json`

설치 방법:

1. Chrome 주소창에 `chrome://extensions` 를 엽니다.
2. 우측 상단 `개발자 모드`를 켭니다.
3. `압축해제된 확장 프로그램을 로드합니다`를 누릅니다.
4. 이 폴더의 `chrome-extension` 디렉터리를 선택합니다.
5. 쿠팡 상품 페이지를 연 뒤 확장 아이콘을 눌러 `상품 정보 추출`을 실행합니다.

기능:

- 현재 탭에서 상품 `제목 / 가격 / 대표 이미지 / URL` 추출
- 가격 후보 DOM 강조
- 현재 보이는 영역 PNG 캡처 저장
- 추출 결과 JSON 복사

주의:

- 이 방식은 사용자의 실제 브라우저 컨텍스트에서 실행되는 전제입니다.
- 쿠팡 UI 변경에 따라 선택자는 추가 조정이 필요할 수 있습니다.
- 페이지가 상품 상세가 아니라면 일부 필드만 추출되거나 실패할 수 있습니다.

## 쿠팡 앱 프로토타입

Electron 기반 앱 프로토타입도 추가했습니다.

실행:

```bash
npm run coupang:app
```

자동 테스트:

```bash
npm run coupang:app:test
```

현재 검증 결과:

- `https://www.coupang.com/vp/products/...` 데스크톱 경로는 이 환경에서 `403 Access Denied`
- `https://m.coupang.com/nm/products/...` 모바일 경로는 Electron 앱에서 가격과 상품 이미지를 추출 성공

파일:

- `electron-app/main.mjs`
- `electron-app/renderer.html`
- `electron-app/renderer.js`

## 검증 스크립트

전체 쇼핑 서비스 검증:

```bash
npm run validate:shopping
```

결과 파일:

- `artifacts/shopping-validation-results.md`
- `artifacts/shopping-validation-results.json`
- `artifacts/shopping-network-probe.txt`
