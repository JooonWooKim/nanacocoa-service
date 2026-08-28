# Toss 재인증 멱등성 키 갱신

상태: 완료

## 목표

실패가 확정된 결제를 새 Toss 인증으로 재시도할 때 새 `paymentKey`와 함께 새
멱등성 키를 사용하고, 동일 승인 요청의 재호출은 기존 키를 유지합니다.

## 하지 않는 일

- 로그인 세션 독립 서버 callback으로 결제 흐름을 재설계하지 않습니다.
- 결제 API, DB 스키마, 백엔드 상태 전이를 변경하지 않습니다.

## 현재 증거

- `retryTossPayment`는 새 Toss 인증을 열면서 저장된 멱등성 키를 재사용했습니다.
- 승인 fingerprint는 회원, 주문, `paymentKey`, 금액을 포함하므로 새
  `paymentKey`와 이전 키의 조합은 `IDEMPOTENCY_KEY_REUSED`로 거부됐습니다.

## 결정

- Toss SDK 준비 후 새 인증창을 열기 직전에 멱등성 키를 회전하고 저장합니다.
- 이전 결제 시도의 `paymentId`를 제거하되 주문과 구매자 문맥은 유지합니다.
- 동일 성공 callback의 승인 재호출에는 키를 회전하지 않습니다.

## 작업 분해

- [x] 조사
- [x] 구현
- [x] 검증
- [x] 리뷰와 문서화

## 진행 상황

- 2026-08-22: 리뷰 원인과 백엔드 fingerprint·실패 상태 복원 규칙을 확인했습니다.
- 2026-08-22: 재인증 키 갱신과 기존 정적 페이지 통합 테스트의 회귀 계약을
  추가했습니다.
- 2026-08-22: 결제 집중 테스트와 68개 전체 테스트 기준선을 검증하고 결과를
  분류했습니다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `./gradlew test --tests 'com.nanacocoa.server.NanacocoaServerApplicationTests.servesStaticPages'` | PASS | 재인증·동일 callback 멱등성 스크립트 계약 통과 |
| `./scripts/harness test-unit` | PASS | 주문·결제·상품 집중 테스트 통과 |
| `./scripts/harness test` | FAIL | 68개 중 NC-TEST-001의 기존 인증·상태 테스트 9개 실패 |
| `./scripts/harness test-baseline` | BASELINED | 68개, skip 0개, 기존 실패 fingerprint 9개 유지 |
| `./scripts/harness check` | FAIL | 브랜치에 없는 환경 파일로 기존 환경·보안·smoke 게이트 실패, 변경 관련 게이트는 통과 |

## 위험과 롤백

키를 너무 자주 바꾸면 동일 승인 재호출의 멱등성이 깨질 수 있으므로 새 Toss
인증 경로에서만 교체합니다. 롤백은 이 작업의 스크립트·테스트·문서 변경만
되돌립니다.

## 미해결 항목

- `.env.example`, `compose.yaml`, `application.yml` 부재에 따른 기존 check
  실패는 이 결제 리뷰 범위 밖이며 별도 작업에서 복구해야 합니다.

## 완료 조건

- 재인증 회귀 계약과 결제 집중 테스트가 통과했습니다.
- 전체 테스트 기준선은 새 실패 없이 유지됐고 check 실패는 기존 환경 파일
  부재로 분류했습니다.
