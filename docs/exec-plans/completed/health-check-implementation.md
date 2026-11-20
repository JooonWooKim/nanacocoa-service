# Actuator 상태 확인 계약 정비 구현

상태: 완료 · 로컬 구현/검증 완료, main 미반영

## 목표

이슈 #30의 공식 상태 API를 /actuator/health로 통일하고 정상·장애·정보 비공개 계약을 검증해 NC-TEST-001을 해소한다.

## 하지 않는 일

별도 상태 API/서비스/DB 구조, 운영 활성화, 커밋·push·PR 생성과 기존 사용자 변경 폐기는 하지 않는다.

## 현재 증거

feature/30은 main de5c595 기준이다. 기존 187개 중 healthApiReturnsOk만 미구현 /api/health의 404로 실패한다.
README/smoke는 Actuator의 200/UP을 사용한다. Spring Boot 기본 show-details는 never다.

## 결정

테스트 이름을 유지하고 기존 Actuator 계약으로 수정한다. 실제 HTTP와 테스트 전용 지표로 정상·장애를 재현한다.
집중·전체 엄격 테스트 통과 전에는 실패 기준선을 제거하지 않는다. 판매자 협의 후순위와 기존 문서 변경을 보존한다.

## 작업 분해

- [x] 조사
- [x] 구현
- [x] 검증
- [x] 리뷰와 문서화

## 진행 상황

- 2026-11-20: 기존 수정/미추적 파일 11개를 /private/tmp/health-implementation-gekmqa2x에 백업하고 SHA-256을 기록했다.

- 2026-11-20: 별도 상태 API를 추가하지 않고 미사용 익명 허용 1개만 제거했다. 기존 health 테스트 ID를 유지하고 실제 HTTP 테스트 4개를 추가했다.
- 2026-11-20: 정상·DOWN·OUT_OF_SERVICE 공개 응답은 상태만 포함했다. 테스트 지표에 합성 내부 상세·예외를 넣었으며 show-details/show-components/HTTP 매핑은 덮어쓰지 않았다.
- 2026-11-20: 집중 6개와 전체 엄격 191개 실패·skip 0개를 먼저 확인했다. XML 이름의 JUnit 괄호를 하네스와 동일하게 정규화해 기존 187개+신규 4개만 존재함을 대조한 뒤 해결된 health 실패/fingerprint만 제거했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | feature/30 확인, 기존 변경 백업 |
| `./gradlew test --tests '*HealthContractHttpTest' --tests '*NanacocoaServerApplicationTests.healthApiReturnsOk' --tests '*SessionSecurityHttpTest'` | PASS | 실제 HTTP 4개·기존 health 1개·세션/CSRF 1개, 총 6개 |
| `./gradlew test` (기준선 제거 전) | PASS | 전체 191개·실패/error/skip 0개, 기존 187개 발견 유지 |
| `JUnit XML/기준선 대조` | PASS | health-pre-removal-evidence.json에 제거 전 실패 기준선과 전체 통과 증거 저장 |
| `./gradlew test` (기준선 제거 후) | PASS | 191개 전체 결과 유지, 실패·error·skip 0개 |
| `./scripts/harness test-baseline` | PASS | 기존 187개+실제 HTTP 4개만 발견, 실패 기준선 0개 |
| `HARNESS_REUSE_TEST_RESULTS=1 ./scripts/harness eval` | PASS | 63개 평가 PASS, BASELINED/BLOCKED 0개, OPS-001~005 PASS |
| `./scripts/harness check` | BASELINED | test-baseline/eval PASS. 기존 구조 예외 3개·보안 위험 2개만 유지 |
| `check: docs/migrations/harness-self-check/entropy/diff/workspace-stability` | PASS | 검사 중 저장소 입력 변화 없음 |
| `check: smoke` | SKIPPED | 실행 중인 Compose 스택과 앱 포트 없음. 임의 localhost/운영 서비스에 요청하지 않음 |
| `백업 원문/SHA-256 대조` | PASS | 기존 파일 8개 바이트 동일. health PRD·인덱스·출시 PRD만 승인 범위에 맞춰 추가 갱신 |

## 위험과 롤백

고정 UP 응답이나 테스트 skip으로 부채를 감추지 않는다. Actuator 집계는 그대로 유지한다.
공개 정보·접근 경계 회귀를 검사한다. 되돌릴 때는 변경 코드·테스트와 연결된 실패/발견 기준선·평가도 함께 검토해 계약 불일치를 남기지 않는다.

- 2026-11-20: 전체 엄격 통과를 확인한 뒤 실패 기준선을 비웠고 다시 엄격/test-baseline/eval/check를 실행했다. 제품 테스트·평가는 PASS이며 전체 check의 BASELINED는 기존 구조·보안 예외뿐이다.
- 2026-11-20: 코드/정보 노출/접근 경계 회귀를 검토하고 NC-TEST-001·관측성/품질/운영·구현/출시 PRD를 갱신했다. 점수는 올리지 않았다. 과거 기능 실행 기록의 당시 실패 결과는 덮어쓰지 않았다.
- 2026-11-20: 기존 사용자 변경과 준비 기록을 보존했다. 커밋·push·PR 생성·운영 활성화는 하지 않았다.

## 미해결 항목

다른 아키텍처/보안·운영 기술부채와 판매자 기준 협의는 이번 범위에서 해결하지 않는다.

## 완료 조건

집중·전체 엄격 통과 후 기준선 정리, test-baseline/eval/check 실행, 기존 변경 보존과 문서·출시 PRD 갱신을 완료했다. 증거는 build/harness/check-summary.txt, eval/results.tsv, JUnit XML 및 health-pre-removal-evidence.json에 있다. 운영 배포/스택 smoke와 main 반영은 완료하지 않았다.
