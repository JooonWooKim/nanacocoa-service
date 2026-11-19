# 회원가입 반응형 UI·약관 펼침·카카오 주소 검색

상태: 완료

## 목표

첨부 레퍼런스의 테두리 폼·전체 동의·펼침 약관과 기존 카카오 주소 검색을 회원가입에 적용한다.

## 하지 않는 일

가입 API·서버 검증·정책 설정·다른 페이지 UI·기존 사용자 파일은 변경하지 않는다. 커밋·push하지 않는다.

## 현재 증거

feature/28, 기존 정책·가입 구현과 PR #4 주소 검색 공통 함수. health 실패 1개 기준선 유지.

## 결정

이메일 아이디·필수 주소 유지, 소셜 버튼 제외. 일반 텍스트 약관과 수동 주소 폴백 유지.

## 작업 분해

- [x] 조사
- [x] 구현
- [x] 검증
- [x] 리뷰와 문서화

## 진행 상황

- 2026-11-19: 기존 변경을 백업하고 승인된 계획의 구현을 시작했다.
- 2026-11-19: 이메일 아이디·필수 주소·기존 API를 유지하며 반응형 테두리 폼,
  비밀번호 확인/표시, 분할 전화번호, 전체 동의와 독립 펼침 약관을 구현했다.
  카카오 주소 검색은 checkout·마이페이지의 공통 함수를 재사용한다.
- 2026-11-19: 8080 H2 테스트 서버의 리소스를 갱신해 새 화면을 확인했다.
  브라우저에서 비밀번호 불일치·필수 동의 누락을 차단하고 선택 수신 거절 가입의
  로그인 화면 이동을 확인했다. 모두 합성 정책·계정이며 운영 승인·활성화는 변경하지 않았다.
- 2026-11-19: CSS가 `.signup-page` 아래로 제한됐고 가입 payload·CSRF·정책 변경
  재동의·중복 제출 방지가 유지됨을 검토했다. 원문은 textContent로 표시한다.
  사용자 문서 6개를 `/private/tmp/signup-ui-backup-x0c9loo6`와 바이트 비교해 보존을 확인했다.
- 2026-11-19: 최초 check의 문서 실패(생성 지도·완료 상태)를 정리했다.
  Compose 앱은 기존 8080 H2 테스트 환경을 위해 중지된 상태라 smoke는 FAIL이다.
  데이터베이스·Redis·운영 정책 설정과 테스트 기준선을 변경하지 않았다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | 기존 사용자 문서와 feature/28 확인 |
| `./gradlew test --tests '*MemberApiIntegrationTest' --tests '*NanacocoaServerApplicationTests.servesStaticPages'` | PASS | Java 15개, 실패 0개 |
| `node src/test/js/policies.test.cjs` | PASS | 11개: 전체/부분 동의·필수 우선 순서·본문 지연 조회·재시도·버전 교체·CSRF·응답 유실·중복 제출 |
| `node src/test/js/signup-fields.test.cjs` | PASS | 5개: 비밀번호·주소 선택 콜백·수동 폴백·주소 길이·수정 후 오류 해제 |
| `node --test src/test/js/*.test.cjs` | PASS | 정책·회원가입·반품·결제 운영의 전체 JS 파일 4개 |
| 모바일/데스크톱 브라우저 | PASS | 360·390·430·768·1280px에서 입력·버튼 가로 넘침 0개. 필수/선택 본문 독립 펼침, 선택 거절 합성 가입 성공 |
| 실제 카카오 팝업 주소 선택 | BLOCKED | SDK·주소검색 버튼 활성화 확인. 클릭 후 자동화 브라우저에 팝업이 노출되지 않아 실제 선택은 미확인. 콜백과 검색 실패 폴백은 집중 테스트 통과 |
| `./scripts/harness check` | FAIL | 최종 재실행: smoke 1개 게이트만 실패(중지된 Compose 앱). docs·migrations·harness-self-check·entropy·diff·workspace-stability PASS, architecture·security·test-baseline·eval은 기존 BASELINED 유지. 전체 183개/skip 0개, 기존 health 실패 1개만 존재. `build/harness/check-summary.txt` |
| `./gradlew test` (check 내부) | FAIL | 기존 `healthApiReturnsOk` HTTP 404 1개. 새로운 실패 없음 |
| `./scripts/harness eval` (check 내부) | BASELINED | POLICY 위험 평가 포함 통과, 기존 OPS 상태 확인 부채 유지 |

화면 증거는 Git에서 제외되는 `build/harness/signup-responsive-mobile.jpg`와
`build/harness/signup-responsive-desktop.jpg`다. 합성 문서의 `<script>`가 실행되지 않고
문자로 표시된다. 커밋·push는 수행하지 않았다.

## 위험과 롤백

필수 동의 우회·선택 수신 불일치·외부 검색 실패·기존 화면 스타일 회귀를 검증한다. 이 UI 변경만 선택적으로 되돌린다.

## 미해결 항목

운영 정책 승인·main 반영 검토·명시적 신규 가입 활성화·P0 종단 검증은 별도다.
실제 외부 팝업 주소 선택은 일반 브라우저에서 수동 확인이 남아 있다.
현재 H2 테스트 서버를 유지해 Compose 전체 스택 smoke 실패도 남는다.

## 완료 조건

화면·집중 회귀·전체 check 결과와 사용자 변경 보존을 기록했다.
출시 PRD와 서비스 정책 PRD에 UI 구현·검증 상태 및 운영 잔여 작업을 갱신했다.
이 기록은 UI 구현 완료이며 환경 smoke·외부 팝업·출시 승인 완료를 뜻하지 않는다.
