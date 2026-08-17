# 체크아웃 주소검색 UI 및 Kakao 우편번호 연동

상태: 완료

## 목표

체크아웃 주소 입력을 우편번호, 기본주소, 상세주소로 나누고 Kakao 우편번호
팝업 검색을 연결하되 기존 주문 API의 `shippingAddress` 문자열 계약을 유지한다.

## 하지 않는 일

- 주문 API, DTO, Entity, DB 스키마를 변경하지 않는다.
- API 키, 자격 증명, 주소 mock 데이터를 추가하지 않는다.
- 체크아웃 외 정적 페이지의 UI를 변경하지 않는다.

## 현재 증거

- `checkout.html`은 현재 `shippingAddress` textarea 하나를 제공한다.
- `script.js`는 입력값을 그대로 주문 API payload에 전달한다.
- `CreateOrderRequest.shippingAddress`는 필수이며 최대 500자다.
- 정적 페이지 접근은 `SecurityConfig`의 기존 허용 규칙으로 처리된다.

## 결정

- Kakao 우편번호 스크립트는 체크아웃 초기화 시 비동기로 준비한다.
- 검색은 사용자의 `주소검색` 버튼 클릭에서만 팝업으로 연다.
- 외부 스크립트 실패 시 검색만 비활성화하고 수동 입력은 유지한다.
- 서버에는 `[우편번호] 기본주소 상세주소` 형식의 단일 문자열을 보낸다.

## 작업 분해

- [x] 조사
- [x] 구현
- [x] 검증
- [x] 리뷰와 문서화

## 진행 상황

- 2026-08-07: `codex/checkout-address-search` 전용 worktree를 만들고 기존 UI,
  JavaScript 제출 흐름, 보안 허용 규칙과 테스트 기준선을 확인했다.
- 2026-08-07: 주소 입력 마크업, 반응형 스타일, Kakao 로더, 검색 클릭 콜백,
  수동 입력 폴백과 500자 조합 검증을 구현했다.
- 2026-08-07: H2 인메모리 로컬 서버에서 로그인, 상품 조회, 주소 검증,
  수동 주소 주문 생성과 1280px/390px 레이아웃을 확인했다. 앱 내 브라우저는
  Kakao 팝업의 별도 창을 노출하지 않아 결과 선택 UI 자동화는 수행하지 못했지만,
  스크립트 로드와 팝업 호출 시 콘솔 오류가 없음을 확인했다.
- 2026-08-07: 전체 하네스가 새 회귀 없이 BASELINED로 통과했고, 변경 diff를
  회귀, 보안, 데이터 손실 관점에서 검토한 뒤 임시 검증 자원을 모두 제거했다.
- 2026-08-10: 구현 파일과 완료 계획을 `feature/2`로 옮기고 JavaScript 구문 및
  정적 페이지 집중 테스트를 다시 통과했다. 전체 하네스는 이 브랜치에 없는
  `.env.example`·`compose.yaml`, 그에 따른 security·smoke 기준선 차이와 샌드박스의
  Gradle 배포 잠금 파일 권한 문제로 FAIL했으며 주소 검색 변경과 무관한 상태로 분류했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git worktree add -b codex/checkout-address-search ...` | PASS | 전용 worktree 생성 |
| `node --check src/main/resources/static/script.js` | PASS | JavaScript 구문 정상 |
| `./gradlew test --tests ...servesStaticPages` | PASS | 정적 주소 마커 제공 |
| 로컬 H2 서버 브라우저 검증 | PASS | 필수값, 500자 제한, 주문 생성, 반응형 배치 확인 |
| 주소 helper 및 Kakao 로더 Node 검증 | PASS | 상세주소 유무 조합과 로드 실패 rejection 확인 |
| `./scripts/harness check` | BASELINED | 테스트 63개, 기존 실패 9개 동일, 새 회귀 없음 |
| `feature/2`에서 `node --check ...` 및 정적 페이지 집중 테스트 | PASS | 이전 구현과 동일한 파일, Gradle 테스트 성공 |
| `feature/2`에서 `./scripts/harness check` | FAIL | 브랜치의 기존 구성 파일 부재·기준선 차이와 샌드박스 Gradle lock 권한 문제 |

## 위험과 롤백

- 외부 CDN 차단 시 우편번호 검색이 불가능하므로 명시적인 수동 입력 안내를 제공한다.
- 조합 주소가 500자를 넘으면 서버 오류 전에 클라이언트 검증으로 차단한다.
- 롤백은 이 브랜치의 체크아웃 정적 파일과 정적 페이지 테스트 변경만 되돌린다.

## 미해결 항목

- 자동화 브라우저가 Kakao 팝업 창을 노출하지 않아 실제 결과 항목 선택은 수동
  브라우저 확인 영역으로 남는다. 로더 성공·실패와 선택 콜백 코드는 별도로 검토했다.

## 완료 조건

- 주소 선택, 수동 입력, 검증 실패, 외부 스크립트 실패 경로를 확인한다.
- 관련 테스트와 `./scripts/harness check` 결과를 분류한다.
- 실행 증거를 기록하고 이 계획을 `completed/`로 이동한다.
