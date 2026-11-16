# 이슈 #12 배송관리 구현

상태: 완료

## 목표

운영자 출고·송장 정정·배송 완료와 구매자 주문 상세 배송 조회를 제공한다.

## 하지 않는 일

첫 출시 범위는 단일 송장과 수동 갱신이다. 반품 처리는 후속 작업이다.

## 현재 증거

[배송관리 구현 PRD](../../product-specs/shipping-management-prd.md), 주문·결제 서비스, V5 스키마를 확인했다.

## 결정

주문 잠금에서 출고와 결제 취소 시작을 직렬화한다. 기존 주문은 V6에서 배송 확인 대상을 표시하고 운영자 확인 후 처리한다. 택배사와 조회 주소는 운영 환경에서 설정한다.

## 작업 분해

- [x] PRD와 현재 코드 대조
- [x] DB·서버·관리자·구매자 화면 구현
- [x] 집중 검증과 하네스 결과 기록

## 진행 상황

- 2026-11-16: feature/12 작업 트리와 주문·결제 경계를 확인했다.
- 2026-11-16: V6, 관리자 API·화면, 구매자 조회와 결제 취소 판정을 구현했다.
- 2026-11-16: H2·MySQL 집중 테스트, 모의 API Chrome 검증과 전체 하네스를 완료했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | feature/12와 기존 사용자 문서 변경을 확인했다. |
| `./scripts/harness migrations --accept-new` | PASS | V6를 새 버전으로 등록했다. |
| `./scripts/harness test --offline --tests '*InventoryIntegrationTest' --tests '*Payment*Test' --tests '*FlywayMigrationTest' --tests '*JpaSchemaValidationTest'` | PASS | H2에서 승인·취소·출고 경합, 권한, 롤백, V6를 검증했다. |
| `./scripts/harness test --offline --tests '*InventoryIntegrationTest'` | PASS | 격리된 MySQL 8.4.10에서 Flyway·JPA와 재고·배송 통합 테스트를 검증했다. |
| `node --check src/main/resources/static/shipping-admin.js` | PASS | 관리자 스크립트 구문을 검사했다. |
| `Chrome headless` | PASS | 모의 API로 출고·정정·완료와 구매자 상세의 최신 송장을 확인했다. |
| `./scripts/harness check` | BASELINED | 테스트 126개 중 기존 실패 5개 유지. SHIP 평가 3개와 문서·마이그레이션 검사 통과. |
| `git diff --check` | PASS | 공백 오류가 없다. |

## 위험과 롤백

출고와 취소가 동시에 확정되면 오배송 또는 환불 손실이 생길 수 있다. DB에 출고 기록을 남긴 운영 환경에서는 구버전이 취소 제한을 모르는 상태로 재가동되지 않도록 쓰기를 중단하고 대조한다.

## 미해결 항목

운영 택배사 및 출고 정책은 실제 운영 설정과 승인으로 확정한다. 브라우저는 모의 API, 서버 동작은 H2·MySQL 통합 테스트로 검증했다. 운영 공개 전 SEC·RETURN 연동을 검증한다.

## 완료 조건

PRD의 관리자·구매자 흐름, 권한, 재전송·타임아웃·경합 및 기존 주문 처리 결과를 검증한다.
