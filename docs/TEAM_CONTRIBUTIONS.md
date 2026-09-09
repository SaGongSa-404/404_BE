# 백엔드 기여 상세

[프로젝트 README](../README.md#팀과-기여)

2026년 9월 9일 확인한 병합 PR과 리뷰를 기준으로 정리했습니다. 구현한 기능과 리뷰로 보완한 내용을 구분하며, PR 수를 기여 비율로 환산하지 않습니다.

## 박재홍 · [PHJ2000](https://github.com/PHJ2000)

초기 애플리케이션·데이터 모델 구성부터 핵심 API, 외부 상품 수집, 알림, 테스트·배포 환경과 운영 안정화까지 구현했습니다.

| 영역 | 구현한 내용 | 주요 PR |
| --- | --- | --- |
| 프로젝트 기반 | Spring Boot·JPA·Flyway 구성, MVP 도메인·스키마와 DB 제약, PostgreSQL 스키마 테스트 | [#6](https://github.com/SaGongSa-404/404_BE/pull/6) |
| 인증·사용자 | 카카오·구글 OAuth2, JWT 발급·갱신, 앱 리다이렉트 검증, 온보딩 저장 | [#10](https://github.com/SaGongSa-404/404_BE/pull/10), [#15](https://github.com/SaGongSa-404/404_BE/pull/15) |
| 소비 의사결정 | 위시 상품 저장·조회, 홈 요약, 구매 결정과 예산 반영, 기회비용 계산 | [#14](https://github.com/SaGongSa-404/404_BE/pull/14), [#16](https://github.com/SaGongSa-404/404_BE/pull/16), [#20](https://github.com/SaGongSa-404/404_BE/pull/20), [#184](https://github.com/SaGongSa-404/404_BE/pull/184) |
| 상품 수집 | 링크 미리보기·브라우저 렌더링, 사이트별 상품값 추출 보완, 영속 작업 큐, 요청 병합·캐시 | [#8](https://github.com/SaGongSa-404/404_BE/pull/8), [#55](https://github.com/SaGongSa-404/404_BE/pull/55), [#200](https://github.com/SaGongSa-404/404_BE/pull/200), [#191](https://github.com/SaGongSa-404/404_BE/pull/191), [#224](https://github.com/SaGongSa-404/404_BE/pull/224) |
| 알림 | 구매 7일 후 리마인더 worker, FCM 연동·기기 토큰 관리, 기획 알림 발송 조건 연결 | [#25](https://github.com/SaGongSa-404/404_BE/pull/25), [#115](https://github.com/SaGongSa-404/404_BE/pull/115), [#156](https://github.com/SaGongSa-404/404_BE/pull/156) |
| 검증·배포 | 서비스/API 회귀 테스트, CI·커버리지, QA 시나리오 데이터, CD·standby 배포, 요청 추적·복구 절차 | [#91](https://github.com/SaGongSa-404/404_BE/pull/91), [#90](https://github.com/SaGongSa-404/404_BE/pull/90), [#137](https://github.com/SaGongSa-404/404_BE/pull/137), [#145](https://github.com/SaGongSa-404/404_BE/pull/145), [#174](https://github.com/SaGongSa-404/404_BE/pull/174), [#154](https://github.com/SaGongSa-404/404_BE/pull/154) |
| 성능·보안·구조 개선 | 마이페이지 N+1 제거, API·인증 요청 제한, 운영 환경의 개발 기능 차단, 이미지 URL 검증, 트랜잭션·작업 소유권 보호 | [#246](https://github.com/SaGongSa-404/404_BE/pull/246), [#253](https://github.com/SaGongSa-404/404_BE/pull/253), [#257](https://github.com/SaGongSa-404/404_BE/pull/257), [#260](https://github.com/SaGongSa-404/404_BE/pull/260) |

## Jung Daeun · [ekdmssld](https://github.com/ekdmssld)

소셜 피드와 마이페이지 API를 구현하고, 소비기록·결정 수정, 신고 기능과 서비스/API 테스트를 보강했습니다.

| 영역 | 구현한 내용 | 주요 PR |
| --- | --- | --- |
| 소셜 피드 | 게시글·댓글·투표 API, 커서 페이징, 이미지 업로드, 신고 사유·프로필 신고 | [#33](https://github.com/SaGongSa-404/404_BE/pull/33), [#109](https://github.com/SaGongSa-404/404_BE/pull/109) |
| 마이페이지·소비기록 | 프로필·예산·알림 설정, 소비 통계, 월별 소비기록 조회와 결정 수정 | [#39](https://github.com/SaGongSa-404/404_BE/pull/39), [#60](https://github.com/SaGongSa-404/404_BE/pull/60) |
| 사용자 흐름 보완 | 숙려 자가점검 문항, 구매 결정 재시도 처리, 온보딩 닉네임 저장 | [#34](https://github.com/SaGongSa-404/404_BE/pull/34), [#88](https://github.com/SaGongSa-404/404_BE/pull/88), [#124](https://github.com/SaGongSa-404/404_BE/pull/124) |
| 테스트 | 소셜 피드·마이페이지·숙고 API 통합 테스트, 위시리스트 등 서비스 단위 테스트 | [#103](https://github.com/SaGongSa-404/404_BE/pull/103), [#97](https://github.com/SaGongSa-404/404_BE/pull/97), [#98](https://github.com/SaGongSa-404/404_BE/pull/98), [#78](https://github.com/SaGongSa-404/404_BE/pull/78) |

## 구매 결정 재시도 리뷰

구매 결정 재시도 처리는 ekdmssld가 구현했습니다. PHJ2000은 리뷰에서 기존 결정 조회보다 상품 상태 검사가 먼저 실행되어 여전히 409가 날 수 있는 경로를 짚고, 검사 순서 변경과 중복 요청 테스트를 제안했습니다.

- [기존 결정 확인을 먼저 수행하도록 제안한 리뷰](https://github.com/SaGongSa-404/404_BE/pull/88#discussion_r3310378441)
- [같은 요청의 결과 ID와 저장 건수 검증을 제안한 리뷰](https://github.com/SaGongSa-404/404_BE/pull/88#discussion_r3310380979)
