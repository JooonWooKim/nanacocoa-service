# 사이트 내 1:1 문의 답변 알림 준비

상태: 완료 · 기능 준비

## 목표

구현 PRD, 이슈와 추적 브랜치를 준비하고 기존 문서를 보존한다.

## 하지 않는 일

기능 구현, 인수 테스트, 커밋과 구현 푸시, PR, 배포, 운영 활성화는 후속 범위다.

## 현재 증거

원격 main `1d40634`에 PR #45가 반영됐다. 이슈 생성 전 동일 목적 이슈·열린 PR이 없었다.

## 결정

기존 사이트 알림함에 1:1 문의의 최초 확정 답변을 연결한다. 작업자에 별도 문의 활성화 설정과 UTC 시작 시각을 두고 과거 답변은 소급하지 않는다.

## 작업 분해

- [x] 원격과 기존 명세 확인 및 사용자 변경 백업
- [x] 이슈와 추적 브랜치 준비
- [x] 구현 PRD, 제품 인덱스와 출시 PRD 연결
- [x] 하네스와 문서 검사 및 최종 대조

## 진행 상황

- 2026-11-23: [이슈 #46](https://github.com/JooonWooKim/nanacocoa-service/issues/46)을 만들고 `feature/46`으로 전환했다. 전환 직후 기존 문서 15개가 모두 동일했다.
- 2026-11-23: 최종 대조에서 13개가 바이트 단위로 같았으며, 공동 편집한 출시 PRD와 인덱스는 이번 준비분만 추가됐다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git fetch / switch / SHA-256` | PASS | 원격 main 기준 추적 전환 후 기존 문서 15개 해시 일치 |
| `./scripts/harness docs / git diff --check` | PASS | 준비 문서와 diff의 공백 검사 통과 |
| `./scripts/harness check` | BASELINED | 종료 코드 0, 기존 아키텍처 예외 3개와 보안 예외 2개 유지 |
| `check: test-baseline / eval / migrations` | PASS | Java 256개 실패·오류·skip 0개, 평가 63개 통과, Flyway 이력 유지 |
| `check: doctor / smoke` | SKIPPED | 비공개 환경 파일 미조회, 실행 중인 앱 없음 |
| `SUPPORT-NOTIFY-01~06` | SKIPPED | 후속 기능 구현과 인수 검증에서 수행 |

백업과 SHA-256, 공동 편집 diff, 하네스 결과는 `/private/tmp/support-reply-prep-za_n3dov`에 보관했다.

## 위험과 롤백

브랜치 전환은 강제하지 않는다. 기존 사용자 문서와 이번 준비 추가분을 파일 백업으로 대조한다.

## 미해결 항목

[구현 PRD](../../product-specs/support-reply-notification-prd.md)에 정의한 구현·검증 및 운영 활성화가 남아 있다.

## 완료 조건

문서·이슈·브랜치 연결, 하네스 결과 분류, 기존 문서 대조를 마치고 완료 기록으로 옮긴다.
