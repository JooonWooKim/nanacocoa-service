# 사이트 내 주문 상태 알림 구현

상태: 완료

## 목표

[이슈 #44](https://github.com/JooonWooKim/nanacocoa-service/issues/44)와 [주문 상태 알림 PRD](../../product-specs/order-notification-prd.md)의 결제·출고·환불 확정 알림, 회원 알림함과 운영자 재처리를 `feature/44`에 구현한다.

## 하지 않는 일

외부 채널 발송, 이력 소급, 실 PG·S3 검증, 운영 활성화와 배포는 이번 작업 범위 밖이다.

## 현재 증거

원격 main `99931a4`에 고객센터 PR #43이 반영됐다. 배송·결제 도메인의 기존 확정 경로와 합성 커머스 런타임을 재사용한다. 작업 시작 전 수정·미추적 파일 17개를 작업 트리 밖에 복사하고 SHA-256을 기록했다.

## 결정

거래 확정 시각을 기존 업무 트랜잭션에 기록한다. 작업자는 커밋된 확정 건을 찾아 DB 유일 제약과 행 잠금으로 한 번만 게시한다. 운영자 재시도는 알림 상태만 바꾼다.

## 작업 분해

- [x] PRD와 거래 확정 경로 확인
- [x] 확정 기록, 예약 작업, 회원·운영자 API와 화면 구현
- [x] 격리 검증과 하네스 계약 확인
- [x] 기존 작업 트리 보존 대조 및 출시 PRD 갱신

## 진행 상황

- 2026-11-23: V16과 회원·운영자 알림을 구현했다. 격리 DB의 확정·중복·실패 복구, 모의 PG 회귀와 Chrome 구매자 흐름을 확인했다.
- 2026-11-23: 전체 하네스를 실행했다. 백업 SHA-256과 대조한 17개 중 14개는 바이트 단위로 일치하고 함께 수정한 3개 PRD·인덱스에는 이번 기능의 상태와 증거만 반영했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `./gradlew test --tests '*OrderNotification*'` | PASS | H2·격리 MySQL 8.4 각각 4개, V15→V16 보존과 JPA 검증, 중복·페이지·읽음 경합·3회 실패·운영자 재시도 |
| `java -jar …` | PASS | 기본 비활성 앱 종료 후 동일한 격리 MySQL로 활성 앱 시작. 누락된 기준 시각 일치 건을 한 번만 게시 |
| `node src/test/js/order-notifications.test.cjs` | PASS | 읽음·복귀·계정 전환·메뉴 미읽음 4개 |
| `node --test src/test/js/*.test.cjs` | PASS | JavaScript 테스트 파일 10개 |
| `python3 -B src/test/python/commerce_e2e_http.py` | PASS | 합성 구매·승인 복구·환불 복구·검수·재입고 시나리오 6개 |
| `python3 -B src/test/python/order_notifications_e2e_http.py` | PASS | 확정 알림 11건, 승인 거절 제외, 소유권·CSRF·읽음·주문 링크와 모의 PG 호출 수 유지 |
| `Chrome · notifications.html` | PASS | 격리 로그인 복귀, 미읽음 10→9, 출고 알림에서 주문 상세 이동, 마이페이지 동일 미읽음 수 |
| `Chrome · notification-admin.html` | SKIPPED | 운영자 실패 주입과 재시도는 H2·MySQL API에서 검증 |
| `HARNESS_APP_PORT=18082 ./scripts/harness check` | BASELINED | Java 256개, 실패·오류·skip 0개. 평가 63개, smoke·문서·이행 검사 통과. 기존 구조 예외 3개·보안 위험 2개 유지 |
| `./scripts/harness docs && git diff --check` | PASS | 완료 실행 기록 이동 후 문서와 diff 재검증 |

## 위험과 롤백

처리 오류가 거래에 개입하지 않도록 트랜잭션을 분리한다. 장애 시 게시 작업을 끄고 확정 이력을 유지한다. 재개할 때 기존 활성화 기준을 사용한다. 적용한 Flyway 이력은 유지하고 스키마 조정이 필요하면 새 이행으로 진행한다.

## 미해결 항목

main 반영, 운영자의 UTC 활성화 기준 확정과 배포 환경 인수가 남아 있다.

## 완료 조건

확정 거래와 알림 증거를 연결하고, 테스트 및 하네스 결과를 분류하고, 변경 전후 사용자 문서와 출시 PRD를 확인한다.
