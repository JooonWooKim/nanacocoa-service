# 사이트 내 주문 상태 알림 준비

상태: 완료 · 기능 준비

## 목표

주문 상태 알림 PRD·이슈·추적 브랜치와 준비 검사 증거를 확보한다.

## 하지 않는 일

기능 코드·스키마·API 구현, 인수 검증, 커밋·구현 푸시·PR·배포·운영 활성화.

## 현재 증거

main 99931a4에 PR #43 반영, 관련 중복 이슈·열린 PR 없음. 기존 수정 문서 12개와 미추적 문서 3개를 SHA-256으로 기록했다.

## 결정

사이트 내 알림, 결제 완료·정상 출고·확정 환불, 활성화 이후만 처리한다. 거래 확정과 비동기 처리를 분리한다.

## 작업 분해

- [x] 조회·초안·백업
- [x] 이슈·추적 브랜치
- [x] PRD·제품 인덱스·출시 PRD
- [x] 검사·증거 보관

## 진행 상황

- 2026-11-23: [이슈 #44](https://github.com/JooonWooKim/nanacocoa-service/issues/44) 생성 후 feature/44 → origin/feature/44 전환. 최초 15개 파일의 전환 전후 해시 일치.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git fetch / switch / SHA-256` | PASS | 15개 원본 /private/tmp/notification-prep-2wlbi7_z 에 보관. 전환 전후 내용 일치. |
| `./scripts/harness docs / git diff --check` | PASS | 문서와 공백 검사 통과 |
| `./scripts/harness check` | BASELINED | exit 0. 기존 아키텍처 예외 3개·보안 예외 2개 유지, 신규 회귀 없음. |
| `check: Java / migrations / eval / self-check / entropy` | PASS | Java 252개, 실패·오류·skip 0개. |
| `check: doctor / smoke` | SKIPPED | 비공개 환경 파일 미조회, 실행 중인 Compose 앱 없음. |
| `SHA-256 / 공동 편집 diff` | PASS | 15개 중 13개 바이트 동일, 출시 PRD와 인덱스는 새 준비 부분만 변경. |
| `NOTIFY-01~06` | SKIPPED | 기능 구현 후 검증. |

[주문 상태 알림 PRD](../../product-specs/order-notification-prd.md)를 연결했다. 검사 결과와 해시·공동 편집 diff는 백업 위치의 check.log, check-summary.txt, java-summary.json, after-switch.json, preservation-final.json에 보관했다.

## 위험과 롤백

파일이 충돌하면 강제 전환하지 않는다. 공동 편집 문서는 백업과 이번 추가분을 대조한다. 실제 운영 알림은 활성화하지 않는다.

## 미해결 항목

NOTIFY-01~06 구현·인수, 배포 시각 결정과 운영 활성화는 별도다.

## 완료 조건

PRD·이슈·브랜치를 연결하고 검사 결과 및 사용자 파일 대조를 completed에 기록한다.
