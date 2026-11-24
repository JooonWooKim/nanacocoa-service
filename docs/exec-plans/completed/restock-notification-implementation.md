# 품절 상품 재입고 알림 구현

상태: 완료

## 목표

[이슈 #48](https://github.com/JooonWooKim/nanacocoa-service/issues/48)의 가입 회원 신청과 확정 재입고 알림을 연결한다.

## 하지 않는 일

외부 발송, 배포와 운영 활성화는 후속 범위다.

## 현재 증거

기준 main은 `d8c5aee`다. 작업 시작 전 기존 수정·미추적 파일을 백업하고 SHA-256을 기록했다.

## 결정

가용 상태 전환에 순서를 붙이고, 승인된 재입고 경로에서 확정 이벤트를 저장한다. 기존 알림 큐와 동일한 운영 계약을 사용하고 상품 잠금 안에서 게시 직전 자격을 확인한다.

## 작업 분해

- [x] PRD 및 관련 정책 확인과 기존 문서 보존
- [x] API·이행·작업자와 상품 화면 구현
- [x] 검증 및 출시 PRD 갱신

## 진행 상황

- 2026-11-24: `feature/48`에서 V18, 상품 신청·취소, 확정 재입고 게시와 상품 상세를 구현했다. 격리 MySQL과 합성 HTTP·Chrome에서 검증했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status / SHA-256` | PASS | 사용자 파일 17개를 작업 트리 밖에 백업 |

| `./gradlew test` | PASS | H2 전체 Java 264개, 실패·오류·skip 0개 |
| `./gradlew test --tests '*RestockNotification*'` | PASS | 격리 MySQL 8.4 집중 5개. V18과 중복 신청, 취소·게시 경합, 재품절 및 재시도 확인 |
| `java -jar …` | PASS | 재입고 기본 비활성에서 신청 유지, 동일 MySQL로 JVM 재시작 후 한 번 게시 |
| `node --test src/test/js/*.test.cjs` | PASS | JS 테스트 파일 11개. 로그인 복귀 조회와 계정 변경·이탈 시 지연 응답 폐기 |
| `python3 -B src/test/python/restock_notification_e2e_http.py` | PASS | 예약 해제 무게시, 검수 적합 재입고에서 본인만 알림. 모의 PG 거래 6개와 주문 알림 11건·문의 답변 알림 회귀 |
| `Chrome · 상품 상세 → 알림함` | PASS | 로그인 복귀 후 직접 신청, 운영자 수량 추가로 게시, 읽음과 같은 상품 상세 이동 |
| `HARNESS_APP_PORT=18082 ./scripts/harness check` | BASELINED | 기존 구조 예외 3개·보안 위험 2개 유지. 문서·이행·런타임 smoke·평가 63개 통과 |
| `./scripts/harness docs / git diff --check` | PASS | 완료 기록 이동 후 문서와 diff 검사 |

백업한 기존 문서 17개 중 14개는 바이트 단위로 일치했다. 출시 PRD·제품 인덱스·재입고 PRD는 공동 편집 diff를 보관했다.

## 위험과 롤백

신규 신청과 게시 작업을 중단할 수 있다. 재고 저장과 알림 처리의 트랜잭션을 분리하며 Flyway 이력을 유지한다.

## 미해결 항목

배포 인수와 운영 활성화는 별도다.

## 완료 조건

기능 인수 기준과 기존 흐름의 회귀 증거를 남기고 출시 PRD 상태를 갱신한다.
