# 이슈 #34 회원 장바구니 구현

상태: 완료 · 로컬 구현·기술 검증 / main 미반영

## 목표

CART-01~10: 회원 저장·선택 checkout·서버 검증·승인 확정 후 멱등 정리를 구현하고 검증한다.

## 하지 않는 일

커밋·push·PR·운영 활성화·배포, 실제 Toss/S3 호출, 비회원 장바구니·부분 환불.

## 현재 증거

feature/34, main 3ed0c59 기준. 기존 변경 12개를 /private/tmp/cart-implementation-8tx9lnpi에
백업하고 preservation.json에 해시를 기록했다. 기존 엄격 테스트 발견 기준은 192개다.

## 결정

기존 계층·응답·주문 멱등성 유지. 승인 트랜잭션은 정리 작업만 준비하고 커밋 후 별도
트랜잭션으로 정리한다. 정리 실패는 결제 보상 취소로 전파하지 않는다.

## 작업 분해

- [x] 조사·사용자 파일 백업
- [x] V11·장바구니 API·주문 연결·정리 작업
- [x] 장바구니·복수 checkout·로그인 복귀
- [x] 집중·전체·MySQL·브라우저 검증
- [x] 리뷰·문서·출시 PRD·증거

## 진행 상황

- 2026-11-22: 승인된 구현 계획 착수. 기존 승인 완료 예외는 보상 취소를 시작하므로 정리를 분리한다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status / 백업` | PASS | 12개 사용자 파일 원본·해시 보존 |

## 위험과 롤백

가격·소유권·동시 수정·PG/DB 경계와 이중 정리가 주요 위험이다. 이번 변경만 분리해 되돌린다.
V11 적용 후에는 기존 마이그레이션을 수정하지 않는다. 사용자 변경을 reset하지 않는다.

## 미해결 항목

기능 구현·CART-01~10 기술 검증 완료. 운영 승인·활성화·배포·커밋/리뷰/main 반영은 별도다.

## 완료 조건

CART 시나리오 증거·관련 테스트·전체 check 분류, 사용자 파일 보존, 출시 PRD와 실제 상태 일치.

- 2026-11-22: H2 전체 205개·격리 MySQL 13개·JS 5개 파일·장바구니 HTTP 5개를 검증했다.
  Chrome 로그인 복귀·두 품목 담기·수량 변경·전체/개별 선택·새로고침·복수 checkout
  124,000원 모의 승인/정리, 43,000원 거절/유지·재로그인 유지 PASS.
  로그아웃 후 항목 잔상 문제를 수정하고 실제 브라우저 재확인했다.
  DTO 표기·테스트 유틸리티·마이그레이션 개수·기존 로그인 문자열 기대값에 따른 초회 실패는
  수정 후 전체 통과했다. 실패 기준선은 완화하지 않았다. 전체 하네스는 다음 단계다.


## 최종 검증과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `./gradlew test` | PASS | 전체 205개, 실패·오류·skip 0개. 기존 192개 유지·장바구니 신규 13개 추가 |
| `CART_TEST_DB_URL=... ./gradlew test --tests '*CartIntegrationTest'` | PASS | 격리 MySQL 8.4.10, 13개 실패·오류·skip 0개, V11 적용·JPA validate |
| `node --test src/test/js/*.test.cjs` | PASS | cart 8개·기존 JS 4개 파일 통과 |
| `python3 -B -m unittest discover -s src/test/python -v` | PASS | 정책 내보내기 회귀 3개 |
| `python3 -B src/test/python/cart_e2e_http.py` | PASS | 실제 HTTP 저장·버전/소유권/CSRF·복수 주문/승인/DB/PG·거절·후속 수정·재로그인 5개 |
| `python3 -B src/test/python/commerce_e2e_http.py` | PASS | 기존 구매·배송·RETURN·환불/재입고 종단 HTTP 6개 |
| `Chrome CUA` | PASS | 로그인 복귀·2종 담기·수량 변경·새로고침·전체/개별 선택·124,000원 모의 승인·정리, 거절 유지·재로그인·로그아웃 보호 |
| `./scripts/harness migrations --accept-new` | PASS | V11만 등록, 기존 10개 checksum 불변 |
| `HARNESS_APP_PORT=18082 ./scripts/harness check` | BASELINED | exit 0, 테스트·docs·migrations·eval·workspace 안정성 PASS, 기존 구조 3개/보안 2개만 유지 |
| `check: runtime smoke` | PASS | 테스트 전용 loopback 18082 /actuator/health, 운영 Compose 인수 아님 |
| `check: doctor / Compose stack` | SKIPPED | 비밀 파일 내용 미조회·실행 운영 스택 없음 |
| `git diff --check / 사용자 파일 대조` | PASS | 공백 검사, 원본 해시·공동 편집 diff 보관 |

- 2026-11-22: 최종 check가 BASELINED로 끝났다. 실패·오류·skip 없는 205개 전체 XML을
  확인했고 발견 목록에는 실제 통과한 CartIntegrationTest 13개만 추가했다. 실패·fingerprint·
  보안/구조 예외 기준선은 변경하지 않았다. 실제 Toss·S3·운영 DB는 호출하지 않았다.
- 2026-11-22: 정리와 승인 예외 경계·락 순서·타인 접근·CSRF·로그인 복귀·데이터 손실을
  검토했다. 정리 flush 실패는 모든 항목/결과를 롤백하고 READY만 남아 재시도한다.
  AFTER_COMMIT listener의 실패는 결제 승인/보상 취소 경로로 전파하지 않는다.
- 2026-11-22: 이번 실행에서 준비 상태를 구현·검증 상태로 갱신했고 ACCOUNT → DISCOVERY가
  다음 미구현 순서다. 커밋·push·PR·운영 승인·활성화는 수행하지 않았다.

증거 디렉터리는 `/private/tmp/cart-implementation-8tx9lnpi`다.
`verification.json`은 민감 값 없이 요약하며, check.log·check-summary.txt·full-tests.log·
mysql-tests.log·js-tests.log·http-results.json·commerce-http-results.json에 검사 결과가 있다.
Chrome 증거는 browser-multi-payment.jpg와 browser-cart-retained.jpg다.
[PRD 시나리오별 대조](../../product-specs/cart-prd.md), [운영 절차](../../runbooks/cart.md).

## 잔여 작업과 한계

실제 PG·S3·운영 DB·Compose 배포 인수·운영 정책 승인은 별도다. 기능 시나리오는
Java/JS·실제 HTTP·MySQL·Chrome 증거의 조합으로 PASS이며 모든 조합을 브라우저로
검증했다는 뜻은 아니다. 소스·문서는 로컬 미커밋이고 origin/feature/34에는 구현 미반영이다.
테스트 서버·이번 임시 MySQL 컨테이너는 검증 후 종료·제거했다. Docker 볼륨 삭제는 수행하지 않는다.

- 2026-11-22: 마지막 보존 대조에서 기존 9개 파일은 바이트 동일, 공동 편집 PRD/인덱스
  3개는 원본 대비 이번 구현 추가/갱신분만 확인하고 diff를 보관했다. 종료 전에 기록한
  runtime smoke PASS는 실행 당시 결과이며 현재 테스트 서버는 중지 상태다.

- 2026-11-22: 사용자 후속 요청으로 장바구니 준비·구현·검증 변경을 커밋하고
  origin/feature/34에 push한다. 위 미커밋·push 미수행 표현은 검증 완료 당시 기록이다.
  PR 생성·main 반영·운영 활성화는 별도다. 기존 다른 문서 변경은 포함하지 않는다.
