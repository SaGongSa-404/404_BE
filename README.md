# 위굴 Backend

위굴 MVP 도메인과 DB 스키마 기준선을 검증하기 위한 Spring Boot 백엔드입니다.
초기 기준선에서는 API 기능 구현보다 Flyway 마이그레이션, JPA 엔티티, enum, 스키마 검증 테스트를 중심으로 둡니다.

## Stack

- Java 21
- Spring Boot 3.5.11
- Spring Data JPA
- Flyway
- PostgreSQL
- Testcontainers
- Gradle Wrapper

## Run

테스트를 실행하면 Flyway 마이그레이션과 JPA 스키마 검증이 함께 수행됩니다.
기본 실행 설정과 테스트 모두 PostgreSQL을 기준으로 합니다.

```bash
./gradlew test
```

## Notes

- 기준 문서: `docs/DOMAIN_SCHEMA_REVISED_FROM_PLANNING_2026_04_16.md`
- DB 기준선: `src/main/resources/db/migration/V1__domain_schema_baseline.sql`
- 스키마 확인 테스트: `src/test/java/com/sagongsa/backend/domain/DomainSchemaSmokeTest.java`
