# 소셜 로그인 설정 및 정책

## 범위

이 문서는 NF-8 소셜 로그인 백엔드 적용 범위를 정리합니다.

- 대상 provider: Google, Kakao
- 로그인 진입: `/oauth2/authorization/{registrationId}`
- OAuth 콜백: `/login/oauth2/code/{registrationId}`
- 로그인 성공 후 앱 이동: `redirect_uri` query parameter가 허용된 값이면 JWT를 fragment로 붙여 redirect
- 사용자 저장: 최초 로그인 시 `users`, `social_accounts`에 저장
- 재로그인: 동일 provider 사용자 ID가 있으면 기존 사용자에 연결

## 환경 변수

로컬 또는 배포 환경에서 아래 값을 설정합니다.

```bash
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
KAKAO_CLIENT_ID=...
KAKAO_CLIENT_SECRET=...
APP_JWT_SECRET=...
APP_ALLOWED_REDIRECT_URI_PREFIXES=sagongsa404://auth/callback,http://localhost,http://127.0.0.1
```

## Google 설정

Google Cloud Console에서 Web application OAuth client를 만들고 아래 redirect URI를 등록합니다.

```text
http://localhost:8080/login/oauth2/code/google
```

사용 scope:

- `profile`
- `email`

## Kakao 설정

Kakao Developers에서 카카오 로그인을 활성화하고 아래 redirect URI를 등록합니다.

```text
http://localhost:8080/login/oauth2/code/kakao
```

사용 scope:

- `profile_nickname`
- `profile_image`

MVP에서는 이메일을 필수로 요구하지 않습니다. 일반 앱 상태에서 이메일 동의항목 제약이 있어 빠른 로그인 검증을 막을 수 있기 때문입니다.

## API

현재 로그인 사용자 조회:

```http
GET /api/auth/me
Authorization: Bearer {accessToken}
```

refresh token 재발급:

```http
POST /api/auth/token/refresh
Content-Type: application/json

{
  "refreshToken": "{refreshToken}"
}
```

## 정책 메모

- MVP 정책상 한 사용자는 하나의 소셜 provider만 연결합니다.
- DB 제약도 `social_accounts.user_id` unique로 같은 정책을 따릅니다.
- 다중 provider 연결을 허용할 때는 해당 unique 제약과 로그인 연결 정책을 함께 바꿔야 합니다.
