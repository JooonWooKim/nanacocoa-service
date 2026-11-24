# 기술부채 추적기

상태: 활성
검토 주기: `./scripts/harness entropy`로 매주 검토

| ID | 상태 | 영역 | 증거 | 다음 행동 | 제거 조건 |
| --- | --- | --- | --- | --- | --- |
| NC-TEST-001 | 해결 · feature/30, main 미반영 | 상태 확인 통합 | 기존 health 테스트를 공식 /actuator/health 계약으로 정합화하고 실제 HTTP 정상·장애·정보 비공개·인증 경계 4개를 추가했다. 전체 엄격 191개 실패/skip 0개 확인 후 해결된 실패/fingerprint만 제거했다. | 구현 검토와 main 반영, 회귀 유지 | 빈 실패 기준선과 엄격 테스트 통과 유지. [구현 기록](completed/health-check-implementation.md) |
| NC-ARCH-001 | 활성 | 의존 방향 | `common.userdetails`가 `member` Entity·Repository class를 import합니다. | 세션 동작을 바꾸지 않고 common 소유 identity port를 도입하거나 어댑터를 member·security로 이동합니다. | `common`에 비즈니스 도메인 import가 없고 아키텍처 예외 파일이 비어 있습니다. |
| NC-OPS-001 | 해결 | 제공자 안전 | 애플리케이션 기본값과 운영 프로필·키 유형·호스트 검증을 [결제 환경 PRD](../product-specs/payment-environment-prd.md)에 기록했습니다. | 운영 활성화 시 결제 환경 운영 절차를 따릅니다. | 기본 실행에서는 실제 PG 어댑터와 자동 복구를 구성하지 않습니다. |
| NC-OPS-002 | 해결 | Compose 제공자 안전 | Compose는 기본 `mysql` 프로필에서 Toss 연동과 복구를 끕니다. | 운영에서만 프로필과 활성화 플래그를 지정합니다. | 명시적 운영 활성화 없이 Compose에서 Toss에 연결할 수 없습니다. |
| NC-OPS-003 | 제안 | 데이터베이스 노출 | Compose가 로컬 도구를 위해 MySQL을 host loopback에 공개하여 host process 접근 범위와 3306 충돌 가능성을 넓힙니다. | host SQL 접근이 기본으로 필요한지 명시적 debug profile에 속하는지 결정합니다. | 기본 Compose에 MySQL host port가 없거나 검토된 운영 필요성과 대체 port 정책이 문서화·테스트됩니다. |
| NC-DATA-001 | 활성 | 마이그레이션 도입 | MySQL 프로필이 `baseline-on-migrate: true`와 기준 버전 1을 사용합니다. | 기존 배포 이력을 감사하고 flag 변경 전에 기준화가 계속 필요한지 결정합니다. | 새·관리 DB가 버전 없는 스키마를 거부하고 배포 DB에 문서화된 전환 절차가 있습니다. |
| NC-DATA-002 | 제안 | 데이터베이스 충실도 | 마이그레이션 테스트는 H2 MySQL 모드를 사용하며 임시 MySQL 8.4 마이그레이션·JPA 검증을 자동 실행하지 않았습니다. | Docker CI 생명주기와 정리 책임이 승인되면 제공자 호출 없는 MySQL container 테스트를 추가합니다. | 운영 마이그레이션 이력과 JPA 검증이 CI의 MySQL 8.4에서 통과합니다. |
| NC-CI-001 | 원격 검증 대기 | hosted CI | 공통 설정 초기화와 GitHub 품질 workflow가 기존 하네스를 호출한다. | PR 또는 main의 실제 품질 검사 결과와 실패 요약을 확인한다. | 운영 비밀값 없이 원격 하네스가 실행되고 기존 기준선·실패 분류를 유지한다. |
| NC-OBS-001 | 제안 | 관측성 | Actuator health는 있지만 구조화된 correlation ID, metrics·tracing 스택, 자동 임시 스택이 없습니다. | 인증·상태 확인 계약 수정 후 디버깅 필요를 측정하고 비식별화 테스트가 있는 저위험 상관관계·metrics만 추가합니다. | 핵심 사용자 흐름이 제한된 진단에 충분한 로그·metrics·trace를 제공합니다. |

새 항목에는 ID, 상태, 증거, 다음 행동, 객관적 제거 조건이 필요합니다.
검사를 삭제하거나 약화해 기준선을 닫지 않습니다.
