# OAuth Social Login Test Backend

구글, 카카오, 네이버, 애플 로그인을 바로 테스트할 수 있도록 만든 Spring Boot 백엔드입니다.

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
```

앱을 실행합니다.

```bash
./gradlew bootRun
```

브라우저에서 `http://localhost:8080` 으로 접속하면 로그인 테스트 화면이 열립니다.

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
- 로그아웃은 `POST /api/logout` 으로 처리합니다.
