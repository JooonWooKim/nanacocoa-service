# 에이전트 우선 하네스 엔지니어링 시스템

상태: 완료

## 목표

새 에이전트가 안전하지 않은 외부 효과 없이 Nanacocoa를 파악하고, 일관된
검사를 실행하고, 조치 가능한 실패를 확인하고, 핵심 위험을 평가하고,
증거를 수집하고, 품질을 래칫할 수 있는 저장소 소유 실행형 하네스를
구축합니다.

## 하지 않는 일

- 배포, push, pull request, merge, remote CI 구성을 수행하지 않습니다.
- 실제 Toss Payments·S3 호출이나 운영 비밀값 접근을 하지 않습니다.
- 광범위한 제품 refactor나 파괴적인 Docker·데이터 작업을 하지 않습니다.
- 기준선에 있는 제품 실패가 올바르다고 주장하지 않습니다.

## 현재 증거

- 저장소에는 커밋과 설정된 remote가 없으며 2026-07-25 기준 모든 기존 파일이
  추적되지 않았습니다.
- 기술 스택: Java 17, Spring Boot 3.5.14, Gradle, MySQL, Redis, Flyway,
  H2 테스트, Docker Compose, Toss client, mock·S3 이미지 저장소
- 도메인: member, products, order, payment, common 인프라
- `./gradlew test` 기준선: 테스트 63개, 실패 9개, 모두
  `NanacocoaServerApplicationTests`에서 발생
- 실패 그룹: 누락된 `/api/health`, 인증 응답·세션 계약 불일치, validation
  누락, demo 로그인 가정
- 기존 주문·결제·상품·마이그레이션 집중 테스트는 기준선에서 통과합니다.
- 작업 전에는 root `AGENTS.md`, 구조화된 `docs/`, 하네스 진입점, CI 설정이
  없었습니다.
- 완성된 하네스는 전체 JUnit XML을 소스·실행 비트 fingerprint에 결합하고,
  skip 0개와 63개 테스트 ID 전체를 비교하며, 알려진 각 실패의 종류·예외
  타입·메시지를 고정합니다.
- 보안 리뷰에서 MySQL loopback 노출을 포함한 정확한 제품·설정 위험 7개를
  발견했으며 숨기지 않고 모두 부채로 기록했습니다.

## 결정

1. runtime 의존성을 추가하지 않도록 작은 POSIX shell dispatcher와 책임별
   스크립트를 사용합니다.
2. 엄격한 `./gradlew test` 동작을 유지하고 `check`는 명시적 실패 기준선을
   래칫으로 사용합니다. 정확한 알려진 실패는 BASELINED이며 추가되거나
   예상하지 않게 해소되면 검토 전까지 실패합니다.
3. 현재 안전한 구조 규칙을 즉시 강제합니다. 관련 없는 refactor를 강요하는
   대신 기존 `common -> member` 의존성을 기록하고 래칫합니다.
4. 소스 입력으로부터 저장소·스키마 참조 문서를 결정적으로 생성합니다.
5. git remote나 최초 커밋이 없어 GitHub, GitLab 등 host를 알 수 없으므로
   제공자 중립 CI 계약을 정의합니다.
6. smoke 검사는 Compose와 이미 실행 중인 로컬 app을 확인합니다. 하네스는
   비밀값을 만들거나 제공자 연결 Service를 시작하거나 볼륨을 삭제하거나
   기존 스택에 대한 권한을 가정하지 않습니다.
7. 모든 상태와 복사한 산출물을 저장소 fingerprint와 하나의 조정된 check
   실행에 함께 결합합니다. 섞였거나 오래된 증거는 제외합니다.
8. 비식별화된 Compose Service·State·Health 필드만 저장합니다. command 인자에
   자격 증명이 있을 수 있으므로 원본 container command JSON은 저장하지
   않습니다.

## 작업 분해

- [x] 저장소와 기준선 감사
- [x] 에이전트 지도, 아키텍처, 지식 베이스 골격
- [x] 하네스 dispatcher와 진단·check·평가 명령
- [x] 아키텍처, 문서, 마이그레이션, 보안 래칫
- [x] 생성 참조 문서와 품질·runbook 문서화
- [x] 독립 리뷰와 전체 검증

## 진행 상황

- 2026-07-25: 요청된 전체 하네스 명세와 적용할 Nanacocoa 백엔드 규칙을
  읽었습니다.
- 2026-07-25: 저장소 구조, dependency import, 마이그레이션, 보안 설정,
  테스트 목록, git 상태, 기준선 테스트 결과를 수집했습니다.
- 2026-07-25: 버전 관리 지식 시스템을 시작하고 이상적이지 않은 기준선
  조건을 명시적으로 기록했습니다.
- 2026-07-25: 명령 dispatcher, 진단, 아키텍처, 문서, 보안, 마이그레이션,
  entropy, smoke, 테스트·평가, 증거, cleanup 계약을 구현했습니다.
- 2026-07-25: 결정적 패키지·스키마 참조, 정확한 테스트 목록·실패
  fingerprint, 숫자 마이그레이션 정렬, parser fixture, 소스·실행 결합 증거를
  추가했습니다.
- 2026-07-25: 독립 정확성, 아키텍처·문서, 보안·신뢰성 리뷰를 완료했습니다.
  모든 높은 심각도 하네스 지적을 닫고 나머지 기계적 무결성 지적을
  반영했습니다.
- 2026-07-25: 제공자 중립 로컬 CI 계약을 검증했습니다. 제품 결함, hosted
  CI, runtime Compose health, 실제 MySQL 충실도는 완료로 취급하지 않고
  명시적으로 분류해 두었습니다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short` | PASS | 커밋 없는 `master`, 현재 모든 프로젝트 파일이 미추적 상태입니다. |
| `./gradlew test` | FAIL | 테스트 63개 중 9개 실패, 보고서는 `build/reports/tests/test/`에 있습니다. |
| `./scripts/harness test-unit` | PASS | 주문·결제·상품 집중 테스트를 성공적으로 완료했습니다. |
| `./scripts/harness check` | BASELINED | 새 회귀가 없고 아키텍처·보안·테스트·평가 기준선이 변경되지 않았습니다. |
| `./scripts/harness architecture` | BASELINED | 기계적 계층 규칙이 통과했고 `common -> member` 예외 3개가 NC-ARCH-001로 남아 있습니다. |
| `./scripts/harness security` | BASELINED | 저장소 검사가 통과했고 정확한 보안·데이터·제공자 기본값 위험 7개가 검토된 부채로 남아 있습니다. |
| `./scripts/harness migrations` | PASS | 순차 숫자 버전과 불변 SHA-256 등록부가 일치합니다. |
| `./scripts/harness docs` | PASS | 필수 문서 graph, 링크, 계획 의미, 명령 참조, 생성 파일이 최신입니다. |
| `./scripts/harness eval` | BASELINED | 핵심 평가 11개가 통과하고 인증·상태 확인 평가 4개가 알려진 실패에 연결됩니다. |
| `./scripts/harness smoke` | SKIPPED | 비식별화된 Compose 설정은 통과했지만 의도적으로 실행한 전체 스택이 없습니다. |
| runtime MySQL 8.4 검증 | BLOCKED | H2 MySQL 모드는 통과하지만 임시 MySQL 생명주기·CI 권한이 구성되지 않았습니다(NC-DATA-002). |
| 호스팅 CI 작업 흐름 | BLOCKED | 최초 커밋, remote, 제공자, 기본 branch가 없습니다(NC-CI-001). |
| 독립 리뷰어 검사 | PASS | 읽기 전용 리뷰어 3명이 수정 후 남은 높은 심각도 하네스 결함이 없음을 확인했습니다. |
| `git remote -v` | PASS | remote가 구성되어 있지 않습니다. |
| 저장소·import 감사 | PASS | Controller·Service·Repository와 외부 경계를 `ARCHITECTURE.md`에 기록했습니다. |

## 위험과 롤백

- 최초 커밋이 없어 git은 기존 내용과 하네스 변경을 구분할 수 없습니다.
  모든 변경은 좁은 범위를 유지하고 기존 파일을 삭제하지 않습니다.
- 오래된 실패 기준선은 결함을 정상화할 수 있습니다. 래칫은 정확한 목록,
  skip, 실패 종류·타입·메시지, 소스 fingerprint 일치를 요구하고 BASELINED를
  명확히 드러내며 어느 부분이든 변경되면 실패합니다.
- 원본 Docker·Compose 검사는 command line 자격 증명을 노출할 수 있습니다.
  smoke 증거는 비식별화된 Service·State·Health 필드만 포함합니다.
- shell 이식성은 macOS·Linux에서 사용할 수 있는 POSIX 도구로 제한되며
  doctor와 하네스 자체 검사가 누락된 선행 조건을 명확히 표시합니다.
- 롤백은 새로 추가한 `AGENTS.md`, `ARCHITECTURE.md`, `docs/`, `harness/`,
  하네스 스크립트를 제거하고 좁은 ignore·build 변경을 되돌리는 것으로만
  구성합니다. 커밋 없는 트리에서 광범위한 git cleanup을 사용하지 않습니다.

## 미해결 항목

- 하네스 구현 미해결 항목은 없습니다.
- 인증·상태 확인 실패 9개를 변경하기 전에 NC-TEST-001의 제품 계약 결정이
  필요합니다.
- NC-SEC-001/002/003, NC-OPS-001/002/003, NC-DATA-001에는 제품 또는 배포
  결정이 필요합니다. 하네스는 조용한 증가를 막지만 정책을 선택하지 않습니다.
- NC-DATA-002에는 승인된 임시 MySQL 생명주기가 필요합니다.
- NC-CI-001에는 최초 커밋, remote, 제공자, branch 정책이 필요합니다.
- NC-OBS-001은 운영 신호 요구사항이 알려질 때까지 제안 상태로 남습니다.

## 완료 조건

- [x] 문서화된 명령 surface가 존재하고 지원하지 않는 입력을 거부합니다.
- [x] 지식, 아키텍처, 마이그레이션, 비밀값, 하네스 검사가 통과하거나 정확한
  검토 완료 기준선을 드러냅니다.
- [x] 엄격 테스트와 기준선 테스트 결과가 모두 보이고 올바르게 분류됩니다.
- [x] 평가 증거가 핵심 위험을 현재 전체 테스트 결과에 연결합니다.
- [x] Compose 설정과 smoke 상태가 안전하지 않은 외부 효과나 자격 증명이
  포함된 원본 검사 출력 없이 분류됩니다.
- [x] 독립 정확성, 아키텍처·문서, 보안·신뢰성 리뷰에 미해결 높은 심각도
  하네스 지적이 없습니다.
