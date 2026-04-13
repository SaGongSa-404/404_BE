# OAuth Social Login Test Backend

구글, 카카오, 네이버, 애플 로그인을 바로 테스트할 수 있도록 만든 Spring Boot 백엔드입니다.
브라우저 세션 로그인과 앱 딥링크 로그인 둘 다 받을 수 있게 정리되어 있습니다.

## Stack

- Java 21
- Spring Boot 3.5.11
- Spring Security
- OAuth2 Client
- Gradle Wrapper

## Run

환경 변수를 먼저 설정합니다.

```bash
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret
export KAKAO_CLIENT_ID=your-kakao-rest-api-key
export KAKAO_CLIENT_SECRET=your-kakao-client-secret
export NAVER_CLIENT_ID=your-naver-client-id
export NAVER_CLIENT_SECRET=your-naver-client-secret
export APPLE_CLIENT_ID=your-apple-service-id
export APPLE_CLIENT_SECRET=your-apple-client-secret-jwt
export APP_JWT_SECRET=your-app-jwt-secret
```

앱을 실행합니다.

```bash
./gradlew bootRun
```

브라우저에서 `http://localhost:8080` 으로 접속하면 로그인 테스트 화면이 열립니다.

앱 로그인으로 붙일 때는 아래처럼 `redirect_uri` 파라미터를 같이 넘기면 됩니다.

```text
GET /oauth2/authorization/google?redirect_uri=sagongsa404://auth/callback
GET /oauth2/authorization/kakao?redirect_uri=sagongsa404://auth/callback
```

로그인이 끝나면 백엔드가 앱 딥링크로 다시 보내며, 토큰은 URL fragment에 담깁니다.

```text
sagongsa404://auth/callback#access_token=...&refresh_token=...&token_type=Bearer
```

허용 규칙은 현재 기준으로 아래와 같습니다.

- 커스텀 앱 스킴: 허용
- `http://localhost`, `http://127.0.0.1`, `https://localhost`, `https://127.0.0.1`: 허용
- 그 외 일반 외부 웹 도메인: 차단

## Redirect URI

OAuth 콘솔에는 아래 Redirect URI를 등록하면 됩니다.

- Google: `http://localhost:8080/login/oauth2/code/google`
- Kakao: `http://localhost:8080/login/oauth2/code/kakao`
- Naver: `http://localhost:8080/login/oauth2/code/naver`
- Apple: `https://your-public-domain/login/oauth2/code/apple`

카카오는 비즈 앱 전환 전 테스트 기준으로 닉네임과 프로필 이미지만 요청합니다. 이메일은 요청하지 않습니다.
네이버는 이름, 이메일, 프로필 이미지를 요청하며 동의 항목 설정에 따라 일부 값은 비어 있을 수 있습니다.
애플은 공식 문서 기준으로 HTTPS Return URL이 필요해 `localhost`로 직접 테스트할 수 없습니다. ngrok 같은 공개 HTTPS 터널이나 배포된 도메인이 필요합니다.

## Notes

- 이 브랜치는 로그인 테스트 목적이라 CSRF를 비활성화했습니다.
- 로그인 후 `GET /api/auth/me` 로 현재 인증된 사용자 정보를 확인할 수 있습니다.
- 앱은 `Authorization: Bearer <access_token>` 으로 `GET /api/auth/me` 를 호출할 수 있습니다.
- 토큰 갱신은 `POST /api/auth/token/refresh` 로 처리합니다.
- 로그아웃은 `POST /api/logout` 으로 처리합니다.
