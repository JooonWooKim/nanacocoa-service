# checkout 토스페이 결제수단 연동

상태: 완료

## 목표

checkout에 토스페이 직접 결제를 연결하고 카카오 페이, 카드 결제,
네이버페이는 준비 중인 비활성 결제수단으로 표시합니다. 결제 완료 화면은
토스 인증 이후 서버 승인이 성공한 경우에만 표시합니다.

## 하지 않는 일

- 토스페이 외 결제수단의 실제 결제 연동
- 결제 취소 UI, 라이브 키 전환, 배포, 원격 push
- 결제 Entity, 상태 모델, Flyway 스키마 변경

## 현재 증거

- 로컬 `feature/3`는 2026-08-22에 `origin/main`으로 fast-forward했습니다.
- checkout 주소검색과 수동 입력 fallback은 결제 UI 추가 후에도 유지됩니다.
- 서버의 기존 승인·조회 API, 주문 금액 검증, 멱등성·복구 흐름을 브라우저
  결제와 연결했습니다.

## 결정

- 별도 worktree 없이 사용자가 지정한 현재 `feature/3`에서 작업했습니다.
- 토스 SDK V2 직접 결제의 `CARD`/`DIRECT`/`TOSSPAY` 조합을 사용했습니다.
- 클라이언트 키만 인증된 설정 API로 제공하고 시크릿 키는 서버 환경변수에만
  유지했습니다.
- 승인 `202 PENDING`은 자동 polling 없이 사용자가 누르는 단일 상태 확인으로
  처리했습니다.
- 성공·실패 callback과 재시도에 필요한 주문 문맥·멱등성 키는 같은 탭의
  `sessionStorage`에 보관했습니다.
- 저장소에서 제외되는 로컬 `.env.example`, `compose.yaml`,
  `application.yml`에는 키 매핑을 추가하되 실제 키 값은 읽거나 기록하지
  않았습니다. 추적 코드의 `TossPaymentsProperties`도 환경변수 완화 바인딩을
  지원합니다.

## 작업 분해

- [x] `origin` 갱신과 `origin/main` fast-forward
- [x] 인증된 `GET /api/payments/client-config` Controller-Service 경계 추가
- [x] `TOSS_PAYMENTS_CLIENT_KEY` 속성·로컬 Compose·환경 템플릿 연결
- [x] 네 결제수단, 필수 약관, 접근 가능한 펼침 UI와 결과 패널 추가
- [x] 주문 생성 전 SDK·설정 준비와 서버 계산 주문 ID·금액 사용
- [x] 성공 callback 검증, 서버 승인, 수동 대기 상태 확인, 동일 주문 재시도
- [x] 주소검색 DOM·이벤트 흐름 보존
- [x] README·아키텍처·보안·로컬 개발·테스트 문서 갱신
- [x] 집중 테스트, 브라우저 점검, 하네스 검사와 회귀 검토

## 진행 상황

- 2026-08-22: `git fetch origin`과 `origin/main` fast-forward를 완료했습니다.
- 2026-08-22: 설정 API, 결제수단 UI, 토스 V2 직접 결제와 callback 흐름을
  구현했습니다.
- 2026-08-22: 테스트 68개와 문서·아키텍처·보안·브라우저 검증을 완료하고
  기존 실패 9개만 BASELINED임을 확인했습니다.

## 명령과 증거

| 명령·점검 | 상태 | 증거 |
| --- | --- | --- |
| `git fetch origin` | PASS | `origin/feature/3`가 `2837bf1`로 갱신됨 |
| `git merge --ff-only origin/main` | PASS | `57df56a..2837bf1` fast-forward |
| 결제·주문·정적 페이지 집중 테스트 | PASS | 새 설정 API 5개와 기존 결제·주문 계약 통과 |
| 앱 내 브라우저 데스크톱·390px | PASS | 토스 기본 선택, 나머지 disabled, 약관 차단, 접기·펼치기, 주소검색 보존 확인 |
| `node --check src/main/resources/static/script.js` | PASS | JavaScript 구문 오류 없음 |
| `./scripts/harness architecture` | PASS | Controller 경계·DTO·트랜잭션 검사 통과; 기존 예외 3개 BASELINED |
| `./scripts/harness security` | PASS | 저장소 검사 통과; 기존 위험 7개 BASELINED, `.env` 미열람 |
| `./scripts/harness docs` | PASS | 지식 graph·생성 문서 일관성 유지 |
| `./scripts/harness test-baseline` | BASELINED | 테스트 68개, skip 0개, 기존 fingerprint 실패 9개만 유지 |
| `./scripts/harness check` | BASELINED | 신규 회귀 없음; NC-TEST-001 엄격 실패 9개 유지 |
| 토스 테스트 상점 수동 결제 | SKIPPED | 실제 제공자 호출 금지와 키 비노출 원칙에 따라 자동화에서 실행하지 않음 |

## 위험과 롤백

- SDK·클라이언트 설정을 먼저 준비하므로 설정 실패는 주문 생성 전에 닫힙니다.
- 주문 생성 이후에는 서버 응답의 `orderId`와 `totalAmount`만 결제 요청에
  사용하며 callback 값 불일치 시 승인 API를 호출하지 않습니다.
- 결제 완료는 승인 API 또는 결제 상태 조회가 `SUCCESS`를 반환한 뒤에만
  표시합니다. `PENDING`은 자동 반복하지 않습니다.
- 무작위 고객 키와 승인 멱등성 키를 사용하며 이메일·전화번호를 고객 키로
  사용하지 않습니다.
- 클라이언트 설정 응답은 `clientKey`만 포함하며 시크릿 키는 브라우저로
  전달하지 않습니다.
- 기존 승인 API, 결제 상태, DB 스키마와 주소검색 이벤트는 변경하지 않았습니다.
- 변경은 이 작업의 로컬 커밋을 되돌려 복구할 수 있습니다. 원격 push·배포와
  worktree 삭제는 수행하지 않았습니다.

## 미해결 항목

사용자가 테스트 환경에 매칭되는 토스 클라이언트 키·시크릿 키를 주입한 뒤
테스트 상점에서 결제창 열기, 취소 복귀, 성공 승인을 수동 확인할 수 있습니다.
제공자 또는 테스트 상점 설정이 거부하면 해당 외부 점검만 BLOCKED로 분류합니다.

## 완료 조건

- 서버 승인 `SUCCESS` 이후에만 결제 완료 화면을 표시합니다.
- 주소검색, 인증 guard, 서버 주문 금액과 기존 결제 테스트에 새 회귀가 없습니다.
- 관련 테스트와 `./scripts/harness check` 결과가 상태 용어로 기록됐습니다.
