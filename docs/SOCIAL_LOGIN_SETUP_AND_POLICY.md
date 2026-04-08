# 소셜 로그인 세팅 및 정책 정리

## 1. 범위

이번 대상은 아래 3개입니다.

- Google
- Kakao
- Apple

현재 브랜치 기준 상태는 아래와 같습니다.

- Google: 로컬 `http://localhost:8080` 에서 로그인/로그아웃/재로그인 확인 완료
- Kakao: 로컬 `http://localhost:8080` 에서 로그인/로그아웃/재로그인 확인 완료
- Apple: 백엔드와 테스트 버튼은 추가 완료, 하지만 Apple 정책상 `localhost` 직접 콜백이 불가능해 로컬 단독 테스트는 불가

## 2. 현재 백엔드 기준 공통 세팅

기술 스택:

- Java 21
- Spring Boot 3.5.11
- Spring Security OAuth2 Client

실행:

```bash
./gradlew bootRun
```

기본 테스트 주소:

- 서비스 URL: `http://localhost:8080`

현재 코드 기준 리다이렉트 URI:

- Google: `http://localhost:8080/login/oauth2/code/google`
- Kakao: `http://localhost:8080/login/oauth2/code/kakao`
- Apple: `https://{public-domain}/login/oauth2/code/apple`

환경 변수:

```bash
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
export KAKAO_CLIENT_ID=...
export KAKAO_CLIENT_SECRET=...
export APPLE_CLIENT_ID=...
export APPLE_CLIENT_SECRET=...
```

주의:

- Google, Kakao는 로컬 테스트 가능
- Apple은 공개 HTTPS 도메인이 필요

## 3. 테스트를 위한 세팅 정리

### 3.1 Google

Google Cloud Console에서 필요한 세팅:

1. 프로젝트 생성
2. `Google Auth Platform > Branding` 설정
3. `Audience`를 `External` + `Testing`으로 설정
4. 테스트 사용자 추가
5. `Clients`에서 `Web application` 생성
6. Redirect URI 등록:
   - `http://localhost:8080/login/oauth2/code/google`

현재 브랜치에서 사용하는 최소 scope:

- `openid`
- `profile`
- `email`

테스트 시 주의:

- `Testing` 상태는 최대 100명의 테스트 사용자만 허용
- 테스트 사용자의 승인과 refresh token은 7일 뒤 만료
- 기본 인증용 `openid/profile/email`은 민감 scope 없이 사용할 수 있음

### 3.2 Kakao

Kakao Developers에서 필요한 세팅:

1. 앱 생성
2. `카카오 로그인` 사용 설정 `ON`
3. Redirect URI 등록:
   - `http://localhost:8080/login/oauth2/code/kakao`
4. `REST API 키` 확인
5. `클라이언트 시크릿` 생성 및 활성화
6. `동의항목` 설정

현재 브랜치에서 사용하는 scope:

- `profile_nickname`
- `profile_image`

현재 브랜치에서 일부러 제외한 항목:

- `account_email`

제외 이유:

- 일반 앱 상태에서는 이메일 동의항목 운영 제약이 있어 테스트가 막힘
- 현재 브랜치는 빠른 로그인 테스트 목적이므로 닉네임/프로필 이미지 중심으로 구성

테스트 시 주의:

- Redirect URI 미등록 또는 불일치 시 `KOE006`
- 동의항목 미설정 시 `KOE205` 등 scope 오류 가능
- 현재 카카오는 재로그인 시 자동 통과를 줄이기 위해 재로그인 유도 파라미터를 추가한 상태

### 3.3 Apple

Apple은 로컬 `localhost` 직접 테스트가 불가능합니다.

필수 전제:

1. Apple Developer 계정
2. Sign in with Apple 이 활성화된 `Primary App ID`
3. `Services ID`
4. 공개 HTTPS 도메인
5. Return URL 등록
6. Sign in with Apple private key 생성
7. private key로 `client secret JWT` 생성

Return URL 예시:

- `https://dev.example.com/login/oauth2/code/apple`
- `https://{ngrok-domain}/login/oauth2/code/apple`

현재 브랜치에서 필요한 값:

- `APPLE_CLIENT_ID`: Services ID
- `APPLE_CLIENT_SECRET`: Apple private key 기반 JWT

테스트 방법:

- `ngrok`, Cloudflare Tunnel 같은 공개 HTTPS 터널 사용
- 또는 dev/staging 서버 배포 후 테스트

## 4. 실제 서비스 단계에서 추가로 해야 할 일과 정책

### 4.1 Google

운영 단계 추가 작업:

- 테스트용과 운영용 Google Cloud 프로젝트 분리
- 운영용 OAuth Consent Screen 구성
- 홈페이지, 개인정보처리방침, 필요 시 서비스 약관 URL 준비
- Consent Screen에 등록한 도메인과 redirect/origin 도메인 소유권 일치
- 최소 권한(scope)만 요청

정책상 중요한 점:

- 운영 앱은 테스트 프로젝트와 분리하는 것이 권장 사항이 아니라 사실상 정책 수준의 권장
- 외부 사용자용 앱에서 이름/로고/홈페이지/정책 URL을 노출하려면 브랜드 검증이 필요할 수 있음
- 민감/제한 scope를 추가하는 경우 별도 검증 필요
- 앱 이름, 로고, 홈페이지, 개인정보처리방침, 승인 도메인 변경 시 재검증 필요

현재 브랜치 수준에서의 해석:

- 지금 사용하는 `openid/profile/email`만으로는 기본 로그인 용도에 충분
- Google API 추가 연동이 없다면 scope 확장은 신중하게 미루는 것이 안전

### 4.2 Kakao

운영 단계 추가 작업:

- 필요 시 `비즈 앱` 전환
- 이메일 등 추가 개인정보를 쓰려면 동의항목 심사 준비
- 회원가입 화면과 실제 수집 정보 정합성 맞추기
- 동의 목적 문구와 실제 서비스 수집 목적 일치시키기
- 필요 시 카카오싱크 도입 여부 결정

정책상 중요한 점:

- 비즈 앱은 이메일을 필수 동의로 설정할 수 있음
- 개인정보 동의항목 심사 시, 회원가입 페이지/수집 항목/필수·선택 구분/수집 목적이 제출 내용과 일치해야 함
- 테스트 앱은 원본 앱 심사 전에 기능 미리보기 용도로 사용할 수 있음

현재 브랜치 수준에서의 해석:

- 로그인만 빠르게 확인하는 용도라면 지금처럼 닉네임/프로필 이미지만으로 충분
- 이메일을 실제 회원 식별이나 마케팅/알림에 쓸 계획이면 비즈 앱 및 동의항목 심사까지 진행해야 함

### 4.3 Apple

운영 단계 추가 작업:

- Apple Developer 계정 및 식별자 체계 정리
- `Primary App ID`와 `Services ID` 연동
- 운영 도메인과 Return URL을 공개 HTTPS로 구성
- `APPLE_CLIENT_SECRET` JWT 갱신 전략 수립
- 사용자가 `Hide My Email`을 선택했을 때의 계정 처리 전략 수립
- Apple private email relay 주소로 메일을 보낼 계획이면 발신 도메인 등록 및 SPF/DKIM 세팅

정책상 중요한 점:

- 웹용 Sign in with Apple은 `return URL`이 HTTPS여야 하며, IP 주소나 `localhost`를 쓸 수 없음
- Apple은 이름을 ID token에 넣지 않으며, 이름은 최초 승인 시 브라우저에서 앱으로 직접 전달되는 `user` 객체에서만 받을 수 있음
- 이후 재인증에서는 `user` 객체가 다시 오지 않을 수 있으므로 최초 값을 반드시 저장해야 함
- iOS 앱이 다른 소셜 로그인을 기본 계정 로그인으로 제공하면, App Store Review Guideline 4.8에 따라 Apple 로그인도 동등한 옵션으로 제공해야 함

현재 브랜치 수준에서의 해석:

- 백엔드 구조는 맞춰두었지만, 실제 검증은 공개 HTTPS 환경이 있어야 완료 가능
- 웹만 운영한다면 별도 "OAuth 심사"보다는 Apple 설정 정합성이 더 중요
- iOS 앱까지 포함되면 App Store 심사 정책을 같이 따라야 함

## 5. 심사/등록 필요 여부, 조건, 소요 시간

### 5.1 Google

테스트 단계:

- `Testing` 상태에서는 심사 없이 테스트 가능
- 단, 최대 100명 테스트 사용자 제한
- 승인 정보는 7일 만료

운영 단계:

- External 앱에서 브랜드 정보 노출 시 브랜드 검증 필요 가능
- 기본 브랜드 검증 소요: 보통 2~3 영업일
- 민감 scope 검증 소요: 보통 3~5 영업일
- 제한 scope 또는 특정 고위험 API는 별도 보안 심사까지 요구될 수 있어 더 오래 걸릴 수 있음

조건:

- 홈페이지
- 개인정보처리방침
- 소유 도메인
- 정확한 redirect URI/origin
- 실제 동작을 보여주는 데모 영상/검증 정보가 추가로 요구될 수 있음

### 5.2 Kakao

테스트 단계:

- 기본 로그인 자체는 심사 없이 가능
- Redirect URI, 동의항목, REST API 키 세팅이 우선

운영 단계:

- 이메일 등 추가 개인정보를 본격 사용하려면 비즈 앱 전환 및 동의항목 심사 필요 가능
- 개인정보 동의항목 심사 소요: 영업일 기준 3~5일
- 반려 후 재심사도 다시 3~5일

조건:

- 회원가입 화면 제출 자료
- 수집 정보와 신청 동의항목의 일치
- 필수/선택 구분 일치
- 동의 목적 문구
- 실제 서비스 화면과 제출 자료의 일치

### 5.3 Apple

테스트/운영 설정 단계:

- 별도의 "Google/Kakao 같은 OAuth 검증" 절차가 문서상 명확히 안내되지는 않음
- 대신 Apple Developer 설정 요건을 충족해야 함

필수 조건:

- Primary App ID
- Services ID
- Sign in with Apple capability
- 공개 HTTPS 도메인
- Return URL
- private key
- client secret JWT

iOS 앱 서비스 단계:

- 다른 소셜 로그인으로 기본 계정 로그인을 제공하면 App Store 심사에서 Apple 로그인 제공이 요구될 수 있음

소요 시간:

- Apple 웹 로그인 설정 자체에 대한 공식 고정 심사 기간은 이번 조사 범위에서 확인되지 않음
- App Store 심사는 고정 SLA가 명시돼 있지 않고, 복잡하거나 새로운 이슈가 있으면 더 오래 걸릴 수 있음

## 6. 소셜 로그인 성공 시 가져올 수 있는 정보

## 6.1 Google

현재 브랜치 테스트 기준:

- `sub`
- `email`
- `email_verified`
- `name`
- `given_name`
- `family_name`
- `picture`

정리:

- `sub`: 항상 제공, 사용자 고유 식별자로 사용 권장
- `email`, `email_verified`: `email` scope를 요청했을 때 제공
- `name`, `picture`, `given_name`, `family_name`: `profile` scope를 요청했을 때 제공될 수 있음

테스트/운영 차이:

- 기본 `openid/profile/email`만 쓴다면 테스트와 운영에서 가져오는 정보 종류 자체는 거의 동일
- 차이는 주로 사용자 수 제한, 토큰 만료 정책, 검증 여부
- 운영에서 Google API 추가 scope를 쓰면 더 많은 정보/권한을 받을 수 있지만 그만큼 검증 부담이 늘어남

실무 권장:

- DB 식별자는 `sub` 사용
- 이메일은 표시/연락용 보조 값으로 취급

### 6.2 Kakao

현재 브랜치 테스트 기준:

- `id` 또는 OIDC 기준 `sub`에 해당하는 서비스 사용자 ID
- 닉네임
- 프로필 이미지 URL

현재 브랜치에서는 제외:

- 이메일

운영 확장 가능 정보:

- 이메일
- 이름
- 성별
- 연령대
- 출생연도
- 생일
- 전화번호
- 배송지

단, 위 정보는 모두 동의항목 설정, 사용자 동의, 앱 권한/심사 상태에 따라 달라집니다.

테스트/운영 차이:

- 현재 테스트 브랜치는 닉네임/프로필 이미지만 받음
- 운영에서 비즈 앱/심사 완료 후 이메일 등 추가 정보 확장 가능

실무 권장:

- 식별자는 Kakao 서비스 사용자 ID 사용
- 이메일은 사용자가 바꿀 수 있으므로 보조 식별자 취급

### 6.3 Apple

현재 브랜치에서 설계한 요청 기준:

- `sub`
- `email`
- `name`

실제 응답 특성:

- `sub`: 항상 핵심 식별자
- `email`: ID token에서 제공될 수 있음
- `name`: 최초 승인 시의 `user` 객체에서만 전달되고, 이후 재인증에서는 다시 오지 않을 수 있음
- 사용자가 `Hide My Email`을 선택하면 실제 이메일 대신 Apple relay 주소가 올 수 있음

테스트/운영 차이:

- 로컬 `localhost` 테스트는 불가
- 운영/공개 테스트 환경에서만 실제 end-to-end 검증 가능
- 정보 종류는 scope와 최초 승인 여부에 크게 좌우됨

실무 권장:

- DB 식별자는 `sub`
- 최초 승인 시 받은 이름은 즉시 저장
- relay 이메일 사용자를 전제로 계정/메일 정책 설계

## 7. 권장 결론

현 시점 권장 방향:

1. Google
   - 현재 구조 유지
   - 운영 전용 프로젝트 분리
   - `openid/profile/email`만 유지

2. Kakao
   - 현재 테스트 브랜치는 닉네임/프로필 이미지만 유지
   - 운영에서 이메일이 필요하면 비즈 앱 + 동의항목 심사 진행

3. Apple
   - 코드 반영은 유지
   - 실제 검증은 `ngrok` 또는 dev HTTPS 도메인으로 진행
   - iOS 앱 계획이 있다면 App Store Guideline 4.8도 같이 체크

## 8. 참고 자료

Google:

- https://developers.google.com/identity/oauth2/web/guides/load-3p-authorization-library
- https://developers.google.com/identity/openid-connect/openid-connect
- https://support.google.com/cloud/answer/15549945
- https://developers.google.com/identity/protocols/oauth2/production-readiness/brand-verification
- https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification
- https://developers.google.com/identity/protocols/oauth2/production-readiness/policy-compliance
- https://developers.google.com/identity/protocols/oauth2/policies

Kakao:

- https://developers.kakao.com/docs/latest/ko/kakaologin/prerequisite
- https://developers.kakao.com/docs/latest/en/app-setting/app
- https://developers.kakao.com/docs/latest/en/kakaologin/utilize
- https://developers.kakao.com/docs/latest/en/kakaologin/rest-api
- https://developers.kakao.com/docs/latest/en/getting-started/permission
- https://developers.kakao.com/docs/latest/ko/kakaologin/faq

Apple:

- https://developer.apple.com/help/account/capabilities/configure-sign-in-with-apple-for-the-web/
- https://developer.apple.com/documentation/signinwithapplerestapi/request-an-authorization-to-the-sign-in-with-apple-server
- https://developer.apple.com/help/account/configure-app-capabilities/create-a-sign-in-with-apple-private-key/
- https://developer.apple.com/help/account/capabilities/configure-private-email-relay-service/
- https://developer.apple.com/app-store/review/guidelines/
