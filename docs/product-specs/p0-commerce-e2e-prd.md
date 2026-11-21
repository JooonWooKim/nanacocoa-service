# P0 구매·환불·재입고 종단 검증 PRD

| 항목 | 내용 |
| --- | --- |
| 상태 | 구현된 P0 기술 시나리오 검증 완료 · Safari 실제 확인창·배송/반품 종단 PASS · 운영 정책 미승인 |
| 기준일 | 2026-11-21 원격 main `afc8cc293975d5cdfd971a0822ca810267aa5e2d` 확인 |
| 대상 | 구매 회원, 배송·반품·결제 운영자, 출시 검증 담당자 |
| 추적 | [이슈 #32](https://github.com/JooonWooKim/nanacocoa-service/issues/32), 로컬 `feature/32` → `origin/feature/32` · 문서 로컬 미커밋 |
| 관련 작업 | RETURN 이슈 #26·PR #27, 결제 운영 이슈 #22·PR #23, 상태 확인 PR #31 |
| 준비 증거 | [준비 실행 기록](../exec-plans/completed/p0-commerce-e2e-preparation.md) · 191개 테스트 PASS, check BASELINED, smoke SKIPPED |

## 목표와 범위

### 2026-11-21 잔여 검증 완료

Safari 네이티브 제어로 Chrome 도구의 확인창 연결 장애를 우회했다. 제품의 확인창을
대체하거나 보안 설정을 완화하지 않았다. 실제 확인창 취소/수락과 서버 반영을 대조해
기존 BLOCKED를 해소했다. 과거 미확인 기록은 실행 기록에 보존한다.

- 구매 → 출고 → 배송 완료 → 본인 반품 신청 → 승인 → 회수 시작/완료 → 검수 →
  환불 → 재입고 → 구매자 재로그인·결과 조회를 모두 화면 조작으로 통과했다.
- 환불·재입고 확인창 취소는 상태·재고를 변경하지 않았고, 수락은 각각 한 번 반영됐다.
- 환불 확인 중에는 재입고 작업이 없고, 예정 시각 전 개별 복구는 비활성이다.
  예정 시각 후 복구 확인창을 수락해 같은 거래가 완료됐으며 새 환불 호출은 없었다.
- 확정 실패 후 같은 사유의 명시적 환불 재시도는 성공했다. 검수 불합격 상품은
  환불 완료 후에도 재입고 작업이 없고 재판매 불가로 표시된다.
- 고객/관리자 응답, 비식별 DB snapshot과 모의 PG 호출 횟수를 대조했다.
  반려 후 동일 사유 재신청과 응답 유실 재전송의 서버 통합 회귀 1개도 추가해 통과했다.

[검증 운영 절차](../runbooks/commerce-e2e.md)에 재현 방법을 기록한다.
아래 시나리오별 결과는 브라우저·실제 HTTP·Java/JS 검증을 합친 결과이며 모든 실패/경합
조합을 브라우저에서만 반복했다는 의미는 아니다. 실제 PG·S3·운영 정책·운영 Compose
배포 검증은 이 기술 검증의 통과 범위에 포함하지 않는다. main 반영과 운영 활성화는 별도다.

아래 준비 범위와 후속 시나리오는 준비 당시의 요구사항이며 위 실행 결과와 구분한다.

기존 P0 구매·주문·배송·취소·반품·환불·복구·재입고를 하나의 사용자 흐름에서 검증한다.
특히 기존 RETURN 기록에서 도구 연결 중단으로 BLOCKED인 환불 확인창 이후의
구매자·운영자 화면과 최종 DB 상태를 확인한다. 준비 완료는 검증 완료나 출시 승인이 아니다.

이번 준비에서는 PRD·이슈·추적 브랜치와 준비 검사 증거만 작성한다.
실제 시나리오 실행, 테스트 실행 환경·fixture 보강 및 발견 결함 수정은 후속 요청이다.
새 공개 API·응답 형식·DB 스키마·Flyway 버전은 계획하지 않는다.
장바구니·부분 환불·교환·자동 회수 연동·관측성 확장·백업 복원 자동화는 이 검증 범위 밖이다.

## 기존 계약과 준비 당시 증거

- [출시 PRD](commerce-launch-prd.md)의 P0 기술 검증 순서를 따른다. 핵심 기능은 main에
  반영됐으며 판매자 운영 기준 협의는 후순위다.
- [RETURN PRD](cancellation-return-prd.md)와 [운영 절차](../runbooks/returns.md),
  [결제 운영 PRD](payment-operations-prd.md), [결제 환경 PRD](payment-environment-prd.md),
  [세션 보안 PRD](session-security-prd.md)의 기존 상태 전이·권한·오류 계약을 사용한다.
- [기존 RETURN 실행 기록](../exec-plans/completed/issue-26-return-implementation.md)의
  신청·승인·검수 브라우저 확인과 당시 BLOCKED는 과거 증거다. 위 Safari 검증으로
  후반 실행·전체 흐름 검증을 완료했으며 과거 결과를 소급 변경하지 않는다.
- PR #31에서 health 부채를 해소하고 엄격 Java 191개 실패·skip 0개를 확인했다.
  이는 기존 기록이며 이번 준비에서의 하네스 실행 결과는 별도 기록한다.

## 검증 환경과 실행 경계

- H2에서는 기존 통합 테스트를 재사용하고 격리 MySQL 8.4에서는 Flyway·JPA 검증과
  주문·배송·재고·RETURN·결제 복구의 DB 제약·잠금·롤백을 확인한다.
  `RETURNS_TEST_DB_URL`, `PAYMENT_OPERATIONS_TEST_DB_URL`은 빈 비밀번호의 격리된 임시
  테스트 DB만 가리킨다. 운영·공유 DB와 기존 사용자 볼륨을 사용하지 않는다.
- 실제 브라우저는 loopback의 별도 테스트 서버와 합성 계정·상품·주소만 사용한다.
  test classpath의 모의 `TossPaymentsClient`와 테스트 전용 client-config/SDK 대역을
  사용해 checkout부터 실제 애플리케이션 API로 연결한다. 제품 API나 상태 저장을
  브라우저 응답 대역으로 통째로 대체하지 않는다.
- 기존 통합 테스트의 `test-only-v1` 정책 fixture를 재사용한다. 정책의 승인자·기간·
  비용·안내는 검증 데이터이며 운영 승인으로 취급하지 않는다. 운영 설정과 `.env`를
  변경하거나 `prod`·실제 PG·S3·가입·RETURN 운영 활성화를 켜지 않는다.
- 후속 실행 시 서버 시작·종료, seed, 시나리오별 초기 상태, 모의 승인·취소·조회 결과와
  호출 횟수, 복구 가능 시각을 고정한 재현 명령을 기록한다. 임시 서버·컨테이너만 정리한다.
  수동 복구는 기존 재시도 시각 계약을 따르고 DB 직접 수정으로 제한을 우회하지 않는다.
- 브라우저 도구의 확인창 처리·연결·접근 실패는 BLOCKED로 남긴다. API/Java 테스트
  통과를 브라우저 확인 완료로 대체하지 않는다. 스크린샷과 상태 대조로 다시 확인한다.

## 화면·API 검증 대상

기존 Controller → Service/Facade → Repository 경계와 `SuccessMessage`,
`NanacocoaException`, `ErrorCode`, 전역 예외 처리를 유지한다.

| 사용자 동작 | 기존 화면·API | 확인할 계약 |
| --- | --- | --- |
| 구매·결제 결과 재확인 | checkout·결제 결과·orders.html, `POST /api/orders`, `GET /api/orders/submission`, `POST /api/payments/confirm`, 본인 주문 목록·상세 | 서버 가격, 주문 제출 멱등성, 저장 상태 기반 표시·소유권 |
| 배송 처리 | shipping.html, `/api/admin/shipping/orders` 목록·상세·변경 | 결제 확정·출고 가능 여부, revision, 취소 접수와 출고 경합 |
| 구매자 신청 | orders.html, `GET/POST /api/orders/{orderId}/return-requests` | 신청 유형·사유, 서버 allowedTypes·holdReason, 같은 요청 재전송 |
| 운영 신청 처리 | returns.html, `/api/admin/return-requests` 목록·상세와 `POST /{id}/actions` | 승인·반려·회수·검수·환불·재입고, 최신 권한·revision·검수 자격 |
| 미확정 복구 | payment-operations.html, `/api/admin/payment-operations` 조회와 `POST /{type}/{id}/reconcile` | 기존 거래로 수렴, 재시도 시각, 조회의 PG 무호출, 새 환불 생성 없음 |

조회·명시적 재제출·재로그인 뒤에도 동일 계약을 적용한다. 미인증 401, 관리자 권한 없음
403, 타인·미존재 주문 404, 상태·내용·revision 충돌 409, 비활성 환경 503과
미확정 환불 202를 기존 계약대로 대조한다. CSRF 누락·만료에는 기존 복구 안내를 제공하고
상태 변경을 자동 재전송하지 않는다. 신규 직접 결제 취소와 검수 조건 우회 재입고도 차단한다.

## 작업 항목과 인수 기준

| 작업 항목 | 구현 범위 | 인수 기준 |
| --- | --- | --- |
| 격리 검증 환경 | 합성 정책·가상 회원·상품·모의 PG, 테스트 전용 실행 방법 정의 | 실제 Toss Payments·S3 호출 없이 재현 가능하고 운영 설정을 변경하지 않음 |
| 구매·결제 | 정상 승인, 거절, 타임아웃·복구, 중복 제출·콜백 | 화면과 DB 결과가 일치하고 주문·예약·승인이 중복 생성되지 않음 |
| 취소·반품 | 출고 전 취소, 출고 후 반품, 반려·재신청, 회수·검수 | 허용 행동·보류 사유가 서버 상태와 일치하고 직접 API 우회가 차단됨 |
| 환불·재입고 후반 | 환불 확인창 이후 완료·실패·복구, 검수 적합·불합격, 반복 실행 | PG 확정 전 완료 표시가 없고 환불·재입고가 각각 최대 한 번 반영됨 |
| 권한·회귀·증거 | 구매자·관리자 화면, 타인 접근, CSRF·세션 만료, 주문·배송·재고 회귀 | 로그인 복귀는 조회만 수행하고 화면·API·DB 증거에 민감 정보가 노출되지 않음 |

## 후속 검증 시나리오

| 시나리오 | 화면·API·DB 기대 결과 |
| --- | --- |
| 정상 구매·주문 상세 | 서버 금액과 결제 완료 일치, 예약이 판매로 한 번 확정 |
| 승인 거절·만료 | 실패 안내, 예약 한 번 해제, 재시도 여부는 최신 서버 상태로 판단 |
| 승인 타임아웃·복구 | 확인 중과 예약 유지, 복구 후 저장 결과로 수렴하고 새 주문·승인 생성 없음 |
| 주문·콜백 반복, 응답 유실 | 같은 시도에 주문·예약 중복 없음, 먼저 조회하고 명시적 재시도만 같은 키 사용 |
| 미결제 취소·출고 전 취소 | 기존 취소 유지, 승인·검수·전액 환불 후 재입고 자격 대조 |
| 출고 후 반품·반려·재신청 | 회수·검수 순서 보장, 최신 반려 ID별 새 신청 키, 응답 유실 재시도는 같은 키 유지 |
| 환불 확인창 취소·실행 | 취소 시 요청·상태 변경 없음, 실행 시 저장된 결과를 고객·관리 화면에서 재조회 |
| 환불 타임아웃·확정 실패·복구 | 확인 전 완료·재입고 없음, 기존 거래·요청 키 유지, 운영자의 명시적 복구로 수렴 |
| 검수 적합·불합격 | 환불 자격과 재판매 가능 여부 분리, 적합·환불 확정 상품만 재입고 |
| 중복 작업·출고 경합·직접 API | 요청 키·revision 충돌 처리, 출고와 출고 전 취소 모순 없음, 환불·재입고 각각 최대 한 번 |
| 새로고침·재로그인·권한 회수 | 저장 상태 재조회, 변경 자동 재제출 없음, 타인 정보 비노출·현재 관리자 권한 재검증 |
| 정책·PG 비활성, 기존 주문·변경된 상품/주소 | 기존 오류·조회 경계 유지, 주문 스냅샷 보존, LEGACY·출고 미확인 자동 처리 보류 |

## 완료 기록

[완료 실행 기록](../exec-plans/completed/issue-32-commerce-e2e.md)에 이전 Chrome 장애와
이번 Safari 재검증 증거를 함께 보관한다. 최종 전체 check는 기존 구조·보안 예외만
BASELINED이며, 192개 엄격 테스트·평가·문서·마이그레이션·테스트 서버 runtime smoke는 PASS다.
비공개 .env의 시작 준비 점검은 의도적으로 SKIPPED이며 기능 테스트 누락으로 취급하지 않는다.
운영 정책·실거래·운영 Compose 스택 배포 인수·main 반영은 완료 범위 밖이다.

## 현재 자동화 검증 결과

전체 엄격 Java 192개, 격리 MySQL 8.4.10 집중 67개(반품 19·결제 운영 13·재고 33·이행 2),
HTTP 종단 6개, JS 4개 파일과 RETURN 집중 JS 8개, Python 정책 3개가 실패·오류·skip 없이 PASS다.
실패·fingerprint 기준선은 비어 있는 상태를 유지하며 검토된 새 통과 ID 1개만 발견 목록에 추가했다.

## 시나리오별 최종 결과

`I`는 InventoryIntegrationTest, `R`은 ReturnRequestIntegrationTest, `O`는
PaymentOperationsIntegrationTest이며 테스트는 기존 실패·예외를 약화하지 않고 실행한다.
원본 브라우저·DB 증거는 실행 기록에 연결한다.

| PRD 시나리오 | 결과 | 검증 근거 |
| --- | --- | --- |
| 정상 구매·주문 상세 | PASS | Safari 실제 구매부터 반품·재입고·구매자 재로그인, 주문·DB·PG 1회 대조 |
| 승인 거절·만료 | PASS | HTTP 상품 3, I.definitiveFailureReleasesOnceAndRetryMustReacquireStock, PaymentRecoveryServiceTest.failsExpiredApprovalOnlyAfterAuthoritativeNotFound |
| 승인 타임아웃·복구 | PASS | HTTP 상품 2, O.recoversApprovalViaHttpAndReplaysCompletedWithoutPg, I.uncertainApprovalAndLateRecoveryKeepReservedStockAfterExpiry |
| 주문·콜백 반복, 응답 유실 | PASS | HTTP 재전송, I.submissionHttpContractScopesKeysAndReplaysStoredOrder, returns.test.cjs의 키 유지·중복 제출 검증 |
| 미결제 취소·출고 전 취소 | PASS | I.unpaidExpiryAndCancellationReleaseExactlyOnce, R.fullCancellationRefundAndRestockReplayExactlyOnce, Safari 신청 1의 실제 환불·재입고 |
| 출고 후 반품·반려·재신청 | PASS | Safari 배송 완료·반품·회수, R.cancellationHoldsShippingAndRejectionReleasesItWithHistory, 새 rejectionAllowsFreshSameReasonApplicationButReplaysDoNotCreateAnotherRequest와 JS 재신청 회귀 |
| 환불 확인창 취소·실행 | PASS | Safari 환불/재입고 각각 취소 후 무변경·수락 후 완료, DB·호출 횟수 대조 |
| 환불 타임아웃·확정 실패·복구 | PASS | Safari 신청 3의 확인 중·개별 복구, 신청 4의 확정 실패·동일 사유 명시적 재시도, HTTP 상품 4·5 |
| 검수 적합·불합격 | PASS | Safari 신청 2 적합·재입고, 신청 5 불합격·재입고 버튼 없음, R.returnCollectionInspectionCanRefundWithoutRestockingDamagedItem |
| 중복 작업·출고 경합·직접 API | PASS | R.concurrentRefundAndRestockOnlyExecuteOnce, R.shipmentAndCancellationRaceCannotBothCommit, R.guardsOwnershipAdminCsrfPrivacyAndDirectCancellation |
| 새로고침·재로그인·권한 회수 | PASS | Safari 구매자 재로그인·본인 결과 조회, SessionSecurityHttpTest, O.protectsEveryBoundaryAndRechecksCurrentAdmin, JS 조회/복귀 검증 |
| 정책·PG 비활성, 기존 주문·변경된 상품/주소 | PASS | R.policyDisabledBlocksNewRequestsAndActionsButAllowsReadsAndOwnedReplay, R.disabledProviderDoesNotRecordRefundIntent, I.orderHistoryProtectsDetailsAndReadsStoredPurchaseInformation, I.oldOrdersRequireVerifiedShippingEvidence |

실행한 테스트 서버의 Actuator HTTP 200/UP은 별도 runtime smoke로 PASS다.
운영 Compose 스택의 배포·설정·실데이터 인수는 이 결과로 완료 처리하지 않는다.

## 검증 방법과 증거 기준

후속 실행에서 기존 집중 테스트를 먼저 재사용하고 실제 공백만 보강한다.
새 테스트가 생기면 발견 목록을 검토해 등록하며 기존 실패·보안·아키텍처 기준선을 약화하지 않는다.

```sh
./gradlew test --tests '*ReturnRequestIntegrationTest' --tests '*PaymentOperationsIntegrationTest' --tests '*InventoryIntegrationTest' --tests '*SessionSecurityHttpTest' --tests '*PolicyIntegrationTest'
./gradlew test --tests '*FlywayMigrationTest' --tests '*FilePolicyConsentMigrationTest'
node --test src/test/js/*.test.cjs
./scripts/harness test
./scripts/harness check
```

브라우저에서는 구매자·관리자 각각의 신청·검수·환불·복구·재입고 후 화면을 남긴다.
시나리오별로 기준 커밋·로컬 변경, 실행 시각, 가상 주문/신청 식별자, 기대·실제 결과,
PG 호출 횟수와 비식별 DB 상태·재고 변화량을 연결한다. 결제 키·세션·요청 키·PG 원문·
고객 주소/연락처·내부 메모는 증거에 복사하지 않는다.

결과는 PASS·FAIL·BLOCKED·SKIPPED·BASELINED로 구분한다. check가 BASELINED여도
개별 브라우저/DB 시나리오의 미확인을 PASS로 표시하지 않는다. 실행 스택이 없어 smoke가
SKIPPED이면 환경 확인 미완료로 남긴다. 발견 결함은 재현·영향·연결된 테스트로 기록한다.

## 완료와 운영 승인 경계

준비 완료는 PRD·GitHub 이슈·추적 브랜치·하네스 결과 보고까지다. 문서는 로컬 미커밋이며
구현 push·PR 생성·병합·배포는 별도 요청이다.
실제 종단 검증을 수행한 뒤에만 출시 PRD·RETURN 실행 기록의 해당 검증 상태를 갱신한다.
기존 기록의 당시 결과는 보존하고 새 증거를 연결한다.
판매자 정책·문의 대응·실재고·운영 설정·배포 대상 검증·출시 승인은 계속 미완료로 관리한다.

2026-11-22 사용자의 별도 요청에 따라 이슈 #32 검증 작업을 feature/32의 커밋으로 관리한다. 원격 main 반영·운영 승인·활성화는 별도다. 위 로컬 미커밋·push 미수행 문구는 검증 완료 당시의 기록이다.
