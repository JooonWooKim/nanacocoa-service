# 테스트 운영 절차

상태: 2026-11-20 상태 확인 계약 정비 검증

## 명령 단계

| 명령 | 의미 |
| --- | --- |
| `./scripts/harness test-unit` | 주문·결제·상품 집중 테스트 패키지 |
| `./scripts/harness test-integration` | 애플리케이션과 마이그레이션 통합 엄격 테스트 |
| `./scripts/harness test` | 전체 Gradle 엄격 테스트 |
| `./scripts/harness test-baseline` | 정확한 알려진 실패 래칫이 있는 전체 테스트 |
| `./scripts/harness eval` | 현재 Gradle XML을 위험 중심으로 평가 |
| `./scripts/harness check` | 로컬 CI 계약 |

## 상태 용어

- PASS: 검증 대상 요구사항을 통과했습니다.
- FAIL: 검사를 실행했지만 요구사항을 만족하지 못했습니다.
- BLOCKED: 필요한 선행 조건이나 결정이 없습니다.
- SKIPPED: 선택적·환경 의존 검사를 실행하지 못했습니다.
- BASELINED: 검토된 기존 실패·예외가 변경되지 않았습니다. 정확성이 아니라
  부채를 의미합니다.

## 현재 기준선

초기 엄격 실행의 테스트 63개 중 9개 실패는 NC-TEST-001로 추적했습니다. 회원가입·세션
계약을 정비한 뒤 남았던 health 실패도 feature/30에서 공식 /actuator/health 계약으로
정합화했습니다. 기존 테스트 이름을 유지하고 실제 HTTP 정상·DOWN·OUT_OF_SERVICE·
인증/CSRF 경계 4개를 추가해 당시 발견 기준은 191개였습니다. 이슈 #32에서 반려 후 동일 사유 재신청과 응답 유실 재전송의
서버 통합 회귀 1개를 추가해 현재 발견 기준은 192개입니다.
전체 엄격 실패·skip 0개를 확인한 뒤 실패 목록과 fingerprint의 해결된 health 항목만
제거했습니다. 현재 두 실패 기준선 파일은 비어 있으며 구현의 main 반영은 별도입니다.

`test-baseline`은 전체 테스트를 실행하고 모든 Gradle XML을 파싱합니다.
전체 192개 테스트 목록을 비교하고 skip 0개를 요구하며 각 알려진 실패의
failure·error 종류, 예외 타입, assertion 메시지를 고정합니다. 전체 실행은
XML 옆에 저장소 fingerprint를 기록하며 `--from-results`는 정확히 동일한
소스·실행 비트 상태에서만 허용합니다. 다음 상황에서는 실패합니다.

- 새 테스트가 실패함
- 통과 테스트가 삭제·이름 변경·미발견·중복·비활성화됨
- 부분 테스트 결과만 존재함
- 동일한 테스트 ID에서 알려진 실패의 종류나 메시지가 변경됨
- 알려진 실패 테스트의 식별자가 변경됨
- 알려진 실패가 해소됐지만 기준선을 의도적으로 줄이지 않음
- 테스트 증거를 만들기 전에 Gradle이 실패함

실패를 해결한 뒤 검증된 통과 ID만 제거하고 엄격 테스트, 기준선 테스트,
평가, 전체 check를 다시 실행합니다.

보고서:

- HTML: `build/reports/tests/test/index.html`
- XML: `build/test-results/test/`
- 하네스 로그·상태: `build/harness/`

통과 결과만 얻기 위해 의미 있는 동작을 비활성화하거나 예외를 삼키거나
mock으로 제거하지 않습니다.

## 정책·가입 검증

POLICY 집중 테스트는 승인·미승인, 파일 스냅샷 불변성·과거 원문, 선택 동의 거절,
회원/동의 롤백과 검증 버전 이력 보존을 다룹니다. 승인 문서는 테스트 리소스에만 둡니다.
기존 가입 성공 테스트도 명시적으로 승인 fixture와 가입 활성화를 설정합니다.
기본 설정을 시험을 위해 활성화하지 않습니다.

```sh
./gradlew test --tests '*PolicyIntegrationTest'
node src/test/js/policies.test.cjs
```

격리 MySQL 8.4 검증은 `POLICY_TEST_DB_URL`을 사용하며 운영 DB를 지정하지 않습니다.
새 빈 DB에서만 기존 데이터 이행 테스트의 `SUBMISSION_MIGRATION_TEST_DB_URL`을 사용합니다.
[정책 운영 절차](service-policy.md),
[구현 기록](../exec-plans/completed/issue-28-policy-implementation.md).

파일 정책 전환은 H2 및 격리 MySQL에서 V10 기존 이력 보존/신규 FK 없는 저장을
확인한다. `POLICY_FILE_MIGRATION_DB_URL`은 빈 테스트 DB만 지정한다.
`python3 -B -m unittest discover -s src/test/python -v`는 DB 연결 없이 내보내기/해시 대조를 검사한다.
2026-11-20 파일 정책 전환 당시 발견 목록은 기존 게시 잠금 관련 3개 테스트를 파일 스냅샷/이력 검증으로 교체하고
정책 3개·V10 이행 1개를 추가해 187개다. health 실패 기준선은 변경하지 않았다.

## 상태 확인 계약 회귀

기존 `healthApiReturnsOk`는 익명 `/actuator/health`의 HTTP 200/UP과 정보 비공개를
검증한다. `HealthContractHttpTest`는 임의 port의 실제 HTTP로 테스트 전용 지표의
UP/DOWN/OUT_OF_SERVICE를 확인한다. 지표에 합성 내부 상세/예외를 넣어도 공개 응답은
상태만 포함해야 한다. 지표는 각 테스트 전후 UP으로 초기화하고 운영 외부 서비스는 호출하지 않는다.
show-details/show-components/HTTP 상태 매핑을 테스트 설정으로 덮어쓰지 않는다.

```sh
./gradlew test --tests '*HealthContractHttpTest' --tests '*NanacocoaServerApplicationTests.healthApiReturnsOk' --tests '*SessionSecurityHttpTest'
```

실패 해소 시에는 집중·전체 엄격 통과를 먼저 확인하고 기준선을 줄인다. 이후 전체 엄격
테스트·test-baseline·eval·check를 다시 실행한다. 아키텍처/보안의 기존 예외는 별도다.


## 회원 장바구니 검증

feature/34는 CartIntegrationTest 13개를 추가해 발견 기준을 205개로 늘린다.
장바구니 저장·동시 담기/수정·소유권/CSRF·가격/재고·주문 응답 복구와 승인 후 정리,
정리 DB 실패 원자성·재처리, 자동/수동 승인 복구·변경 항목 보존을 검증한다.
CART_TEST_DB_URL로 격리 MySQL 8.4에서도 같은 테스트를 실행한다.
JS cart.test.cjs와 테스트 전용 cart_e2e_http.py는 화면 상태·실제 HTTP를 별도로 확인한다.
기존 192개 테스트·실패 기준선과 구조/보안 예외는 유지한다.
[운영 절차](cart.md), [PRD](../product-specs/cart-prd.md).


## 회원 배송지 검증

feature/36은 ShippingAddressIntegrationTest 13개를 추가해 발견 기준을 218개로 늘린다.
기본값 경합·등록 재전송·독립된 프로필·주문/장바구니 스냅샷·DB 소유권 제약·입력/보안,
V11→V12 회원·주문·장바구니 보존을 검증한다. ADDRESS_TEST_DB_URL은 격리 MySQL만,
ADDRESS_MIGRATION_TEST_DB_URL은 빈 이행 검증 DB만 사용한다. 환경 전환은 --rerun-tasks로 실행한다.
JS shipping-addresses.test.cjs는 지연 응답·사용자 편집·재조회·계정 변경·등록 응답 유실을 다루고,
shipping_address_e2e_http.py는 실제 HTTP 검증 결과에 원문 주소·연락처를 기록하지 않는다.
[운영 절차](shipping-addresses.md), [PRD](../product-specs/shipping-address-prd.md).
