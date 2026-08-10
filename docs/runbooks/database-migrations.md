# 데이터베이스 마이그레이션 운영 절차

상태: 검증됨

`src/main/resources/db/migration` 아래 Flyway SQL이 운영 스키마 이력입니다.
현재 파일은 V1부터 V3까지이며 MySQL 시작 시 적용되고 JPA는
`ddl-auto=validate`를 사용합니다.

## 마이그레이션 추가

1. 다음 순번의 `V<버전>__<설명>.sql`을 추가합니다.
2. 등록된 기존 마이그레이션을 수정·이름 변경·순서 변경·삭제하지 않습니다.
3. 다음을 실행합니다.

```sh
./scripts/harness migrations
./scripts/harness migrations --accept-new
./scripts/harness generate-docs
./gradlew test --tests 'com.nanacocoa.server.migration.*'
./scripts/harness check
```

첫 번째 명령은 등록되지 않은 새 파일을 보고해야 합니다. `--accept-new`는
기존 checksum 변경을 거부하고 새 파일만 등록합니다. 커밋 전에 checksum
diff를 검토합니다.

## 기준 정보

- 마이그레이션 SQL: `src/main/resources/db/migration`
- 무결성 등록부: `harness/baselines/migration-checksums.sha256`
- 생성된 판독용 이력: [db-schema.md](../generated/db-schema.md)
- JPA 호환성: `FlywayMigrationTest`, `JpaSchemaValidationTest`

`src/main/resources/schema-mysql.sql`은 Compose 운영 경로에서 적용되지 않으며
Flyway 이력을 대체하는 것으로 취급하면 안 됩니다.
