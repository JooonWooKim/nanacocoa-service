# 이슈 #38 이메일 비밀번호 복구 구현

상태: 완료 · 로컬 구현·기술 검증 완료 / main 미반영

## 목표

RECOVERY-01~10 서버·화면·검증을 구현하고 실제 결과를 기록한다.

## 하지 않는 일

이메일 찾기·실제 SMTP/Toss/S3·운영 활성화·커밋·push·PR·병합.

## 현재 증거

feature/38, main f2ccaf4. 기존 문서 14개를 /private/tmp/account-recovery-implementation-6h9e85a4 에 백업했다.
Java 218개, 구조 예외 3개·보안 예외 2개를 유지한다.

## 결정

DB 인증 세대·일회용 토큰과 DB 요청 제한을 사용한다. 계정 조회·발송은 제한된 비동기
경로에서 처리하고 SMTP를 DB 트랜잭션 밖에서 실행한다.

## 작업 분해

- [x] 조사·원본 백업
- [x] 서버·화면 구현
- [x] 집중·전체·브라우저·MySQL 검증
- [x] 리뷰·제품/출시 문서·사용자 파일 보존 대조

## 진행 상황

- 2026-11-22: 인증 구조·PRD 확인, 사용자 변경 백업 후 구현 시작.
- 2026-11-22: 복구 API·화면·V13·인증 세대·SMTP 어댑터를 구현하고 H2 집중 및 JS 회귀를 검증했다. MySQL·브라우저·전체 회귀를 진행한다.
- 2026-11-22: H2·격리 MySQL 집중 24개, 전체 Java 237개, 신규 JS 11개·전체 JS 7개 파일, 실제 HTTP 4개와 Chrome 흐름을 확인했다. 같은 MySQL에서 Java 서버를 실제 재시작한 뒤 상태 보존도 통과했다.
- 2026-11-22: 최초 실패는 SMTP MIME/동기화 fixture와 기존 결제 테스트의 null 회원 principal이었다. 실제 메일 본문 파싱과 실제 로그인 fixture로 수정하고 기대 상태·기존 ID를 보존했다. V13 추가에 맞춰 기존 이행 횟수만 1씩 늘렸다. Gradle이 DB 환경 변수를 추적하지 않는 것을 확인해 MySQL은 --rerun-tasks로 실제 재검증했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status / SHA-256 백업` | PASS | 기존 문서 14개 보존 |
| `./gradlew test --tests '*Recovery*Test'` | PASS | H2 집중 24개(신규 19개·기존 결제 복구 5개) |
| `MySQL 집중 --rerun-tasks` | PASS | 격리 MySQL 8.4.13·별도 빈 V12→V13 이행 DB에서 24개 실제 실행 |
| `./gradlew test` | PASS | 전체 237개, 실패·오류·skip 0개, 기존 218개 ID 유지·신규 19개만 등록 |
| `node src/test/js/password-recovery.test.cjs` | PASS | 11개, 지연 성공/실패·이탈·CSRF 대기 무효화·새 화면 보호 |
| `node --test src/test/js/*.test.cjs` | PASS | 전체 JS 7개 파일 |
| `python3 -B src/test/python/recovery_e2e_http.py` | PASS | HTTP-RECOVERY-01~04, 독립 세션 2개·재설정·CSRF·신규 로그인·장바구니/배송지 조회 |
| `Chrome 실제 브라우저` | PASS | 요청→모의 메일→fragment 제거→재설정→기존 세션 차단→신규 로그인→cart 조회 |
| `recovery_restart_http.py + Java 재시작` | PASS | 실제 Java 프로세스 재시작 후 비밀번호·소비 토큰 거부·요청 제한 보존, 세대 1·소비 토큰 1개 |
| `./scripts/harness docs` | PASS | 최초 활성 계획 진행 증거 형식 오류를 수정하고 재검사 통과 |
| `HARNESS_APP_PORT=18086 ./scripts/harness check` | BASELINED | exit 0, 기존 구조 예외 3개·보안 예외 2개 유지, 새 회귀 없음 |
| `check: test-baseline / eval` | PASS | 최종 Java 237개 실패·오류·skip 0개, 기존 발견 ID·빈 실패 기준선 유지 |
| `check: docs / migrations / self-check / entropy / diff / workspace-stability` | PASS | 최종 소스 계약·기존 V1~V12 checksum 유지 |
| `check: runtime smoke` | PASS | 명시적 loopback 18086의 Actuator UP, 운영 Compose 스택 인수는 미실행 |
| `check: doctor / Compose 스택 인수` | SKIPPED | 비밀 설정 미조회·실행 중인 운영 스택 없음 |
| `원본 SHA-256·공동 편집 diff 대조` | PASS | 기존 14개 중 11개 바이트 동일, PRD·출시·제품 인덱스 3개는 의도한 갱신만 반영 |

## 위험과 롤백

세션 검증·토큰 소비·DB 실패·발송 정보 유출을 집중 검사한다. 기존 마이그레이션·
실패 기준선을 완화하지 않는다. 이번 변경만 원본 백업과 대조해 분리한다.

## 미해결 항목

실제 공급자 선정·실발송·배포·운영 인수는 별도다.

## 완료 조건

집중·전체 테스트와 docs/diff/check 결과를 분류하고 PRD·출시 문서·원본 보존 대조를 마친다.

## 완료 산출물

- [복구 PRD](../../product-specs/account-recovery-prd.md) RECOVERY-01~10 PASS
- [운영·격리 검증](../../runbooks/account-recovery.md), 출시 PRD·제품 인덱스·아키텍처·생성 참조 문서 갱신
- 증거: /private/tmp/account-recovery-implementation-6h9e85a4 의 check.log·check-summary.txt,
  mysql-summary.json·all-tests-summary.json·new-test-ids.txt·javascript.log·http.log·restart-evidence.txt·browser-reset-success.jpg
- 원본은 files/·manifest.json·before.patch, 공동 편집 대조는 preservation.json·세 implementation.diff에 보관
- 로컬 feature/38 → origin/feature/38, HEAD f2ccaf4 유지. 신규 커밋·구현 push·PR·병합 없음
- 실제 SMTP·Toss·S3·운영 설정 변경·실발송·정책 승인·배포·활성화 없음

기능 검증과 운영 인수를 구분한다. 실제 공급자·발신 도메인·HTTPS·비밀 설정·전
인스턴스 세션 검증 배포와 운영 승인 후 별도 활성화해야 한다. 다음 미구현 기능은
DISCOVERY다. 이번 작업에서 시작한 검증 서버·임시 MySQL만 종료하고 기존 볼륨을 삭제하지 않는다.
