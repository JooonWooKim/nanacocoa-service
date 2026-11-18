# 결제 환경 운영 절차

## 로컬과 테스트

기본 애플리케이션과 Compose는 실제 결제와 자동 복구를 끕니다.
`TOSS_PAYMENTS_ENABLED=false`인 경우 키나 주소가 설정돼 있어도 HTTP 결제
어댑터를 만들지 않습니다. checkout은 결제를 시작하기 전에 서버 설정을
확인하고, 사용할 수 없으면 SDK와 주문을 만들기 전에 안내합니다.

자동화는 `TossPaymentsClient` 모의 응답과 `MockRestServiceServer`를 사용합니다.
외부 연결 없이 HTTP 요청과 타임아웃 분류, H2의 결제·재고 상태와 복구를 확인합니다.

```sh
./gradlew test --tests 'com.nanacocoa.server.payment.*' --tests '*InventoryIntegrationTest'
./scripts/harness check
```

## 운영에서 활성화

| 설정 | 운영 값 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Compose에서 `mysql,prod`. 직접 실행 시에도 기존 데이터 프로필에 `prod`를 추가합니다. `test`와 함께 활성화할 수 없습니다. |
| `TOSS_PAYMENTS_ENABLED` | `true`를 명시해야 활성화됩니다. |
| `TOSS_PAYMENTS_BASE_URL` | `https://api.tosspayments.com`. 항목을 생략하면 같은 공식 주소를 사용합니다. 기존 로컬 loopback 값은 운영 활성화 전에 제거하거나 공식 주소로 바꿉니다. |
| `TOSS_PAYMENTS_CLIENT_KEY`, `TOSS_PAYMENTS_SECRET_KEY` | 현재 checkout의 결제창에 맞는 같은 상점의 API 개별 연동 라이브 키(`live_ck_`, `live_sk_`)를 사용합니다. 시크릿 키는 서버에만 주입합니다. |
| `PAYMENT_RECONCILIATION_ENABLED` | 자동 복구를 운영할 때 `true`를 명시합니다. 결제 연동도 활성화되어 있어야 실행됩니다. |

프로필, 주소, 키 유형이 맞지 않으면 시작을 거부합니다. 시작 검증은 결제사에
요청하지 않으므로 두 키가 같은 상점에 속하는지, 계약이 유효한지는 운영자가
확인해야 합니다. [Toss Payments SDK 환경 설정](https://docs.tosspayments.com/sdk/v2/js/environment)을 참고합니다.

승인·취소를 멈출 때는 `TOSS_PAYMENTS_ENABLED=false`로 바꾸고 재시작합니다.
본인 주문·결제 조회는 계속 가능하지만 처리 대기 중인 거래는 자동 복구되지
않습니다. 재활성화 전 미확정 거래를 대조하고, 복구에 같은 상점의 키를 사용합니다.
출시용 화면 종단 검증과 미확정 거래 운영 경로는
[커머스 출시 PRD](../product-specs/commerce-launch-prd.md)의 PAY 후속 작업입니다.

## 기존 로컬·배포 설정에 반영

공개 저장소에서 제외되는 `application.yml`, `compose.yaml`, `.env.example`은
Git 브랜치 전환으로 갱신되지 않습니다. 기존 환경에 반영할 때는 결제 항목만
아래처럼 수정하고 나머지 설정과 자격 증명을 보존합니다.

- `application.yml`: `toss-payments.enabled`에 `${TOSS_PAYMENTS_ENABLED:false}`를
  추가하고 `payment.reconciliation.enabled`의 기본값을
  `${PAYMENT_RECONCILIATION_ENABLED:false}`로 바꿉니다.
- `compose.yaml`의 `app.environment`: `TOSS_PAYMENTS_ENABLED`를
  `${TOSS_PAYMENTS_ENABLED:-false}`로 전달하고, 기존 복구 플래그를
  `${PAYMENT_RECONCILIATION_ENABLED:-false}`로 바꿉니다.
  `SPRING_PROFILES_ACTIVE`는 `${SPRING_PROFILES_ACTIVE:-mysql}`로 전달합니다.
- `.env.example`: `SPRING_PROFILES_ACTIVE=mysql`,
  `TOSS_PAYMENTS_ENABLED=false`, `PAYMENT_RECONCILIATION_ENABLED=false`를
  기본값으로 둡니다. 키 항목은 비워 두고 이미 생성된 `.env`는 변경하지 않습니다.

## 미확정 결제·환불 운영 화면

관리자는 `payment-operations.html`에서 미확정 승인·보상·취소를 주문 번호와 유형으로
조회한다. 미래 재시도 대상도 표시되며 완료 거래는 상세 URL에서 확인할 수 있다.
조회에는 PG 호출이 없다. 각 거래의 오류 안내, 주문·결제·재고 상태와 복구 예정
시각을 확인한다. 시각 누락은 운영 점검 대상이며 DB를 임의로 수정해 실행하지 않는다.

개별 복구는 PG 조회 후 기존 승인 재시도·보상·환불이 실행될 수 있다. 결제 연동을
활성화한 환경에서만 사용하고 자동 복구 플래그는 수동 실행을 차단하지 않는다.
성공 안내 후 저장된 결과를 확인한다. 확인 중이면 예정 시각 이후 다시 조회한다.
409는 처리 경합 또는 재시도 보류, 503은 결제 비활성 또는 잠금 서비스 장애를
의미한다. 미확정 결과만으로 환불 완료·재입고를 판단하지 않는다.

통신 오류로 실행 응답을 잃으면 상태 다시 조회를 사용한다. 자동 재제출은 하지 않는다.
화면·증거에 키·개인정보를 복사하지 않는다. 수동 실행 로그는 운영자 내부 ID,
거래 유형·ID, 시각과 결과 분류만 제공한다. 로그는 영구 감사 원장을 대체하지 않는다.
출시 전 점검 담당자와 고객 문의·이관 절차를 확정한다.

검증 명령(외부 PG 연결 없음):

```sh
./gradlew test --tests '*PaymentOperationsIntegrationTest' --tests 'com.nanacocoa.server.payment.*'
node --test src/test/js/payment-operations.test.cjs
./scripts/harness check
```

임시 MySQL 8.4 검증은 별도 가상 DB URL을 `PAYMENT_OPERATIONS_TEST_DB_URL`로
지정한다. 테스트는 빈 비밀번호를 쓰는 격리된 임시 root DB만 대상으로 하며
운영·공유 DB URL을 사용하지 않는다. 테스트 프로필에서는 결제 제공자를 모의한다.
