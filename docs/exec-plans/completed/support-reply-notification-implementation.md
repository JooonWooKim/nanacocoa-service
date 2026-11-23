# 1:1 문의 답변 알림 구현

상태: 완료

## 목표

[이슈 #46](https://github.com/JooonWooKim/nanacocoa-service/issues/46)의 확정 답변 알림을 기존 회원 알림함과 운영자 재처리에 연결한다.

## 하지 않는 일

외부 채널, 이전 답변 소급, 운영 활성화와 배포는 후속 범위다.

## 현재 증거

기준 main은 `1d40634`다. 기존 수정·미추적 파일 17개를 백업하고 SHA-256을 기록했다.

## 결정

문의 답변과 확정 시각을 함께 커밋한다. 기존 작업자의 주문·문의 탐색과 처리 조건을 분리하고 알림 상태·권한 계약을 재사용한다.

## 작업 분해

- [x] 기존 문의·알림 흐름과 사용자 파일 확인
- [x] 서버·이행·화면
- [x] 집중·전체 검증과 출시 PRD 갱신

## 진행 상황

- 2026-11-23: V17 이행과 회원·운영자 알림을 구현했다. H2·격리 MySQL, 두 작업자 경합, JVM 재시작과 합성 HTTP·Chrome 흐름을 확인했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status / SHA-256` | PASS | 기존 파일을 작업 트리 밖에 백업 |

| `./gradlew test` | PASS | H2·전체 Java 259개, 실패·오류·skip 0개 |
| `./gradlew test --tests '*OrderNotification*' --tests '*SupportReplyNotification*'` | PASS | 격리 MySQL 8.4에서 집중 검증 7개. V16→V17, JPA, 두 게시 작업의 경합 및 권한 |
| `java -jar …` | PASS | 기본 비활성에서 게시 대기, 같은 MySQL을 유지하고 JVM 재시작 후 한 건 게시 |
| `node --test src/test/js/*.test.cjs` | PASS | 기존 JS와 링크·지연 응답 회귀 |
| `python3 -B src/test/python/support_reply_notification_e2e_http.py` | PASS | 합성 환경에서 답변·소유권·읽음·CSRF 확인. 기존 문의 HTTP, 모의 PG 거래 6개 및 주문 알림 11건도 통과 |
| `Chrome · 알림함 → 1:1 문의` | PASS | 문의 답변과 동일 문의 로그인 복귀 확인 |
| `HARNESS_APP_PORT=18082 ./scripts/harness check` | BASELINED | 기존 구조 예외 3개·보안 위험 2개 유지, 런타임 smoke·문서·이행·평가 63개 PASS |
| `./scripts/harness docs / git diff --check` | PASS | 완료 실행 기록 이동 후 문서·diff 검사 |

기존 수정·미추적 문서 17개 중 14개는 바이트 단위로 유지했다. 출시 PRD·제품 인덱스·기능 PRD는 백업과 공동 편집 diff를 대조했다. 증거는 `/private/tmp/support-reply-impl-46`에 보관했다.

## 위험과 롤백

작업 플래그를 내려 문의 게시를 멈출 수 있다. 배포된 Flyway 이력은 유지하고 문의 답변 저장과 게시 실패를 분리한다.

## 미해결 항목

운영 적용과 UTC 시작 시각 결정은 별도다.

## 완료 조건

PRD 인수 기준을 확인하고 증거를 보관한다.
