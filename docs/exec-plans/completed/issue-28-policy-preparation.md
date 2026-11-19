# 이슈 #28 POLICY 서비스 안내·약관·가입 동의 기능 준비

상태: 완료 · 기능 준비 완료, 정책 승인·기능 구현·검증 대기

## 목표

승인 대기 구조의 POLICY 구현 PRD 초안, GitHub 이슈와 최신 원격 main 기준
추적 브랜치를 준비하고 기존 사용자 문서 보존 및 검증 증거를 남긴다.

## 하지 않는 일

- 애플리케이션·스키마 구현, 정책 원문 작성·승인, 운영 활성화를 수행하지 않는다.
- 커밋, 구현 push, PR 생성·병합, 별도 worktree 생성을 수행하지 않는다.
- 기존 사용자 문서를 폐기하거나 실제 Toss Payments·S3를 호출하지 않는다.

## 현재 증거

- 2026-11-19 원격 main은 `0cee3bec457cced7468d61c2d068aa0669cc9300`이다.
  [PR #27](https://github.com/JooonWooKim/nanacocoa-service/pull/27)의 RETURN 병합을 확인했다.
- 현재 feature/26의 HEAD와 해당 main은 추적 파일 내용이 같다.
- 변경 문서 4개와 미추적 문서 3개를 임시 백업하고 SHA-256을 기록했다.
- 원격 이슈 13개와 열린 PR을 재조회했다. POLICY 이슈와 열린 PR은 없다.
- 가입 요청은 이메일 수신 선택만 저장하며 서비스 문서 버전·가입 동의 이력이 없다.
- RETURN은 승인 정보·버전·안내·조건이 있는 경우에만 신규 신청·처리를 허용한다.

## 결정

- 사용자가 계획 모드에서 승인 대기 구조를 선택하고 준비 계획 실행을 요청했다.
- P0 POLICY를 선정하며 원문·배송비·반품 기간·비용은 임의 확정하지 않는다.
- 게시·가입 동의와 승인·활성화 선행 조건을 명세하고 코드 구현은 별도로 진행한다.
- 기존 문서 수정은 보존하며 제품 인덱스와 출시 PRD에 준비 결과를 연결한다.
- 가입 동의의 필수 여부와 기존 회원 적용은 승인 항목으로 구분한다. 기존 회원의
  과거 동의를 추정하지 않으며 회원·가입 동의 원자적 저장과 게시 버전 검증을 명세한다.

## 작업 분해

- [x] 우선순위·원격 main·관련 이슈·PR 및 기존 변경 조사
- [x] 구현 PRD 초안·이슈·원격 추적 브랜치 준비
- [x] 현재 작업 트리에서 브랜치 전환 및 사용자 문서 보존 확인
- [x] 제품 인덱스·출시 PRD 연결
- [x] 문서·하네스 검증 및 회귀·보안·데이터 보존 검토
- [x] 증거 기록·completed 이동·완료 보고 자료 정리

## 진행 상황

- 2026-11-19: 준비 계획 실행을 시작했다. 원격 main과 PR #27 병합 및 기존 문서 7개의 백업·해시를 확인했다.
- 2026-11-19: [이슈 #28](https://github.com/JooonWooKim/nanacocoa-service/issues/28)과 원격 feature/28을 만들고 현재 작업 트리에서 추적 로컬 브랜치로 전환했다. 기존 문서 7개 해시가 모두 일치했다.
- 2026-11-19: [구현 PRD 초안](../../product-specs/service-policy-prd.md)을 저장했다. 승인·정책 원문·기능 구현은 대기 중이다.
- 2026-11-19: 제품 인덱스·출시 PRD에 준비 결과를 연결하고 인덱스의 RETURN main 반영 상태를 PR #27과 일치시켰다. 기존 사용자 문서 6개는 동일하고 출시 PRD는 POLICY 표·다음 구현 순서·준비 설명만 변경했음을 비교했다.
- 2026-11-19: 문서·공백 검사는 PASS. 전체 check는 170개·skip 0개·기존 health 실패 1개를 유지한 BASELINED이며 새 회귀는 없다. 실행 중인 Compose app·MySQL·Redis와 runtime health smoke는 PASS다.
- 2026-11-19: 승인되지 않은 정책 게시, 동의 위조·이력 덮어쓰기, 가입 부분 저장·자동 재제출 및 기존 정책 경계 회귀 위험을 검토했다. 코드·스키마는 변경하지 않았다.
- 2026-11-19: 준비 기록을 completed로 이동하고 연결을 갱신했다. 최종 작업 트리의 문서·check 판정은 `build/harness/check-summary.txt`와 상태·로그에서 확인한다. 정책 승인·기능 구현·브라우저 기능 검증은 준비 완료와 별도다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | feature/26, 기존 변경 문서 4개·미추적 문서 3개 확인 |
| `git ls-remote origin refs/heads/main` (sandbox) | BLOCKED | sandbox DNS 제한, 승인된 실행으로 해소 |
| `git ls-remote origin refs/heads/main` | PASS | sandbox DNS 제한 후 승인된 실행으로 0cee3be 확인 |
| `git diff --name-status HEAD 0cee3bec457cced7468d61c2d068aa0669cc9300` | PASS | 추적 파일 차이 없음 |
| `python3 사용자 문서 백업·SHA-256 기록` | PASS | 기존 문서 7개 보존 기준 확보 |
| `git fetch` (sandbox) | BLOCKED | .git/FETCH_HEAD 쓰기 제한, 승인된 실행으로 해소 |
| `git fetch origin refs/heads/main:refs/remotes/origin/main refs/heads/feature/28:refs/remotes/origin/feature/28` | PASS | .git 쓰기 제한 후 승인된 실행으로 원격 추적 ref 확보 |
| `git switch --track origin/feature/28` (sandbox) | BLOCKED | .git/index.lock 쓰기 제한, 승인된 실행으로 해소 |
| `git switch --track origin/feature/28` | PASS | .git/index.lock 제한 후 승인된 실행으로 feature/28 전환·upstream 설정 |
| `python3 사용자 문서 SHA-256 비교` | PASS | 브랜치 전환 후 기존 문서 7개 내용 일치 |
| `python3 기존 문서·출시 PRD 변경 범위 비교` | PASS | 6개 동일, 출시 PRD의 기존 RETURN 병합 갱신 보존. build/harness/policy-preparation-preservation.txt |
| `git diff --check` | PASS | 공백 오류 없음 |
| `./scripts/harness docs` | PASS | 로컬 링크·계획·생성 문서 일관성 유지 |
| `./scripts/harness check` | BASELINED | 준비 초안 검사에서 신규 회귀 없음. 완료 기록·링크 정리 후 최종 트리에서도 재실행 |
| `check: test-baseline` | BASELINED | 170개·skip 0개, healthApiReturnsOk 실패 1개의 ID·fingerprint 유지 |
| `check 내부 ./gradlew test` | FAIL | 기존 NC-TEST-001의 healthApiReturnsOk HTTP 404, 엄격 테스트는 FAIL 유지 |
| `check: architecture / security` | BASELINED | 기존 common 의존 예외 3개·보안 위험 2개 유지 |
| `check: migrations / docs / harness-self-check / entropy` | PASS | 기존 구조·문서·Flyway 불변성 유지 |
| `check: smoke` | PASS | 실행 중인 Compose app·MySQL·Redis 정상, runtime actuator health 통과 |
| `check: eval` | BASELINED | OPS-001 기존 health 부채 외 위험 평가 통과 |
| `check: doctor` | SKIPPED | 실행 포트는 사용 중, .env 원문을 읽지 않아 시작 준비 상태는 검증하지 않음 |

## 위험과 롤백

- 초안·미구현·미승인 상태를 구분하고 승인되지 않은 원문을 실제 정책으로 게시하지 않는다.
- 전환은 강제하지 않으며 변경 충돌 시 기존 파일을 보존한 채 원인을 확인한다.
- 되돌릴 때 이 준비의 문서 변경만 선택적으로 되돌린다. 기존 문서·원격 이슈·브랜치는 자동 삭제하지 않는다.

## 미해결 항목

- 정책 승인 정보·원문·필수 항목·기존 회원 적용·배송 및 반품 조건은 운영 승인 대기다.
- NC-TEST-001의 기존 health 실패와 RETURN 브라우저 후반 검증은 이번 준비에서 해결하지 않는다.
- PRD·준비 문서와 출시 PRD 변경은 미커밋·main 미반영이다.
- 구현 시 새 Flyway·회원/이력 원자성·브라우저 흐름을 검증해야 한다. 이번 check는 POLICY 기능 구현의 검증이 아니다.

## 완료 조건

- [x] PRD·이슈·원격 및 로컬 추적 브랜치를 준비했다.
- [x] 기존 사용자 문서 보존을 확인했다.
- [x] 문서와 하네스 검사 결과 및 잔여 부채를 분류했다.
- [x] 출시 PRD에 준비 상태와 남은 작업을 연결했다.
- [x] 코드 구현·정책 승인과 준비 완료를 구분한 보고 자료를 정리했다.
