# 결제 성공 콜백 로그인 복구

상태: 완료

## 목표

토스 결제 성공 콜백에서 세션이 만료되어도 로그인 후 동일한 콜백으로 돌아와
기존 결제 승인 요청을 안전하게 재개합니다.

## 하지 않는 일

백엔드 결제 API, 멱등성 계약, 다른 미해결 PR 리뷰는 변경하지 않습니다.

## 현재 증거

- `script.js`의 로그인 성공 처리는 항상 `index.html`로 이동합니다.
- 결제 성공 콜백의 `paymentKey`는 URL에만 있으며 결제 문맥에는 저장하지 않습니다.
- 승인 요청의 401 처리는 로그인 링크만 표시해 콜백을 복구할 수 없습니다.

## 결정

- 성공 콜백 URL을 현재 탭의 `sessionStorage`에만 보존합니다.
- 로그인 성공 시 저장값을 먼저 삭제하고 현재 origin의 유효한
  `checkout.html` 성공 콜백만 복구합니다.
- 누락·변조·외부 origin 값은 폐기하고 기존 `index.html`로 이동합니다.

## 작업 분해

- [x] 조사
- [x] 구현
- [x] 검증
- [x] 리뷰와 문서화

## 진행 상황

- 2026-08-28: PR 리뷰와 현재 로그인·결제 콜백 흐름을 대조했습니다.
- 2026-08-28: 전용 `codex/preserve-payment-callback` worktree를 생성했습니다.
- 2026-08-28: 성공 콜백 보존·일회성 복구·동일 origin과 경로 검증을 구현했습니다.
- 2026-08-28: 관련 통합 테스트와 68개 전체 테스트 기준선을 검증했습니다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `node --check src/main/resources/static/script.js` | PASS | JavaScript 구문 정상 |
| `./gradlew test --tests com.nanacocoa.server.NanacocoaServerApplicationTests.servesStaticPages` | PASS | 콜백 보존·검증·로그인 복귀 계약 통과 |
| `./scripts/harness test-baseline` | BASELINED | 테스트 68개, skip 0개, 기존 fingerprint 실패 9개 유지 |
| `./scripts/harness docs` | PASS | 실행계획과 문서 계약 통과 |
| `./scripts/harness architecture` | PASS | 기존 아키텍처 기준선 유지 |
| `./scripts/harness migrations` | PASS | Flyway 이력 변경 없음 |
| `./scripts/harness check` | FAIL | worktree에 없는 ignored 로컬 환경 파일과 sandbox Gradle 잠금으로 환경·security·smoke·test 게이트 실패 |

## 위험과 롤백

잘못된 복귀 URL은 외부 이동 또는 로그인 반복을 만들 수 있습니다. origin, 경로,
필수 콜백 파라미터를 검증하고 저장값을 일회성으로 소비합니다. 롤백은 이 계획의
`script.js`와 정적 자원 테스트 변경만 되돌립니다.

## 미해결 항목

- `.env.example`, `compose.yaml`, `application.yml`은 원본 작업 트리에만 있는 ignored
  로컬 파일이어서 전용 worktree의 전체 check 환경·security·smoke 게이트는
  이 변경과 무관하게 실패합니다.

## 완료 조건

- 성공 콜백의 인증 만료 후 로그인 복귀 경로가 보존됩니다.
- 외부·일반·불완전 URL은 복구되지 않습니다.
- 관련 테스트는 통과했고 전체 기준선에 새 실패가 없으며 check 실패는 로컬
  환경 부재로 분류했습니다.
