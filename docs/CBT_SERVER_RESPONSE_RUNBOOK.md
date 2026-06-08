# CBT 서버 대응 runbook

## 목적

안드로이드 비공개 테스트 중 서버 상태를 빠르게 확인하고, 사용자 제보를 서버 로그와 연결하기 위한 최소 절차다.

## 헬스체크

```bash
curl -i https://<QA_API_HOST>/health
```

정상 기준:

- HTTP `200`
- 응답 body의 `status`가 `UP`
- 응답 header에 `X-Request-Id`가 존재

## Request ID 확인

서버는 모든 응답에 `X-Request-Id`를 내려준다.
클라이언트가 `X-Request-Id`를 보내면 안전한 값에 한해 그대로 재사용하고, 없거나 안전하지 않은 값이면 서버가 UUID를 생성한다.

테스터 제보를 받을 때 최소한 아래 정보를 함께 받는다.

- 발생 시각
- 화면/행동
- 로그인 계정 또는 userId
- `X-Request-Id`
- 증상 설명과 스크린샷

## 서버 로그 확인

QA 서버에 접속한 뒤 최근 로그를 확인한다.

```bash
ssh <GCP_VM_USER>@<GCP_VM_HOST>
sudo journalctl -u wigul-backend -n 200 --no-pager
sudo journalctl -u wigul-backend --since "30 minutes ago" --no-pager
```

특정 제보의 `requestId`가 있으면 해당 값으로 검색한다.

```bash
sudo journalctl -u wigul-backend --since "2 hours ago" --no-pager | grep "<X-Request-Id>"
```

로그 패턴에는 `requestId`와 가능한 경우 `userId`가 포함된다.

## 서비스 상태 확인

```bash
systemctl status wigul-backend --no-pager
```

## 수동 재시작

자동 배포 workflow와 같은 서비스명을 사용한다.

```bash
sudo -n systemctl restart wigul-backend
systemctl status wigul-backend --no-pager
curl -i https://<QA_API_HOST>/health
```

`sudo -n`이 비밀번호 요구로 실패하면 배포 계정의 sudoers 설정을 먼저 확인한다.

## 범위 밖

- Android Crashlytics/Sentry 연동
- 치명 오류 알림 자동화
- 상세 모니터링 대시보드
- 자동 롤백/복구
