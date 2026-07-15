# 쇼핑 가져오기 요청 병합·단기 캐시 운영

## 동작

- 사용자별 job은 각각 생성한다.
- 동일한 `crawl_key`의 대표 job이 `PENDING` 또는 `RUNNING`이면 새 job은 follower로 연결한다.
- worker는 대표 job만 크롤링하고 성공·실패 결과를 follower 전체에 전파한다.
- 대표 job 완료 후 성공 결과는 5분, 실패 결과는 30초 동안 재사용한다.
- follower 응답의 `originalUrl`은 각 사용자가 제출한 원본 URL을 유지한다.

## 캐시 키

상품번호가 확실한 사이트만 `사이트 + 상품번호`를 사용한다.

- OliveYoung: `goodsNo`
- Musinsa, 29CM, KREAM, Bunjang: `/products/{id}`
- Ably: `/goods/{id}`
- Zigzag: `/catalog/products/{id}`

그 외 URL은 추적 파라미터를 제거하지 않은 원본 URL 전체의 SHA-256을 사용한다. 캐시 키 생성은 요청 URL이나 저장 URL을 변경하지 않는다.

## 환경변수

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `SHOPPING_IMPORT_COALESCING_ENABLED` | `true` | 활성 대표 job에 동일 상품 요청 병합 |
| `SHOPPING_IMPORT_CACHE_ENABLED` | `true` | 완료된 성공·실패 결과 단기 재사용 |
| `SHOPPING_IMPORT_CACHE_SUCCESS_TTL` | `PT5M` | 성공 결과 TTL |
| `SHOPPING_IMPORT_CACHE_FAILURE_TTL` | `PT30S` | 실패 결과 TTL |
| `SHOPPING_IMPORT_OLIVEYOUNG_INTERVAL_ENABLED` | `true` | 올리브영 요청 시작 간격 사용 여부 |
| `SHOPPING_IMPORT_OLIVEYOUNG_MIN_START_INTERVAL` | `PT2S` | 올리브영 요청 최소 시작 간격 |
| `SHOPPING_IMPORT_OLIVEYOUNG_MAX_START_INTERVAL` | `PT3S` | 올리브영 요청 최대 시작 간격 |
| `MANAGEMENT_SERVER_ADDRESS` | `127.0.0.1` | 관리 endpoint bind 주소 |
| `MANAGEMENT_SERVER_PORT` | `9090` | 관리 endpoint 포트 |

환경변수 변경 후 애플리케이션을 재시작해야 적용된다. 장애 시 병합과 캐시를 각각 `false`로 설정해 독립적으로 비활성화할 수 있다.

QA의 8080/8081 무중단 배포에서는 두 애플리케이션이 잠시 동시에 실행된다. systemd의 8080 본 서비스는 `9090`, 8081 standby는 workflow가 명시한 `9091`을 사용한다. 배포 완료 후 standby의 `8081`과 `9091`은 함께 종료된다.

직전 배포 실패로 Caddy가 8081 standby를 바라보는 경우 workflow가 먼저 복구한다. 8080을 임시 관리 포트 `9091`로 기동해 트래픽을 되돌리고 기존 standby를 종료한 뒤, 새 standby는 `9092`에서 검증한다. 최종 전환에서는 8080 본 서비스를 다시 `9090`으로 정리한다.

## Prometheus 지표

관리 endpoint는 기본적으로 서버 localhost에서만 접근한다.

```bash
curl -fsS http://127.0.0.1:9090/actuator/prometheus
```

| Micrometer 이름 | Prometheus 이름 | 태그 |
|---|---|---|
| `shopping.import.cache.lookups` | `shopping_import_cache_lookups_total` | `site`, `outcome=success_hit\|failure_hit\|miss` |
| `shopping.import.coalesced` | `shopping_import_coalesced_total` | `site` |
| `shopping.import.crawls` | `shopping_import_crawls_total` | `site` |
| `shopping.import.upstream.rejections` | `shopping_import_upstream_rejections_total` | `site`, `status=403\|429` |
| `shopping.import.queue.wait` | `shopping_import_queue_wait_seconds` | `site` |

사이트별 5분 캐시 적중률:

```promql
sum by (site) (rate(shopping_import_cache_lookups_total{outcome=~"success_hit|failure_hit"}[5m]))
/
sum by (site) (rate(shopping_import_cache_lookups_total[5m]))
```

사이트별 실제 크롤링 처리량:

```promql
sum by (site) (rate(shopping_import_crawls_total[5m]))
```

사이트별 403·429:

```promql
sum by (site, status) (increase(shopping_import_upstream_rejections_total[5m]))
```

사이트별 큐 대기 p95:

```promql
histogram_quantile(
  0.95,
  sum by (le, site) (rate(shopping_import_queue_wait_seconds_bucket[5m]))
)
```

## 운영 확인

1. `crawls`가 요청 수보다 줄었는지 확인한다.
2. `coalesced`와 cache hit가 증가하는지 확인한다.
3. 403·429가 증가하면 해당 사이트의 실제 크롤링 수와 함께 확인한다.
4. cache hit 없이 큐 대기 p95만 증가하면 서로 다른 상품 요청이 처리량보다 빠르게 유입된 상황이다.
5. 잘못된 상품 결과 공유가 의심되면 캐시를 먼저 끄고, 계속되면 요청 병합도 끈다.
