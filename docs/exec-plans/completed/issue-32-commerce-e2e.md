# 이슈 #32 구매·환불·재입고 종단 검증

상태: 완료 · 구현된 P0 기술 시나리오·Safari 실제 확인창·배송/반품 종단 검증 완료 · 운영 승인 별도

## 목표

실제 화면·API·DB를 연결해 구매부터 환불·복구·재입고까지 검증한다.

## 하지 않는 일

실제 PG·S3 호출, 운영 정책 승인·활성화, 배포·커밋·push·PR 생성·병합은 하지 않는다.

## 현재 증거

feature/32의 기존 사용자 파일 12개를 /private/tmp/p0-e2e-implementation-jjxv8h7b에 백업했다.
기존 Java 191개와 RETURN 후반 브라우저 BLOCKED 기록을 기준으로 한다.

## 결정

테스트 classpath의 loopback 서버·가상 SDK·모의 PG만 추가한다. 기존 API와 보안 경계를 통과한다.
격리 MySQL 8.4.10을 사용하고 사용자 볼륨을 건드리지 않는다.

## 작업 분해

- [x] 조사·기존 변경 백업
- [x] 테스트 전용 서버와 재현 방법 구현
- [x] H2·MySQL·실제 HTTP·기존 JavaScript 회귀 및 가상 fixture 수정
- [x] 브라우저 구매·신청·승인·검수 및 HTTP 처리 후 고객/관리자 후반 표시 확인
- [x] 브라우저 native 확인창 수락/취소와 환불·재입고 실행·전체 배송/반품 종단 검증
- [x] 최종 check·문서·보존 확인

## 진행 상황

- 2026-11-21: 재개 후 최종 HARNESS_APP_PORT=18082 check는 BASELINED로 완료했다. Java 192개 실패·오류·skip 0개, test-baseline·eval·문서·마이그레이션·공백·workspace-stability PASS다. 명시적 테스트 서버 runtime smoke도 PASS이며 운영 Compose 스택 인수로 취급하지 않는다. doctor의 비공개 .env 시작 준비 확인은 의도적으로 SKIPPED다. 기존 구조 예외 3개·보안 예외 2개만 유지하며 실패·예외 기준선을 약화하지 않았다. PRD 12개 기술 시나리오 모두 적절한 테스트 층의 PASS 근거를 연결했다. 새 제품 결함은 발견하지 않았으며 제품 코드 수정 없이 도구 대체와 회귀 보강으로 마무리했다.
- 2026-11-21: 완료 보고 전 이 작업의 loopback 검증 서버와 작업 전용 tmpfs MySQL 컨테이너만 정리한다. 관련 없는 기존 변경 파일 13개는 백업과 SHA-256 동일하고 나머지 7개는 이 작업의 문서 상태·결과·위치 갱신만 검토했다. 기존 기술부채·운영 승인·실거래 활성화는 별도다. 실행 기록을 completed로 이동하고 최종 문서·공백 검사를 수행한다.

- 2026-11-21: 재개 후 전체 엄격 Java 192개가 실패·오류·skip 0개로 통과했다. 신규 통과 ID 1개만 발견 목록에 등록했으며 실패 기준선은 유지한다. 최종 하네스를 이어서 실행한다.

- 2026-11-21: 재개 검증에서 Safari 네이티브 앱 제어로 Chrome 확장 도구의 확인창 장애를 우회했다. 실제 취소·확인 버튼으로 환불·재입고 취소의 무변경과 수락의 1회 반영을 검증했다. 확인창을 대역으로 교체하거나 제품 코드를 바꾸지 않았다. 구매 → 출고 → 배송 완료 → 본인 반품 신청 → 승인 → 회수 시작/완료 → 검수 → 환불 → 재입고 → 구매자 재로그인·결과 조회를 모두 실제 화면 조작으로 완료했다.
- 2026-11-21: 환불 지연 신청 3은 확인 중·재입고 없음·예정 전 개별 복구 비활성을 확인하고 30초 이후 실제 복구 확인창을 수락해 완료했다. 신청 4는 확정 실패 후 같은 사유의 명시적 재시도로 완료했다. 신청 5는 재판매 불가 표시와 재입고 작업 부재를 확인했다. 신청 1·2는 DB 재입고 완료와 PG 취소 1회, 신청 3·5도 취소 1회, 신청 4는 거절+재시도 총 2회를 대조했다. 고객 응답의 상태도 일치했다.
- 2026-11-21: 요구사항 대조 중 반려 후 동일 사유의 서버 재신청·과거 키 재전송 검증 공백을 보강했다. 새 통합 테스트가 H2 19개·MySQL 집중 67개에 포함돼 실패·오류·skip 0개로 통과했다. 발견 목록에 이 통과 ID 1개만 추가했고 실패·fingerprint·보안·아키텍처 예외 기준선은 변경하지 않았다. Python 정책 검증 3개·기존 JS 4개 파일·RETURN JS 집중 8개·HTTP 6개 재실행도 PASS다.
- 2026-11-21: 초기 원본과 사용자 변경을 /private/tmp/commerce-e2e-browser-xun4retk에 백업했다. 브라우저 DB 증거와 PNG를 build/e2e/browser에 복사했다. Chrome 장애 기록과 이전 FAIL/BLOCKED는 당시 증거로 보존하며 아래 Safari 재검증 결과가 최종 기능 상태다. 운영 정책·원격 main 반영은 별도다.

- 2026-11-21: 최종 check는 BASELINED로 완료했다. H2 전체 191개 실패·오류·skip 0개, test-baseline·eval·문서·마이그레이션·self-check·entropy·공백·workspace-stability PASS다. 기존 아키텍처 예외 3개와 보안 예외 2개만 유지하며 Compose 앱 미실행 smoke는 SKIPPED다. 사용자 파일 12개 중 관련 없는 8개는 SHA-256 동일하고 공동 편집 4개는 상태/결과 갱신과 추가만 검토했다. 운영 main 코드·DB migration·실패 기준선은 변경하지 않았다. 확인창 조작이 남아 있어 실행 기록은 active에 유지한다.
- 2026-11-21: 테스트 classpath의 별도 Gradle commerceE2e 서버를 구현했다. H2/Flyway/JPA validate·test 프로필·loopback·모의 PG·mock 저장소를 강제하고 운영 설정·기본 test 실행과 분리했다. 처음 fixture의 중복 Controller 매핑과 잘못된 snapshot 컬럼을 수정했다. 실제 API 계약에 맞춰 HTTP runner의 멱등성 헤더·운영 kind 필터·422 실패 응답 및 실행 전후 재고 대조를 보완했다. 제품 코드·스키마·실패 기준선은 변경하지 않았다.
- 2026-11-21: H2 전체 Java 191개·격리 MySQL 8.4.10 집중 66개·JS 4개 파일을 통과했다. 최종 빈 H2 서버의 실제 HTTP 시나리오 6개도 통과했고 재고는 [20,20,20,20,20,19], 예약은 모두 0이다. 판매 불합격 상품만 재입고하지 않았고 정상/지연/재시도 환불은 각각 허용된 호출 횟수로 완료됐다. 승인/환불 확인 중에는 기본 30초를 기다려 복구했으며 DB 시간을 수정하지 않았다.
- 2026-11-21: Chrome에서 checkout 로그인 복귀·실제 주문 생성·SDK 대역 콜백·승인 완료와 구매자 신청·관리자 승인·검수를 조작했다. 환불 native confirm에서 mouse 명령 및 focus 명령이 응답하지 않았다. dialog API·네이티브 제어·새 탭 복구를 시도했으나 확인창 수락/취소는 관찰하지 못했다. IAB 대체 경로는 loopback 연결 거부였다. 해당 조작은 도구 원인 BLOCKED다. HTTP로 저장한 환불·재입고 완료를 관리자·구매자 주문 화면에서 조회한 결과는 PASS이며 실행 검증과 구분한다.
- 2026-11-21: bootJar를 검사해 테스트 서버·SDK·snapshot·HTTP runner가 포함되지 않음을 확인했다. 실제 PG/S3 호출이나 운영 활성화는 없으며 검증 서버와 작업 전용 tmpfs MySQL 컨테이너만 종료·제거했다. Docker 볼륨은 사용·삭제하지 않았다. 생성 문서는 테스트 Java 파일 추가만 반영했다.
- 2026-11-21: 승인된 계획 실행을 시작했다. 기존 상태와 테스트 경계를 확인했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | feature/32의 사용자 변경 백업 |
| `HARNESS_APP_PORT=18082 ./scripts/harness check` 최종 | BASELINED | Java 192개·eval·문서·마이그레이션·runtime smoke PASS, 구조 3/보안 2 기존 예외만 유지 |
| `최종 사용자 파일 보존 대조` | PASS | 원본 20개 중 관련 없는 13개 SHA-256 동일, 관련 문서 7개 상태/경로 갱신 검토 |

| `./scripts/harness test` 재개 | PASS | 새 회귀 포함 Java 192개 실패·오류·skip 0개 |
| `Safari native 확인창 취소/수락` | PASS | 환불·재입고 각각 취소 시 무변경, 수락 시 DB·PG·재고 1회 반영 |
| `Safari 전체 배송/반품 종단 흐름` | PASS | 가상 주문 NC_8ce60a68-dac6-4ecf-a7be-1bc47e4d8ec3, 구매부터 출고·배송 완료·반품·회수·검수·환불·재입고·구매자 재로그인 |
| `Safari 환불 지연·실패·불합격` | PASS | 신청 3 개별 복구 확인창, 신청 4 같은 사유 재시도, 신청 5 재입고 작업 없음 |
| `브라우저 DB snapshot 대조` | PASS | build/e2e/browser/browser-db-evidence.json, 실제 브라우저 실행 결과와 고객/관리자·PG 호출 횟수 일치 |
| `./gradlew test --tests '*ReturnRequestIntegrationTest'` 재개 | PASS | 새 재신청·응답 유실 회귀 포함 H2 19개 실패·오류·skip 0개 |
| `격리 MySQL 8.4.10 재개 검증` | PASS | RETURN 19·결제 운영 13·재고 33·이행 2, 총 67개 실패·오류·skip 0개 |
| `python3 -B -m unittest discover -s src/test/python -v` | PASS | 정책 파일 보존·해시·이행 3개 |
| `node src/test/js/returns.test.cjs` | PASS | 재신청·키 유지·중복 제출·권한/복귀 집중 8개 |
| `python3 src/test/python/commerce_e2e_http.py` 재개 | PASS | 기존 서버의 별도 새 주문으로 6개 시나리오 재실행 |
| `HARNESS_APP_PORT=18082 ./scripts/harness smoke` | PASS | 명시적 테스트 서버 HTTP 200/UP. 운영 Compose 스택 검증과 구분 |

| `./gradlew commerceE2e` | PASS | loopback 18082, 실제 checkout/API·합성 정책·모의 PG·SDK 경로 실행 |
| `./gradlew test` | PASS | H2 전체 191개, 실패·오류·skip 0개 |
| `격리 MySQL 8.4.10 집중 Gradle 테스트` | PASS | RETURN 18·결제 운영 13·재고 33·이행 2, 총 66개 실패·오류·skip 0개 |
| `node --test src/test/js/*.test.cjs` | PASS | 기존 4개 파일, 실패·skip 0개 |
| `python3 src/test/python/commerce_e2e_http.py` | PASS | 빈 가상 DB의 6개 시나리오, 예약 모두 0, 재입고 가능 5개 상품 수량 유지·불합격만 1 감소 |
| `Chrome 구매·신청·승인·검수` | PASS | 실제 로그인·CSRF·구매 API·화면 동작 확인 |
| `Chrome HTTP 처리 후 상태 조회` | PASS | 고객·관리 화면의 환불/재입고 완료 표시. 증거는 /private/tmp/p0-e2e-implementation-jjxv8h7b/admin-refund-restock.jpg |
| `Chrome native confirm 수락/취소` | BLOCKED | click/focus 도구 timeout, dialog·native·새 탭 복구 후에도 조작 결과 미확인 |
| `./gradlew bootJar` | PASS | 테스트 서버·SDK·HTTP runner는 운영 jar에 없음 |
| `./scripts/harness generate-docs` | PASS | repository-map의 테스트 Java 파일 30→31 반영 |
| `./scripts/harness check` | BASELINED | H2 전체 191개 실패·오류·skip 0개, 기존 구조·보안 예외만 유지, 새 회귀 없음 |
| `./scripts/harness smoke` (check 내부) | SKIPPED | 실행 중인 Compose 앱 없음, 테스트 서버로 대체하지 않음 |
| `git diff --check` | PASS | 공백 오류 없음 |
| `사용자 파일 SHA-256·본문 대조` | PASS | 무관한 8개 바이트 동일, 공동 편집 4개 변경 검토 |

## 위험과 롤백

모의 결제 코드는 src/test에만 두고 운영 jar에 포함하지 않는다. 작업 추가분만 되돌릴 수 있다.

## 미해결 항목

구현된 P0 기술 시나리오의 브라우저 미확인은 해소했다. PRD의 12개 시나리오는
Java/JS·HTTP·Safari 실제 화면의 적절한 검증 층에 연결한다. 모든 실패 조합을 브라우저에서
반복했다는 뜻은 아니다. 운영 정책 승인·실제 PG·운영 Compose 배포·실데이터 인수·main 반영은
이번 기능 검증과 별도이며 수행하지 않았다.

브라우저 원본 증거: /private/tmp/commerce-e2e-browser-xun4retk.
대표 파일은 native-refund-restock.png, full-return-admin.png, full-return-buyer.png,
refund-pending.png, refund-recovered.png, damaged-no-restock.png, browser-db-evidence.json이다.
초기 /private/tmp/p0-e2e-implementation-jjxv8h7b의 기록도 보존한다.
신청 1·3·4·5의 초기 승인/검수는 API fixture로 준비하고 환불 이후는 실제 Safari로 실행했다.
신청 2는 구매부터 최종 조회까지 모든 상태 변경을 실제 Safari로 실행했다.

## 완료 조건

검증 증거와 실제 남은 작업을 출시 PRD·구현 PRD에 기록하고 사용자 변경을 보존한다.

2026-11-22 사용자의 별도 요청에 따라 이슈 #32 검증 작업을 feature/32의 커밋으로 관리한다. 원격 main 반영·운영 승인·활성화는 별도다. 위 로컬 미커밋·push 미수행 문구는 검증 완료 당시의 기록이다.
