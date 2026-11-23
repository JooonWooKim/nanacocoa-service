# 상품 검색·카테고리 관리 구현

상태: 완료 · 로컬 구현과 기술 검증

## 목표

이슈 #40의 공개 상품 검색과 운영자 카테고리 관리를 구현하고 DISCOVERY-01~06을 검증한다.

## 하지 않는 일

FAQ·문의·알림, 실데이터와 외부 서비스, 커밋·푸시·배포.

## 현재 증거

기준 main 8b9eaae, feature/40. 기존 237개 Java 테스트. 사용자 파일 15개를 /private/tmp/discovery-implementation-ws15hbsb 에 백업했다.

## 결정

V14 nullable FK, DB 페이지 조회와 고정 카테고리 ID, 숨김과 신규 연결의 행 잠금, 기존 공개 상품 목록 응답 보존.

## 작업 분해

- [x] 조사
- [x] 구현
- [x] 검증
- [x] 리뷰와 문서화

## 진행 상황

- 2026-11-23: 기존 파일 백업 후 서버·화면·V14 구현. 격리 MySQL 집중 검사와 JavaScript를 실행했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status / SHA-256 백업` | PASS | 기존 변경·미추적 15개 보관 |

| `./gradlew test --rerun-tasks` | PASS | H2 포함 Java 245개, 실패·오류·skip 0개. 기존 237개 ID 유지, 신규 8개 등록. |
| `MySQL 집중 --rerun-tasks` | PASS | 격리 MySQL 8.4.13에서 검색·카테고리 API/동시성 7개와 V13→V14 이행 1개. |
| `node --test src/test/js/*.test.cjs` | PASS | JS 파일 8개 및 신규 검색 집중 4개. |
| `상품 탐색·장바구니 HTTP` | PASS | 검색·카테고리 2개와 모의 PG 구매/장바구니 회귀 5개. |
| `Chrome` | PASS | 공유 2페이지→상세→뒤로 가기, 관리자 로그인 복귀, 추가·숨김·복구·분류 지정, 상세→장바구니→주문서. 결제 승인은 HTTP에서 확인. |
| `HARNESS_APP_PORT=18082 ./scripts/harness check` | BASELINED | exit 0. 기존 아키텍처 예외 3개와 보안 예외 2개. 로컬 smoke·문서·이행·평가·diff PASS. |
| `check: doctor / Compose 스택` | SKIPPED | 환경 파일 내용 미조회, 실행 중인 Compose 앱 없음. |
| `SHA-256 및 공동 편집 diff 대조` | PASS | 기존 15개 중 12개 동일. PRD·출시 PRD·제품 인덱스는 이번 갱신만 확인. |

로컬 증거는 /private/tmp/discovery-implementation-ws15hbsb 의 check.log, check-summary.txt, java-summary.json, mysql-api.xml, mysql-migration.xml, http-discovery.log, http-cart.log, browser-category-saved.jpg에 보관했다. 제품별 결과는 [상품 탐색 PRD](../../product-specs/product-discovery-prd.md)에 기록했다.

## 위험과 롤백

적용된 V1~V13은 보존한다. 이번 변경만 백업과 대조하고, 운영 활성화 전에 대상 환경 이행을 확인한다.

## 미해결 항목

main 반영, 배포 환경 이행과 운영 활성화는 별도다. FAQ·문의와 주문 상태 알림은 후속이다.

## 완료 조건

DISCOVERY 증거, 전체 check, 문서·공백 검사와 기존 사용자 파일 대조를 완료한다.
