# 고객센터 FAQ·1:1 문의 구현

상태: 완료 · 로컬 구현과 기술 검증

## 목표

이슈 #42 고객센터의 공개 FAQ, 개인 문의, 본인 주문 선택 연결, 운영자 답변을 구현한다.

## 하지 않는 일

첨부·추가 대화·외부 알림·실데이터·운영 활성화·커밋·푸시·PR.

## 현재 증거

feature/42, main 0765330. 원본 문서 16개를 /private/tmp/support-impl-7kgy5rxp 에 백업했다.

## 결정

검토형 FAQ 파일, V15 문의 저장, 회원 잠금 기반 요청 키 재전송, 문의 행 잠금 기반 단일 답변을 쓴다.

## 작업 분해

- [x] 서버·화면·이행 구현
- [x] H2·격리 MySQL 집중
- [x] HTTP·브라우저·전체 검사와 문서 보관

## 진행 상황

- 2026-11-23: 기능 구현 후 격리 MySQL과 JS 집중 검증을 완료했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `SHA-256 백업` | PASS | 기존 변경·미추적 파일 보관 |

| `./gradlew test / test-baseline` | PASS | Java 252개, 실패·오류·skip 0개. 기존 245개 보존, 신규 7개만 추가. |
| `MySQL 집중 --rerun-tasks` | PASS | 격리 MySQL 8.4.13의 API·경합·롤백 6개 및 V14→V15 이행 1개. |
| `node --test src/test/js/*.test.cjs` | PASS | JS 9개 파일과 신규 집중 2개. 응답 유실 재시도 및 화면 복원에서 쓰기 미재실행. |
| `customer_support_e2e_http.py / commerce_e2e_http.py` | PASS | 고객센터 실제 HTTP 3개, 기존 주문·배송·취소·반품·환불·재입고 6개. |
| `Chrome` | PASS | 공개 FAQ, 비회원 안내·로그인 복귀, 합성 문의 접수·운영자 답변·회원 상세. 실제 세션 응답 형식에 맞춰 브라우저 오류를 수정하고 재검증. |
| `HARNESS_APP_PORT=18082 ./scripts/harness check` | BASELINED | exit 0. 기존 아키텍처 예외 3개·보안 예외 2개 유지. 문서·마이그레이션·평가·공백·로컬 런타임 smoke PASS. |
| `check: doctor / Compose 운영 스택` | SKIPPED | 환경 파일 비공개 내용 미조회, 실행 중인 운영 Compose 앱 없음. |
| `SHA-256 / 공동 편집 diff` | PASS | 기존 16개 중 13개 동일, 고객센터 PRD·출시 PRD·인덱스에 의도한 새 편집만 추가. |

[고객센터 PRD](../../product-specs/customer-support-prd.md)에 SUPPORT-01~06 결과를 남겼다. 로컬 증거는 /private/tmp/support-impl-7kgy5rxp 의 check.log, check-summary.txt, java-summary.json, mysql-api.xml, mysql-migration.xml, http-support.log, http-commerce.log, browser-reply.jpg에 보관했다.

## 위험과 롤백

문의 내용·응답은 개인정보처럼 취급한다. 기존 Flyway checksum을 보존한다. 사용자 파일 원본과 편집분을 대조한다.

## 미해결 항목

실제 FAQ 검토·정책 승인, 운영 담당자 지정·출시 승인 및 주문 상태 알림은 후속이다.

## 완료 조건

SUPPORT-01~06 증거, 전체 검사 분류, 제품·출시 문서 갱신과 기존 파일 대조를 마친다.
