# PR #37 주소록 지연 응답 무효화

상태: 완료 · 로컬 수정 검증 / 원격 미반영

## 목표

계정 변경·로그아웃·페이지 이탈 후 이전 관리 화면의 응답이 개인정보·안내·버튼 상태를 복원하지 못하게 한다.

## 하지 않는 일

API·DB·HTML·CSS 변경, 자동 쓰기 재시도, 커밋·push·리뷰 해결 처리·병합·운영 활성화.

## 현재 증거

feature/36의 45616a6에 대한 PR #37 P2 리뷰. 관리 화면 reload/mutate가 await 결과를 무조건 반영한다.
기존 사용자 변경 9개와 수정 대상 원본을 임시 디렉터리에 백업하고 해시를 기록한다.
Java 발견 기준은 218개이며 실패/구조/보안 기준선은 변경하지 않는다.

## 결정

화면 작업별 세대·계정을 고정하고 성공·실패·후속 조회·finally를 검사한다.
clearPrivate는 모든 이전 작업을 무효화한다. 초기 await 전에 focus 감시를 등록한다.

## 작업 분해

- [x] 조사·기존 파일 백업
- [x] 지연 응답 회귀를 추가하고 수정 전 실패 확인
- [x] 요청 세대와 전송 전/응답 후 검사 구현
- [x] 집중·전체·브라우저 검증
- [x] PRD·출시 문서와 완료 기록

## 진행 상황

- 2026-11-22: 사용자 승인 계획으로 착수. 읽기 전용 재현에서 계정 변경 후 이전 목록·성공 안내 복원을 확인했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status / 백업` | PASS | 사용자 변경 보존, 기능 코드 원본 보관 |

## 위험과 롤백

이전 오류나 finally가 새 작업을 훼손하지 않도록 검사한다. 전송된 쓰기의 서버 처리를 취소했다고 표시하지 않는다.
이번 수정만 분리해 원본과 대조한다. 사용자 문서를 reset하지 않는다.

## 미해결 항목

로컬 수정 검증 완료. 커밋·원격 push·PR 리뷰 해결 처리·병합은 수행하지 않았다.

## 완료 조건

수정 전 회귀 실패·수정 후 통과, 전체 check 분류, 개인정보 재표시 없음과 기존 동작 유지 증거, 문서 갱신 후 완료 보관.


- 2026-11-22: 원본·해시는 /private/tmp/pr37-stale-response-er3flae1에 보관했다.
  기존 10개 통과·신규 회귀 21개 실패를 red.log에서 확인한 뒤 수정했다.
  화면 작업별 세대/계정 고정·clearPrivate 무효화·동일 세대 오류 재조회·finally 검사와
  초기 await 이전 focus 등록을 적용했다. shared request의 기본 유효성 검사는 항상 true로
  유지해 checkout 기존 흐름은 보존하며 관리 화면만 전송 전/응답 후 유효성을 전달한다.
- 2026-11-22: 주소록 JS 31개·전체 JS 6개 파일 PASS. 브라우저 18084 합성 fixture에서
  보류 GET→계정 전환→이전 응답 해제 후 빈 목록/로그인 안내 유지와 새 계정 명시적 조회를 확인했다.
  fixture는 실제 JS와 주소록 HTML을 사용하며 인증/네트워크는 합성 제어다. 실제 Spring 인증의
  교차 탭 통합 전체를 새로 검증한 것은 아니다. 스크린샷에는 원문 주소가 없다.


## 최종 검증

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `node --test src/test/js/shipping-addresses.test.cjs` 수정 전 | FAIL | 기존 10개 PASS, 신규 지연 응답 21개 FAIL. red.log |
| `node src/test/js/shipping-addresses.test.cjs` 수정 후 | PASS | 총 31개, fail/cancel/skip 0개. green-direct.log |
| `node --test src/test/js/*.test.cjs` | PASS | 주소록 포함 전체 6개 JS 파일 통과. all-js.log |
| `Chrome 합성 지연 응답 fixture` | PASS | 18084에서 보류 GET→계정 전환→이전 응답 해제 후 빈 목록/로그인 안내 유지·새 계정 명시적 조회 |
| `./scripts/harness check` | BASELINED | exit 0, Java 218개 실패/오류/skip 0개·기존 구조 3개/보안 2개 예외만 유지 |
| `check: docs / migrations / eval / self-check / entropy / git-diff-check` | PASS | 기존 계약 유지·코드/실행 파일 안정성 PASS |
| `check: doctor / smoke` | SKIPPED | 환경 원문 미조회·실행 Spring/Compose 스택 없음. 합성 브라우저 서버를 runtime smoke로 취급하지 않음 |
| `사용자 파일 SHA-256 대조` | PASS | 시작 시 변경/미추적 파일 9개 모두 바이트 동일 |

- 2026-11-22: 최종 전체 check(BASELINED)와 Java 218개 통과를 확인했다. Java 발견/실패·
  fingerprint·구조/보안·마이그레이션 기준선은 변경하지 않았다. API·DB·HTML·CSS도 미변경이다.
- 2026-11-22: 늦은 성공·실패·finally, 오류 후 조회의 세대 유지, 초기 focus 등록,
  인증 확인 대기 중 전송 차단을 검토했다. 이미 보낸 변경은 서버에서 완료될 수 있으며
  이를 취소된 것으로 표시하거나 자동 재전송하지 않는다. 기존 멱등 등록 재시도·입력 보존은 유지한다.
- 2026-11-22: [PRD ADDRESS-07](../../product-specs/shipping-address-prd.md)과
  [출시 PRD](../../product-specs/commerce-launch-prd.md)에 기존 검증 누락과 보완 결과를 기록했다.
  커밋·push·PR 리뷰 해결 처리·병합·운영 활성화는 미수행이다.

증거 디렉터리: /private/tmp/pr37-stale-response-er3flae1.
verification.json·check.log·check-summary.txt·red.log·green-direct.log·all-js.log와
preservation.json/preservation-after.json을 보관했다. 브라우저 증거는
browser-stale-response-blocked.jpg다. fixture는 실제 관리 JS/HTML과 합성 인증/네트워크를
사용하며 Spring의 실제 교차 탭 인증 전체를 새로 검증한 것은 아니다. 이번 검증 서버는 종료한다.

- 2026-11-22: 사용자 후속 요청으로 리뷰 수정·회귀·관련 문서를 로컬 커밋한다.
  위 미커밋 문구는 검증 완료 당시 기록이며 push·리뷰 해결 처리·병합은 수행하지 않는다.
