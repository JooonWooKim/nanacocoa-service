# 테스트 운영 절차

상태: 2026-08-22 검증됨

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
추적합니다. 결제 클라이언트 설정·인증 계약 테스트 5개를 추가한 현재 발견 집합은
68개이며 실패 9개는 그대로입니다.

`test-baseline`은 전체 테스트를 실행하고 모든 Gradle XML을 파싱합니다.
전체 68개 테스트 목록을 비교하고 skip 0개를 요구하며 각 알려진 실패의
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
