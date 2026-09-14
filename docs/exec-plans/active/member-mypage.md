# 마이페이지 회원정보·비밀번호 관리

상태: 활성 - 기존 환경 파일 부재로 전체 check 완료 차단

## 목표

인증된 회원이 이메일 기반 아이디와 프로필을 조회하고, 현재 비밀번호 확인 후
프로필 또는 비밀번호를 안전하게 변경할 수 있는 API와 정적 화면을 제공합니다.

## 하지 않는 일

- 이메일 변경, 계정 탈퇴, 주문 내역, 이메일 발송, 주소 검색을 추가하지 않습니다.
- 기존 CSRF·세션 고정 기술부채를 이 기능에서 확대하지 않습니다.
- 적용된 Flyway 마이그레이션을 수정하거나 배포·push하지 않습니다.

## 현재 증거

- `Member`에는 이메일, 이름, 비밀번호, 운영자 여부만 있습니다.
- 인증은 이메일 기반 세션이며 `/api/members/**`는 기존 보안 기본값으로 인증이 필요합니다.
- `./scripts/harness architecture`는 기존 common 의존 예외만 BASELINED이고 구조 검사는 PASS입니다.
- `./scripts/harness doctor`에서 Docker daemon만 SKIPPED 상태입니다.

## 결정

- 이메일을 수정할 수 없는 로그인 아이디로 사용합니다.
- 프로필과 비밀번호 변경 요청마다 현재 비밀번호를 확인합니다.
- 신규 가입자는 주소·전화번호·이메일 수신여부를 필수 입력합니다.
- 기존 회원은 주소·전화번호 빈 값, 수신거부 `N`으로 이관하고 첫 수정 시 완전한 값을 요구합니다.
- 전화번호는 `010-0000-0000`, 비밀번호는 8~72자를 허용합니다.

## 작업 분해

- [x] 조사
- [x] 도메인·API·마이그레이션 구현
- [x] 마이페이지·회원가입 UI 구현
- [x] 서비스·통합·마이그레이션 테스트
- [x] 검증·리뷰·문서화

## 진행 상황

- 2026-09-14: 현재 코드, PRD, 아키텍처, 테스트 기준선과 보안 경계를 조사했습니다.
- 2026-09-14: `codex/member-mypage` 전용 worktree에서 구현을 시작했습니다.
- 2026-09-14: 회원 API, V4 마이그레이션, 회원가입·마이페이지 UI와 실패 가드를 구현했습니다.
- 2026-09-14: 신규 집중 테스트와 회원가입 계약을 검증해 전체 테스트를 68개에서
  83개로 늘리고 기존 실패를 9개에서 5개로 줄였습니다.
- 2026-09-14: 기능 관련 아키텍처·마이그레이션·문서·테스트·평가는 통과했습니다.
  전체 check는 clean worktree에 기존 환경 파일이 없어 3개 게이트가 실패했습니다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `./scripts/harness architecture` | PASS | 기존 NC-ARCH-001 예외만 BASELINED |
| 회원 Service/API·마이그레이션 집중 테스트 | PASS | 신규·관련 테스트 17개 통과 |
| `./scripts/harness test` | BASELINED | 83개 중 신규 실패 없음, 기존 인증·상태 5개 실패 |
| `./scripts/harness test-baseline` | BASELINED | 83개, skip 0개, 정확한 fingerprint 실패 5개 |
| `./scripts/harness eval` | BASELINED | MEMBER-001~004와 AUTH-003 PASS, 기존 AUTH/OPS만 BASELINED |
| `./scripts/harness migrations` | PASS | V4 등록, V1~V3 checksum 불변 |
| `./scripts/harness docs` | PASS | 지식 graph와 생성 문서 최신 |
| `node --check .../script.js` | PASS | 정적 UI 스크립트 구문 정상 |
| `git diff --check` | PASS | whitespace 오류 없음 |
| `./scripts/harness check` | FAIL | 환경·security·smoke 3개 기존 clean-worktree 구성 실패 |

## 위험과 롤백

- 비밀번호는 BCrypt 결과만 저장하고 요청·응답·로그에 평문을 남기지 않습니다.
- 기존 회원의 개인정보를 추측하지 않고 빈 프로필로 이관합니다.
- 롤백은 애플리케이션 변경만 되돌리고 적용된 마이그레이션과 컬럼은 유지합니다.

## 미해결 항목

- clean worktree에는 `.env.example`, `.env`, `compose.yaml`, 추적된
  `application.yml`이 없습니다. 사용자 로컬 파일을 복사하거나 보안 기준선을
  약화하지 않았으며, 저장소 차원에서 복구 범위를 결정해야 합니다.
- NC-TEST-001의 demo 로그인·세션·health 실패 5개는 이번 기능 범위 밖이며
  정확한 기존 fingerprint로 유지합니다.

## 완료 조건

- [x] 관련 테스트와 `./scripts/harness check`를 실행하고 결과를 분류합니다.
- [ ] 기존 환경 파일 부재를 해결하고 security·smoke를 포함한 전체 check를 통과합니다.
- [x] API와 정적 UI가 PRD 및 문서와 일치합니다.
- [x] 회귀·보안·데이터 손실 위험을 검토합니다.
