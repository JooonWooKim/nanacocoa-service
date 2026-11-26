# 격리 MySQL 8.4 품질 검사 PRD

| 항목 | 내용 |
| --- | --- |
| 상태 | 준비 완료 · 구현 대기 |
| 우선순위 | P0 OPS-MYSQL-QUALITY · 기술부채 NC-DATA-002 |
| 기준 | 2026-11-26 원격 main `b3b8031a5624c5600727fb0aa3cb2bf316486368` |
| 이슈·브랜치 | [이슈 #54](https://github.com/JooonWooKim/nanacocoa-service/issues/54) · `feature/54` → `origin/feature/54` |
| 대상 | 변경을 검토하는 개발자와 배포 담당자 |

## 목표

PR과 main의 품질 검사에서 운영형 공용 Compose와 같은 MySQL 버전으로 DB 이행과 주문·재고·결제 동작을 확인한다. 기존 H2 검사는 유지한다. 지금까지의 [격리 MySQL 실행 절차](../runbooks/commerce-e2e.md)는 수동으로 실행되고, [PR #51](https://github.com/JooonWooKim/nanacocoa-service/pull/51)의 품질 workflow는 기본 H2를 실행한다. [PR #53](https://github.com/JooonWooKim/nanacocoa-service/pull/53)의 사전점검에서 사용하는 기술 증거에 MySQL 검사 결과를 연결할 수 있게 한다.

## 작업과 인수 기준

| 작업 항목 | 구현 범위 | 인수 기준 |
| --- | --- | --- |
| 격리 MySQL 실행 | 공용 Compose와 같은 `mysql:8.4.10`, 실행별 임시 컨테이너·동적 loopback 포트·테스트별 빈 DB | 실제 TCP 연결과 MySQL 버전을 확인한다. 기동 실패 또는 타임아웃은 검사 실패다. 실행이 만든 컨테이너만 정리한다. |
| DB 이행·JPA 검증 | 기존 회원·주문·가입 동의 이행, Flyway·JPA 스키마 검증 | 초기 버전에서 최신 버전까지 이행한 뒤 기존 데이터와 제약을 확인한다. MySQL 설정 누락으로 H2 검사가 대신 실행되면 MySQL 검사에 실패한다. |
| 주문·재고·결제 회귀 | 기존 재고·결제 운영·반품 통합 테스트 재사용 | 주문 재요청과 만료 시각 일치, 재고 경합·롤백, 모의 결제 복구·반품을 확인한다. |
| GitHub 검사와 증거 | 기존 Quality workflow에 독립 작업을 추가하고 로컬에 동일 실행 진입점 제공 | H2 게이트와 기준선은 유지한다. MySQL 실패와 미발견·skip을 별도 결과로 표시한다. 실패 증거는 검사 상태, 테스트 집계와 식별자로 제한한다. |

## 실행 계약

- 로컬과 CI가 같은 실행 스크립트를 사용한다. 스크립트가 MySQL 컨테이너를 직접 만들고 빈 DB를 생성한 뒤, 이 실행에서 받은 포트로 JDBC 주소를 구성한다. 외부에서 지정한 DB 주소나 운영 자격 증명을 사용하지 않는다. 테스트 기본 설정을 구성할 때 공용 초기화 경로를 재사용한다.
- MySQL의 3306 포트를 호스트의 동적 loopback 포트에만 바인딩한다. MySQL 준비 확인은 실제 테스트 접속 경로와 서버 버전을 확인하고 유한한 시간 내에 종료한다. 테스트, 타임아웃, 중단 후 임시 DB는 이 실행이 생성한 컨테이너 ID에 한해 정리한다. 다른 프로젝트나 볼륨은 건드리지 않는다.
- MySQL 실행은 최신 커밋의 테스트를 직접 실행하고 필수 테스트 클래스의 적어도 한 건이 결과에 포함됐는지 확인한다. 다음 표의 환경 변수를 실행 스크립트 안에서 모두 설정한다. 기본 H2 동작은 그대로 유지한다.
- GitHub의 일반 PR과 main push에서 별도 작업을 실행한다. Gradle 종료 코드, 테스트 실패·누락·skip, 정리 실패가 검사 성공으로 바뀌지 않게 한다. 실패 artifact에는 허용된 상태·집계·검증된 테스트 식별자만 포함한다. 원본 로그·JUnit XML·JDBC URL·자격 증명·고객 정보를 업로드하지 않는다.

| 테스트 | 전용 빈 DB에 연결하는 기존 변수 |
| --- | --- |
| `FlywayMigrationTest` | `SUBMISSION_MIGRATION_TEST_DB_URL` |
| `FilePolicyConsentMigrationTest` | `POLICY_FILE_MIGRATION_DB_URL` |
| `InventoryIntegrationTest` | `INVENTORY_TEST_DB_URL` |
| `PaymentOperationsIntegrationTest` | `PAYMENT_OPERATIONS_TEST_DB_URL` |
| `ReturnRequestIntegrationTest` | `RETURNS_TEST_DB_URL` |

Flyway 이력과 기존 데이터는 이행 테스트에서 검증한다. Flyway 적재 뒤 JPA의 `ddl-auto=validate` 기동은 기존 MySQL 통합 테스트에서 확인한다. 적용된 마이그레이션 파일은 유지한다.

## 후속 검증

- 깨끗한 체크아웃과 합성 데이터로 정상 MySQL 검사와 기존 H2 전체 하네스를 실행한다.
- Docker 미기동, MySQL 준비 시간 초과, 테스트 실패·누락·skip, 중단·정리 경로를 각각 검증한다. 임의의 호스트 포트와 다른 컨테이너에 영향이 없는지 확인한다.
- 합성 실패에서 안전한 요약만 남는지 확인하고 GitHub 실행 링크와 분류를 기록한다. GitHub 실행 증거가 없으면 그 항목은 BLOCKED로 기록한다.
- 운영 DB, 실제 Toss Payments·S3, 운영 배포 설정과 브랜치 보호 변경은 이 작업 범위 밖이다.

- [커머스 출시 PRD](commerce-launch-prd.md)
- [CI 계약](../runbooks/ci.md)
- [기술부채 추적기](../exec-plans/tech-debt-tracker.md)
- [기준 main 품질 검사](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45762640926)
- [준비 실행 기록](../exec-plans/completed/mysql-quality-gate-preparation.md)
