# 배송관리 구현 PRD 작성

상태: 완료

## 목표

출시 P0 배송관리를 구현할 수 있도록 화면, 상태 전이, 결제 취소 경합,
기존 주문 처리 방식과 인수 기준을 제품 명세로 작성한다.

## 하지 않는 일

이번 작업은 제품 명세 작성과 문서 인덱스 갱신 범위다. 기능 구현과 운영 정책
승인은 후속 작업이다.

## 현재 증거

- [출시 PRD 작성 기록](../completed/commerce-launch-prd.md)과
  [출시 명세](../../product-specs/commerce-launch-prd.md)를 확인했다.
- `OrderController`와 `OrderHistoryResponse`에 본인 주문 조회와 배송지 정보가 있다.
  배송 상태, 송장, 관리자 출고 화면은 구현되어 있지 않다.
- `PaymentTransactionalService`의 취소 준비는 결제·주문 잠금 안에서
  `CANCEL_PENDING`을 기록하며, 현재 출고 여부 검증은 없다.

## 결정

- 주문당 단일 송장과 운영자 수동 갱신을 첫 출시안으로 상세화한다.
- 결제 취소와 배송 전이의 서버 판정, 상태 변경 이력, 기존 주문의 출고 보류 기준을 명세에 적는다.
- 제품 명세 인덱스와 상위 출시 명세에서 세부 명세를 연결한다.

## 작업 분해

- [x] 상위 명세와 주문·결제 코드 대조
- [x] 배송관리 명세와 인수 기준 작성
- [x] 하네스 검증과 결과 기록

## 진행 상황

- 2026-11-15: 주문 조회, 재고 상태, 결제 취소 경계를 확인했다.
- 2026-11-15: 배송관리 세부 명세를 작성하고 제품 명세 인덱스·상위 명세에 연결했다.
- 2026-11-15: 문서, 아키텍처, 마이그레이션, 보안 검사를 확인했다. Gradle 소켓
  제한으로 집중 테스트와 전체 테스트 증거 생성이 차단됐다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | 기존 변경과 미추적 문서를 확인했다. |
| `./scripts/harness docs` | PASS | 링크, 실행 계획 및 생성 문서 검사를 통과했다. |
| `./scripts/harness architecture` / `./scripts/harness security` | BASELINED | 검토된 기존 예외가 유지됐다. |
| `./scripts/harness migrations` | PASS | Flyway 이력과 체크섬이 일치했다. |
| `./scripts/harness test --offline --tests '*InventoryIntegrationTest' --tests '*Payment*Test' --tests '*FlywayMigrationTest'` | BLOCKED | Gradle이 로컬 소켓을 열지 못해 테스트 시작 전에 중단됐다. |
| `./scripts/harness check` | FAIL | 위 환경 제한으로 test-baseline이 실패했고 eval에 사용할 현재 결과가 없었다. |
| `./scripts/harness smoke` | SKIPPED | 전체 검사에서 Compose 구성은 확인했고 실행 스택은 확인할 수 없었다. |
| `git diff --check` | PASS | 추적 파일 변경에 공백 오류가 없다. |

## 위험과 롤백

출고 기록이 없는 과거 주문은 출고 여부를 추정하지 않는 기준을 명세에 둔다.
문서 변경을 되돌릴 때 새 명세, 인덱스와 출시 명세 변경, 이 계획만 검토한다.

## 미해결 항목

택배사, 출고 기준, 기존 주문 대조 담당자 등 운영 결정을 제품 명세에 기록한다.
Gradle의 로컬 소켓 사용이 가능한 환경에서 테스트와 평가를 다시 실행한다.

## 완료 조건

- P0의 관리자 출고와 본인 배송 조회가 상태·권한·실패 동작까지 명세화된다.
- 직접 결제 취소, 동시 요청, 송장 정정, 과거 주문 검증 기준이 있다.
- 문서 검증과 전체 하네스 결과를 기록한다.
