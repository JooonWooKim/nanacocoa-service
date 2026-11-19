# 테스트 운영 절차

상태: 2026-09-14 검증됨

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

초기 엄격 실행은 테스트 63개 중 9개가 실패했고 모두
`NanacocoaServerApplicationTests`에 있습니다. 정확한 ID는
`harness/baselines/test-failures.txt`에 있으며 원인과 해결은 NC-TEST-001로
추적합니다. 결제와 회원 기능 테스트를 추가했으며,
회원가입 관련 실패 4개를 검증된 계약으로 해소했습니다. 세션 보안 작업에서 인증
fixture와 API 계약 검증을 정비해 상태 확인 실패 1개가 남아 있습니다. 실제 HTTP 세션
보안, 모의 PG 결제 복귀·HTTP 결제 환경·관리자 복구·취소·반품 검증을 포함한 전체 발견 기준은 187개입니다.

`test-baseline`은 전체 테스트를 실행하고 모든 Gradle XML을 파싱합니다.
전체 187개 테스트 목록을 비교하고 skip 0개를 요구하며 각 알려진 실패의
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
이번 발견 목록은 기존 게시 잠금 관련 3개 테스트를 파일 스냅샷/이력 검증으로 교체하고
정책 3개·V10 이행 1개를 추가해 187개다. health 실패 기준선은 변경하지 않았다.
