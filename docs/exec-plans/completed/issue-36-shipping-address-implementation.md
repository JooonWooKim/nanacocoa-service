# 이슈 #36 회원 배송지 관리 구현

상태: 완료 · 로컬 구현·기술 검증 / main 미반영

## 목표

ADDRESS-01~08 주소록·기본값·checkout 자동 입력/선택·주문 보존을 구현하고 검증한다.

## 하지 않는 일

계정 복구·이메일 발송, 기존 주소 이행/동기화, 커밋·push·PR·병합·운영 활성화·배포·실제 PG/S3 호출.

## 현재 증거

feature/36, main 8ae5def 기준. 기존 문서 13개를 /private/tmp/shipping-address-implementation-wn872x04에
복사하고 preservation.json에 SHA-256을 기록했다. 기존 Java 발견 기준은 205개다.

## 결정

회원 잠금으로 변경을 직렬화하고 주소록 revision과 항목 version을 검사한다. 기본값은 회원별
단일 포인터로 저장한다. 신규 checkout 미편집 상태에만 기본값을 한 번 적용한다. 기존 주문 API는 유지한다.

## 작업 분해

- [x] 조사·원본 백업
- [x] V12·주소록 API·경합/재전송 방어
- [x] 주소록 화면·checkout·로그인 복귀·잔상 방어
- [x] H2·MySQL·JS·HTTP·브라우저 검증
- [x] 리뷰·출시 PRD·문서·증거

## 진행 상황

- 2026-11-22: 사용자 요청으로 구현 착수. 기존 프로필 본인 확인·주문 스냅샷을 유지한다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status / SHA-256 백업` | PASS | 기존 13개 파일 보존 |

## 위험과 롤백

기본값 경합·늦은 조회의 폼 덮어쓰기·계정 전환·개인정보 노출이 주요 위험이다.
사용자 원본을 유지하며 이번 변경만 분리한다. 적용된 마이그레이션은 수정하지 않는다.

## 미해결 항목

ADDRESS-01~08 기술 시나리오 검증 완료. 이메일 복구·운영 승인·활성화·배포·커밋/리뷰/main 반영은 별도다.

## 완료 조건

PRD 시나리오 증거·집중/전체 check 분류·기존 파일 보존 대조·출시 PRD 갱신 후 완료 보관한다.


- 2026-11-22: V12·주소록/기본 포인터/등록 키·버전 검사와 관리/checkout UI를 구현했다.
  초기 H2 12개·전체 Java 217개를 통과했고 V11 데이터 보존 회귀를 추가해 집중 13개로 확장했다.
  최초 MySQL 12개는 PASS였다. 최종 13개 실행은 Gradle up-to-date 재사용을 발견해
  --rerun-tasks로 재실행한다. 캐시된 H2 결과를 MySQL 증거로 기록하지 않는다.
- 2026-11-22: JS 10개·기존 JS 5개 파일과 배송지 HTTP 5개 PASS. Chrome 등록·수정·기본 변경/삭제,
  자동 입력·입력 후 재조회 보존·다른 주소 선택·직접 구매 모의 승인·복수 checkout 자동 입력을 확인했다.
  장시간 경과로 실제 세션이 만료된 뒤 원문 제거·로그인 복귀·주소록 유지도 PASS였고
  checkout 로그아웃 후 배송 정보가 사라짐을 확인했다. 증거 화면은 주소·연락처 없는 승인 결과만 저장했다.


## 최종 검사와 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `./gradlew test` (최종 check 내부) | PASS | 전체 218개, 실패·오류·skip 0개, 기존 205개 유지·신규 13개 추가 |
| `ADDRESS_TEST_DB_URL=... ADDRESS_MIGRATION_TEST_DB_URL=... ./gradlew test --rerun-tasks --tests '*ShippingAddressIntegrationTest'` | PASS | 격리 MySQL 8.4.10 13개, 실패·오류·skip 0개, V11 회원·주문·장바구니 보존·V12 validate |
| `node --test src/test/js/*.test.cjs` | PASS | 주소록 10개·기존 JS 5개 파일 통과 |
| `python3 -B -m unittest discover -s src/test/python -v` | PASS | 정책 내보내기 회귀 3개 |
| `python3 -B src/test/python/shipping_address_e2e_http.py` | PASS | 실제 HTTP 등록/기본/버전·소유권/CSRF/계정/프로필 경계·스냅샷/재전송·재로그인·삭제 재전송/PG 미호출 5개 |
| `python3 -B src/test/python/cart_e2e_http.py` | PASS | 기존 장바구니 실제 HTTP 5개 |
| `python3 -B src/test/python/commerce_e2e_http.py` | PASS | 구매·배송·RETURN·환불·재입고 실제 HTTP 6개 |
| `Chrome CUA` | PASS | 등록·수정·기본 변경/삭제·자동 승격 없음·직접/복수 checkout 기본 입력·입력 후 재조회 보존·명시적 선택·41,000원 모의 승인 |
| `Chrome 실제 세션 만료/재로그인/checkout 로그아웃` | PASS | 만료 시 주소 제거·복귀 조회·주소록 유지·로그아웃 후 원문 비노출 |
| `./scripts/harness migrations --accept-new` | PASS | V12만 등록, 기존 V1~V11 checksum 불변 |
| `HARNESS_APP_PORT=18082 ./scripts/harness check` | BASELINED | exit 0, 구조 3개·보안 2개 기존 예외만 유지, 새 회귀 없음 |
| `check: docs / migrations / eval / self-check / entropy / git-diff-check` | PASS | 문서·기존 이력·평가·하네스·공백 계약 유지 |
| `check: runtime smoke` | PASS | 테스트 전용 loopback 18082 /actuator/health, 운영 Compose 인수 아님 |
| `check: doctor / operational stack` | SKIPPED | 환경 원문 미조회·실행 운영 스택 없음 |
| `사용자 파일 해시/공동 편집 diff 대조` | PASS | 기존 10개 바이트 동일, PRD/출시/제품 인덱스 3개는 이번 구현 추가·상태 갱신만 반영 |

- 2026-11-22: 최종 Java 218개와 전체 check(BASELINED)를 확인했다. 발견 목록에는 실제
  통과한 신규 13개만 추가했다. 실패·fingerprint·구조/보안 예외 기준선은 변경하지 않았다.
  MySQL은 캐시 재사용을 배제한 최종 --rerun-tasks 실행에서 13개가 모두 통과했다.
- 2026-11-22: 회원 잠금·revision·기본 포인터의 소유권 FK, 등록 재전송과 롤백·개인정보
  비노출을 검토했다. 잠금 획득 실패는 주소록 충돌로 안내한다. URL/브라우저 저장소에
  전체 주소록을 기록하지 않고 등록 기록에는 원문 대신 해시만 저장한다.
- 2026-11-22: 이번 작업은 feature/36 로컬 미커밋이다. 커밋·push·PR·병합·실제 PG/S3·
  운영 DB·운영 승인·활성화·배포는 수행하지 않았다. 다음 미구현 기능은 이메일 계정 복구다.

[구현 PRD](../../product-specs/shipping-address-prd.md), [운영 절차](../../runbooks/shipping-addresses.md),
[출시 PRD](../../product-specs/commerce-launch-prd.md)에 실제 구현과 남은 작업을 반영했다.
증거는 /private/tmp/shipping-address-implementation-wn872x04의 verification.json·check.log·
check-summary.txt·mysql-tests.log·js-tests.log·address-http.json·preservation-after.json과
공동 편집 implementation.diff다. browser-address-payment.jpg는 주소/연락처 없는 승인 결과다.

## 검증 한계와 남은 작업

ADDRESS-01~08은 Java·JS·HTTP·DB·Chrome 증거를 조합한 PASS이며 모든 조합을
브라우저로 검증했다는 뜻은 아니다. 외부 주소 검색 서비스의 실주소 선택은 이번 범위가 아니며
직접 입력을 유지한다. 이메일 복구·운영 정책·실데이터·배포 대상 인수는 별도다.
테스트 서버와 이번 tmpfs MySQL 컨테이너는 검증 후 종료·제거했다. 기존 서비스·볼륨은 변경하지 않았다.

- 2026-11-22: 완료 보관 후 문서·diff 검사를 다시 통과했고 사용자 파일 최종 해시를 대조했다.
  실행 기록의 runtime smoke PASS는 서버 가동 중 결과이며 현재 검증 서버는 중지 상태다.

- 2026-11-22: 사용자 후속 요청으로 배송지 준비·구현·검증 변경을 커밋하고 origin/feature/36에
  push한다. 위 미커밋·push 미수행 표현은 검증 완료 당시 기록이다. PR·main 반영·운영 활성화는 별도다.
