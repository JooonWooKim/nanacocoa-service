# 기술부채 추적기

상태: 활성
검토 주기: `./scripts/harness entropy`로 매주 검토

| ID | 상태 | 영역 | 증거 | 다음 행동 | 제거 조건 |
| --- | --- | --- | --- | --- | --- |
| NC-TEST-001 | 활성 | 인증·상태 확인 통합 | `NanacocoaServerApplicationTests`에서 9개가 실패하며 정확한 목록은 `harness/baselines/test-failures.txt`로 래칫됩니다. | 전용 제품 계획에서 API 테스트, frontend 기대값, validation, health route, demo 사용자 fixture를 조정합니다. | 엄격한 `./gradlew test`가 통과하고 기준선 파일이 비어 있습니다. |
| NC-ARCH-001 | 활성 | 의존 방향 | `common.userdetails`가 `member` Entity·Repository class를 import합니다. | 세션 동작을 바꾸지 않고 common 소유 identity port를 도입하거나 어댑터를 member·security로 이동합니다. | `common`에 비즈니스 도메인 import가 없고 아키텍처 예외 파일이 비어 있습니다. |
| NC-SEC-001 | 활성 | CSRF | `SecurityConfig`가 세션 인증 브라우저 애플리케이션의 CSRF를 비활성화합니다. | 브라우저·API CSRF 계약을 정의하고 보호를 활성화하며 성공·실패 요청 테스트를 추가합니다. | 상태 변경 세션 요청에 CSRF가 활성화되고 보안 기준선 항목이 제거됩니다. |
| NC-SEC-002 | 활성 | 로그아웃 의미 | `AuthController`가 세션을 변경하는 로그아웃에 GET과 POST를 허용합니다. | client를 확인한 뒤 GET을 제거하고 method·CSRF 회귀 테스트를 추가합니다. | 검토된 상태 변경 method만 허용되고 기준선 항목이 제거됩니다. |
| NC-SEC-003 | 활성 | 세션 fixation | `SessionLoginService`가 명시적 세션 ID 회전 없이 인증을 설정합니다. | Spring Security 인증·세션 전략 또는 검토된 회전 방식을 선택하고 세션 ID 전이를 테스트합니다. | 로그인 성공 시 세션 ID가 회전하고 기준선 항목이 제거됩니다. |
| NC-OPS-001 | 활성 | 제공자 안전 | `application.yml`은 실제 Toss URL과 활성화된 reconciliation을 기본값으로 사용합니다. | 명시적 운영 프로필·활성화 flag와 실제 제공자가 아닌 로컬·테스트 대상을 요구합니다. | 기본 시작으로 Toss를 호출할 수 없고 운영은 의도적인 제공자 설정을 요구합니다. |
| NC-OPS-002 | 활성 | Compose 제공자 안전 | `compose.yaml`도 실제 Toss 기본값과 활성화된 reconciliation을 결합합니다. 로컬 `.env.example`은 두 값을 안전하게 덮어쓰지만 누락·부분 환경은 여전히 위험합니다. | 배포 설정 책임을 정한 뒤 Compose 기본값에서 실제 제공자 동작을 제거합니다. | 명시적 운영 opt-in 없이 Compose가 Toss에 연결할 수 없습니다. |
| NC-OPS-003 | 제안 | 데이터베이스 노출 | Compose가 로컬 도구를 위해 MySQL을 host loopback에 공개하여 host process 접근 범위와 3306 충돌 가능성을 넓힙니다. | host SQL 접근이 기본으로 필요한지 명시적 debug profile에 속하는지 결정합니다. | 기본 Compose에 MySQL host port가 없거나 검토된 운영 필요성과 대체 port 정책이 문서화·테스트됩니다. |
| NC-DATA-001 | 활성 | 마이그레이션 도입 | MySQL 프로필이 `baseline-on-migrate: true`와 기준 버전 1을 사용합니다. | 기존 배포 이력을 감사하고 flag 변경 전에 기준화가 계속 필요한지 결정합니다. | 새·관리 DB가 버전 없는 스키마를 거부하고 배포 DB에 문서화된 전환 절차가 있습니다. |
| NC-DATA-002 | 제안 | 데이터베이스 충실도 | 마이그레이션 테스트는 H2 MySQL 모드를 사용하며 임시 MySQL 8.4 마이그레이션·JPA 검증을 자동 실행하지 않았습니다. | Docker CI 생명주기와 정리 책임이 승인되면 제공자 호출 없는 MySQL container 테스트를 추가합니다. | 운영 마이그레이션 이력과 JPA 검증이 CI의 MySQL 8.4에서 통과합니다. |
| NC-CI-001 | 차단 | hosted CI | 최초 커밋과 remote가 없어 hosting 제공자와 기본 branch 계약을 알 수 없습니다. | remote가 생긴 뒤 `./scripts/harness check`를 실행하고 `build/harness`를 업로드하는 제공자 workflow를 추가합니다. | Hosted CI가 제공자 전용 테스트 로직 없이 동일한 로컬 계약을 실행합니다. |
| NC-OBS-001 | 제안 | 관측성 | Actuator health는 있지만 구조화된 correlation ID, metrics·tracing 스택, 자동 임시 스택이 없습니다. | 인증·상태 확인 계약 수정 후 디버깅 필요를 측정하고 비식별화 테스트가 있는 저위험 상관관계·metrics만 추가합니다. | 핵심 사용자 흐름이 제한된 진단에 충분한 로그·metrics·trace를 제공합니다. |

새 항목에는 ID, 상태, 증거, 다음 행동, 객관적 제거 조건이 필요합니다.
검사를 삭제하거나 약화해 기준선을 닫지 않습니다.
