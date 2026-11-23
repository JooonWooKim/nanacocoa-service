# 사이트 알림 운영·검증

사이트 알림은 결제 완료, 정상 출고 및 확정 환불을 내 주문과 연결하고, 문의 답변 알림을 내 문의와 연결한다. 운영자는 재고 관리 화면의 **알림 운영**에서 실패와 시도 횟수를 확인한다.

## 설정과 복구

| 설정 | 기본값 | 동작 |
| --- | --- | --- |
| `notifications.enabled` | `false` | 게시 작업 활성 여부 |
| `notifications.starts-at` | 활성화 시 필수 | `2026-11-23T00:00:00Z` 형식의 UTC 기준. 기준 시각 포함 |
| `notifications.support.enabled` | `false` | 전체 게시 작업이 켜져 있을 때 문의 답변 게시 활성 여부 |
| `notifications.support.starts-at` | 문의 기능 활성화 시 필수 | 주문 기준과 별도의 UTC 시작 시각. 기준 시각 포함 |
| `notifications.initial-delay` | `30000` | 첫 실행 대기 시간(ms) |
| `notifications.fixed-delay` | `30000` | 실행 완료 후 다음 실행까지의 대기 시간(ms) |

활성화 전에 V17 마이그레이션이 적용됐는지 확인하고 기준 시각을 확정한다. 기준 시각이 없거나 유효하지 않으면 활성 설정으로 앱이 시작되지 않는다. 게시 작업을 일시 중단했다가 재개할 때는 기준 시각을 유지한다. 같은 기준에서 미처리 확정 기록을 다시 읽는다. V16 전에 확정된 거래와 V17 전에 확정된 문의 답변의 알림용 시각은 비어 있다. 문의 게시를 중단하는 동안에도 새 답변의 확정 기록은 남는다.

게시가 3회 실패하면 **실패** 상태로 남는다. 복구 후 실패 목록의 **재시도 요청**으로 해당 알림을 대기 상태로 돌린다. 작업자가 다음 주기에 처리하며 누적 시도 횟수와 마지막 실패 코드는 보존한다. 거래 상태 확인은 주문 상세와 기존 결제 운영 화면을 사용한다. 작업 로그에는 비식별 오류와 내부 알림 ID만 남긴다.

## 격리 검증

- `./gradlew test --tests '*OrderNotification*'`의 기본 DB는 H2다. MySQL 8.4 검증 시 `NOTIFY_TEST_DB_URL`, `NOTIFY_MIGRATION_DB_URL`에 루프백 주소의 일회성 격리 DB 두 개를 지정한다. 마이그레이션용 DB는 비어 있어야 한다. 콘솔·로그에 운영 접속 정보나 고객 데이터를 남기지 않는다.
- 합성 HTTP 검증은 `SPRING_APPLICATION_JSON='{"notifications":{"enabled":true,"starts-at":"2000-01-01T00:00:00Z","initial-delay":500,"fixed-delay":500}}' ./gradlew commerceE2e`를 시작해 `E2E_READY`를 확인한 뒤 진행한다.
- 같은 합성 런타임에 `python3 -B src/test/python/commerce_e2e_http.py > <임시_JSON_경로>`를 실행한다. 그 JSON을 `python3 -B src/test/python/order_notifications_e2e_http.py <임시_JSON_경로>`에 전달해 확정 알림, 비대상 제외, 본인 읽음, 주문 링크를 대조한다.
- 브라우저에서 합성 구매자로 로그인하고 **내 주문 → 알림함**을 열어 결제·출고·환불 유형과 미읽음 수를 확인한다. 한 건을 읽음으로 처리하고 해당 주문 상세가 열리는지 확인한다.

- 문의 집중 검증에는 `--tests '*SupportReplyNotification*'`을 추가한다. V16→V17의 격리 MySQL 이행에는 빈 별도 DB의 `SUPPORT_NOTIFY_MIGRATION_DB_URL`을 사용한다.
- 합성 런타임의 JSON 설정에서 `notifications.support.enabled=true`와 `notifications.support.starts-at`도 지정한 뒤 `python3 -B src/test/python/support_reply_notification_e2e_http.py`를 실행한다. Chrome에서 **내 문의에서 확인**과 로그인 복귀를 확인한다.

[주문 상태 알림 PRD](../product-specs/order-notification-prd.md) · [1:1 문의 답변 알림 PRD](../product-specs/support-reply-notification-prd.md)
