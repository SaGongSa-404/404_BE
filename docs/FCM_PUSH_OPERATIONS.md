# FCM 푸시 운영 가이드

## 목적

백엔드에서 Firebase Cloud Messaging 푸시 알림을 발송하기 위해 필요한 서버 설정과 검증 절차를 정리한다.

Firebase 서비스 계정 JSON은 비밀키다. 레포와 빌드 산출물 밖에서만 관리한다.

## 런타임 설정

백엔드는 아래 환경변수를 읽는다.

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `APP_PUSH_FCM_ENABLED` | `false` | `false`이면 FCM 발송 대신 no-op sender를 사용한다. |
| `APP_PUSH_FCM_CREDENTIALS_LOCATION` | empty | Firebase 서비스 계정 JSON의 Spring resource 경로다. FCM 활성화 시 필수다. |
| `APP_ADMIN_NOTIFICATION_TOKEN` | empty | 관리자 알림 API에서 `X-Admin-Token` 검증에 사용할 수 있는 선택 값이다. |

`APP_PUSH_FCM_ENABLED=true`인데 `APP_PUSH_FCM_CREDENTIALS_LOCATION`이 비어 있거나 읽을 수 없으면 애플리케이션 부팅이 실패한다. 푸시가 반쯤 켜진 상태로 서버가 뜨는 것을 막기 위한 동작이다.

## 비밀 정보 관리 원칙

- Firebase 서비스 계정 JSON 파일은 커밋하지 않는다.
- JSON 내용을 이슈, PR, 로그, 런북, 채팅에 붙여 넣지 않는다.
- 실제 private key 필드, `private_key_id`, `client_email`, 다운로드된 로컬 파일명을 문서에 남기지 않는다.
- JSON의 `project_id`가 프론트 앱 등록에 사용한 Firebase 프로젝트와 일치하는지만 확인한다. 프로젝트가 다르면 등록된 토큰 발송이 전부 실패할 수 있다.

## VM 설정

Firebase Admin SDK JSON은 승인된 SSH 경로로 VM에 업로드한다. 예를 들어 GCP 브라우저 SSH 업로드 또는 등록된 private key를 사용하는 `scp`를 사용할 수 있다.

임시 업로드 경로 예시:

```text
/tmp/firebase-adminsdk.json
```

이후 파일을 백엔드 서비스 계정이 읽을 수 있는 secret 디렉토리로 설치한다.

```bash
SERVICE_USER=$(systemctl show -p User --value wigul-backend)
[ -z "$SERVICE_USER" ] && SERVICE_USER=root
SERVICE_GROUP=$(id -gn "$SERVICE_USER")

sudo install -d -m 750 -o "$SERVICE_USER" -g "$SERVICE_GROUP" /opt/wigul/secrets

sudo install -m 600 -o "$SERVICE_USER" -g "$SERVICE_GROUP" \
  /tmp/firebase-adminsdk.json \
  /opt/wigul/secrets/firebase-adminsdk.json

rm -f /tmp/firebase-adminsdk.json
```

private key 내용은 출력하지 않고 `project_id`와 파일 권한만 확인한다.

```bash
sudo grep '"project_id"' /opt/wigul/secrets/firebase-adminsdk.json
sudo ls -l /opt/wigul/secrets/firebase-adminsdk.json
```

권한 형태:

```text
-rw------- <service-user> <service-group> /opt/wigul/secrets/firebase-adminsdk.json
```

## systemd drop-in

FCM 설정은 systemd drop-in으로 주입한다. 이렇게 하면 secret 경로가 jar와 레포 밖에 남는다.

```bash
sudo mkdir -p /etc/systemd/system/wigul-backend.service.d

printf '%s\n' \
'[Service]' \
'Environment="APP_PUSH_FCM_ENABLED=true"' \
'Environment="APP_PUSH_FCM_CREDENTIALS_LOCATION=file:/opt/wigul/secrets/firebase-adminsdk.json"' \
| sudo tee /etc/systemd/system/wigul-backend.service.d/fcm.conf >/dev/null

sudo systemctl daemon-reload
sudo systemctl restart wigul-backend
```

drop-in이 로드됐고 서버가 정상 부팅됐는지 확인한다.

```bash
sudo systemctl status wigul-backend --no-pager
sudo systemctl show wigul-backend -p Environment --no-pager
sudo journalctl -u wigul-backend --since "10 minutes ago" --no-pager
```

정상 기준:

- `wigul-backend`가 `active (running)`이다.
- `Environment=`에 `APP_PUSH_FCM_ENABLED=true`가 있다.
- `Environment=`에 `APP_PUSH_FCM_CREDENTIALS_LOCATION=file:/opt/wigul/secrets/firebase-adminsdk.json`가 있다.
- startup 로그에 `Failed to load Firebase credentials`가 없다.

## 배포와의 관계

QA 배포 workflow는 jar를 후보 파일로 업로드한 뒤 8081 standby 프로세스를 먼저 띄운다. 8081 `/health`가 성공하면 Caddy를 8081로 전환하고, 기존 8080 `wigul-backend`의 jar를 교체한 뒤 서비스를 재시작한다. 8080 `/health`가 다시 성공하면 Caddy를 8080으로 되돌리고 8081 standby를 종료한다.

8080 재시작이 실패하면 workflow는 실패 처리되지만, Caddy는 8081 standby를 바라보는 상태로 남겨 외부 요청을 계속 처리한다. 이 경우 8080 복구가 끝날 때까지 8081 standby 프로세스를 종료하면 안 된다.

Firebase 서비스 계정 JSON을 업로드하지 않고, systemd drop-in도 만들지 않는다. VM-local secret 파일과 drop-in은 일반적인 jar 배포와 서비스 재시작 후에도 유지된다. 다만 새 VM을 만들거나 서비스 유닛을 다시 구성하면 FCM 설정을 다시 provision해야 한다.

## End-to-End 테스트 흐름

1. 프론트가 같은 Firebase 프로젝트에서 FCM 토큰을 발급받는다.
2. 프론트가 토큰을 등록한다.

```http
POST /api/v1/push-tokens
```

3. 백엔드가 스케줄 worker, reminder worker, 관리자 알림 API 중 하나로 알림을 생성한다.
4. 백엔드는 인앱 알림을 저장하고 활성 device token으로 FCM을 발송한다.
5. 프론트가 실기기에서 푸시 수신을 확인한다.

서버에서는 토큰 등록과 알림 생성 여부를 확인할 수 있지만, 실제 푸시 수신 여부는 실기기에서 확인해야 한다.

## 장애 확인 포인트

| 증상 | 확인 |
| --- | --- |
| 서버는 뜨지만 푸시가 발송되지 않음 | `systemctl show`에서 `APP_PUSH_FCM_ENABLED=true`인지 확인한다. |
| 재시작 시 서버 부팅 실패 | `APP_PUSH_FCM_CREDENTIALS_LOCATION`과 파일 권한을 확인한다. |
| 토큰 등록 후 모든 발송 실패 | 프론트 앱 등록 Firebase 프로젝트와 서비스 계정 JSON의 프로젝트가 일치하는지 확인한다. |
| 특정 기기만 푸시 수신 실패 | 토큰이 invalid 처리되어 비활성화됐는지 확인한다. |

## 롤백

credential 파일은 유지하고 FCM만 비활성화한다.

```bash
sudo rm -f /etc/systemd/system/wigul-backend.service.d/fcm.conf
sudo systemctl daemon-reload
sudo systemctl restart wigul-backend
sudo systemctl show wigul-backend -p Environment --no-pager
```

credential 파일은 키 교체나 환경 폐기 시에만 삭제한다.

```bash
sudo rm -f /opt/wigul/secrets/firebase-adminsdk.json
```
