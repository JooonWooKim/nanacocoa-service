# 이슈 #28 POLICY 기능 구현

상태: 완료 · feature/28 구현·검증 완료, main 반영·정책 승인·운영 활성화 대기

## 목표

[PRD](../../product-specs/service-policy-prd.md)에 따라 승인 문서 게시와 가입 동의 이력을 구현한다.
현재 feature/28에서 작업하고 기존 사용자 문서를 보존한다.

## 하지 않는 일

- 정책 원문 승인·운영 활성화, 기존 회원 동의 생성, 관리자 CMS를 구현하지 않는다.
- 커밋·push·PR 생성·병합·별도 worktree 생성 및 실제 Toss Payments·S3 호출을 하지 않는다.

## 현재 증거

- HEAD와 origin/feature/28은 main의 RETURN 병합 커밋 0cee3be다.
- 준비 문서와 기존 사용자 변경 10개를 임시 백업하고 해시를 기록했다.
- 가입은 이메일 수신 선택만 저장하고 정책 버전 검증은 없다. Flyway 최신 버전은 V8이다.
- 기존 전체 170개 테스트의 healthApiReturnsOk 실패 1개는 NC-TEST-001 기준선이다.

## 결정

- 승인 문서는 저장소 JSON 파일과 재배포로 반영하고 게시 원문은 DB에 보존한다.
- 새 V9의 게시 원문·현재 게시 집합·가입 동의 저장과 트랜잭션 잠금으로 버전 교체를 제어한다.
- 기본 카탈로그는 비어 있고 신규 가입은 503으로 보류한다. 기존 회원 로그인·조회는 유지한다.
- 문서 본문은 일반 텍스트로 렌더링하고 승인 fixture는 테스트에만 둔다.

## 작업 분해

- [x] 현황 조사·사용자 변경 백업
- [x] 정책 도메인·V9·게시 및 가입 저장 구현
- [x] 정책·가입 화면과 링크 연결
- [x] 집중·화면·마이그레이션·MySQL 검증
- [x] 문서·평가·출시 PRD 갱신과 전체 check
- [x] 회귀·보안·데이터 보존 검토 및 완료 기록

## 진행 상황

- 2026-11-19: 승인된 계획 구현을 시작하고 기존 변경 파일을 백업했다.
- 2026-11-19: 정책·게시·동의 모델과 V9, 공개 API와 가입 검증·원자적 저장을 구현했다. 클래스패스 카탈로그 전체 검증, 같은 버전 변경 거부·게시 revision 유지, 공유/쓰기 잠금으로 게시·가입 경합을 제어했다.
- 2026-11-19: 기본 비활성 가입 플래그를 추가해 게시 문서와 과거 동의 기록을 보존하면서 신규 가입만 중단할 수 있게 했다. Git 제외 대상인 로컬 설정 3개에는 이 작업의 플래그 연결만 추가하고 다른 설정을 보존했다. Java 코드도 YAML 없이 기본 비활성을 유지한다.
- 2026-11-19: H2 정책 집중 13개 PASS. 초기 H2 집중 실행은 정책 11개·기존 회원 14개·마이그레이션 2개 PASS였다. tmpfs MySQL 8.4.10에서 당시 핵심 정책 12개·기존 데이터 이행 1개와 JPA 검증이 PASS였다. 후속 추가인 가입 중단 플래그 테스트는 H2에서 확인했다.
- 2026-11-19: 화면 회귀 7개와 기존 JS 테스트 파일들이 PASS다. 버전 재조회 시 필수 동의와 선택 이메일 수신을 초기화하며 자동 가입 재전송을 막는다.
- 2026-11-19: 별도 H2 테스트 서버·합성 승인 문서로 브라우저에서 필수 동의, 선택 수신 거절 가입 후 로그인 이동, 원문 텍스트 표시를 확인했다. build/harness/policy-browser.jpg에 텍스트 표시 증거를 저장했다. 테스트 서버·tmpfs MySQL 컨테이너를 정상 종료했고 기존 Compose는 변경하지 않았다.
- 2026-11-19: 전체 엄격 테스트 183개·skip 0개에서 기존 health 실패 1개만 확인했다. 기존 170개 ID·실패 fingerprint를 보존한 채 정책 테스트 13개만 등록하고 POLICY 평가 6개를 연결했다. 전체 check는 최종 문서 상태에서 실행한다.
- 2026-11-19: 전체 check는 BASELINED, POLICY 평가 6개는 PASS다. 기존 아키텍처·보안 부채와 health 실패만 유지됐고 신규 회귀는 없다. 문서·마이그레이션·스택 smoke·workspace-stability도 통과했다.
- 2026-11-19: 기존 사용자 문서 7개의 해시와 출시 PRD의 RETURN 병합·잔여 작업을 보존했다. 로컬 설정은 이 작업에서 추가한 플래그 연결 외의 원래 내용을 유지한다. 회귀·공개 응답·원문 비실행·가입 부분 저장·정책 역행·기존 동의 보존을 검토했다.
- 2026-11-19: 완료 기록을 completed로 이동하고 모든 관련 링크를 갱신했다. 정책 네트워크·JSON 오류는 기술 오류 원문 대신 재조회 안내를 표시한다. 최종 작업 트리의 check 증거는 build/harness/check-summary.txt·상태·로그에서 확인한다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | feature/28, 기존 사용자 변경 보존 기준 확보 |
| `./gradlew compileJava` 최초 sandbox 실행 | BLOCKED | Gradle wrapper 캐시 잠금 접근 제한, 승인된 재실행으로 해소 |
| `./gradlew compileJava` | PASS | Java 17 컴파일 성공 |
| `./gradlew test --tests '*PolicyIntegrationTest' --tests '*migration.*' --tests '*MemberApiIntegrationTest'` | PASS | 초기 H2 집중 27개·skip 0개 |
| `./gradlew test --tests '*PolicyIntegrationTest'` | PASS | 최종 정책 집중 13개·skip 0개 |
| `POLICY_TEST_DB_URL=격리DB ./gradlew test --tests '*PolicyIntegrationTest' --tests '*FlywayMigrationTest'` | PASS | tmpfs MySQL 8.4.10의 당시 정책 12개·기존 이행 1개, JPA validate 포함 |
| `node src/test/js/policies.test.cjs` | PASS | 정책 화면 7개, 원문 HTML 비실행·선택 거절·버전 재확인·CSRF·중복 방어 |
| `node --test src/test/js/*.test.cjs` | PASS | 정책·기존 결제 운영·반품 테스트 파일 3개 모두 통과 |
| `./gradlew test` | FAIL | 전체 183개 중 기존 healthApiReturnsOk 1개만 실패·skip 0개 |
| `./scripts/harness migrations --accept-new` | PASS | V9만 추가, V1~V8 checksum 유지 |
| `./scripts/harness generate-docs` | PASS | 저장소·스키마 참조 갱신 |
| `./scripts/harness docs` | PASS | 링크·실행 계획·생성 문서 일치 |
| `./scripts/harness architecture` | BASELINED | 기존 common 의존 예외 3개 유지 |
| `./scripts/harness security` | BASELINED | 기존 위험 2개만 유지, 저장소 검사 통과 |
| `./scripts/harness check` | BASELINED | 183개·skip 0개·정확한 기존 health 실패 1개, POLICY 위험 평가 6개 PASS |
| `check: migrations / docs / harness-self-check / entropy` | PASS | 구조·문서·Flyway checksum 계약 유지 |
| `check: smoke` | PASS | 기존 실행 중 Compose app·MySQL·Redis 및 runtime actuator health 정상. 이번 기능의 Docker 배포 검증은 아님 |
| `check: doctor` | SKIPPED | 기존 포트 사용 중, .env 원문을 읽지 않아 시작 준비 상태 미검증 |
| `python3 사용자 문서 해시·기존 RETURN 근거 비교` | PASS | 관련 없는 기존 문서 7개 동일, 기존 출시 작업 보존 |
| `git diff --check` | PASS | 공백 오류 없음 |
| `브라우저 테스트 서버 종료` | PASS | 의도적 SIGTERM으로 JavaExec 종료 143, 검증 실패가 아님 |

## 위험과 롤백

미승인 정책 노출·동의 위조·회원만 저장되는 부분 성공·버전 역행을 집중 검증한다.
적용한 V9는 수정·삭제하지 않고 게시·동의 이력을 보존한다. 비활성화 시 신규 가입을 보류한다.

## 미해결 항목

운영 원문·필수 항목·판매자·배송·반품 조건과 기존 회원 적용 승인은 별도다.
기존 health 실패와 RETURN 브라우저 후반 검증은 이번 구현 범위 밖이다.
기능 코드·문서는 미커밋·main 미반영이며 로컬 설정 3개는 Git 제외 대상이다.

## 완료 조건

- [x] 관련 검증과 전체 check의 결과를 분류했다.
- [x] 출시 PRD의 구현·검증·main 반영 상태와 남은 작업을 갱신했다.
- [x] 사용자 변경 보존 및 회귀·보안·데이터 손실 위험을 검토했다.
