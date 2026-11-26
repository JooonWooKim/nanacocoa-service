# 주문·결제 장애 추적 준비

상태: 완료 (구현 준비)

## 목표

[이슈 #57](https://github.com/JooonWooKim/nanacocoa-service/issues/57)의 요청 번호·로그·화면 동작과 검증 기준을 정리하고 구현 브랜치를 준비한다.

## 하지 않는 일

코드·화면 구현, 운영 로그 접근, 실제 결제, 배포·운영 활성화는 후속 범위다.

## 현재 증거

[PR #56](https://github.com/JooonWooKim/nanacocoa-service/pull/56)의 병합 커밋 `f08b213`에서 [H2·MySQL 품질 검사](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45775042831)가 통과했다. 기존 [장애 디버깅 운영 절차](../../runbooks/incident-debugging.md)와 NC-OBS-001에 요청 상관관계 후속이 남아 있다.

## 결정

초기 구현은 주문·결제 요청 번호, 오류 화면에서의 표시, 수동·자동 복구 식별과 비식별 로그에 한정한다. 기존 운영 권한과 업무 상태 전이를 유지한다.

## 작업 분해

- [x] 최신 main 및 중복 이슈·PR 확인, 기존 파일 백업
- [x] 이슈와 추적 브랜치 생성, PRD와 출시 문서 연결
- [x] 문서·diff·전체 하네스 확인과 준비 기록 보관

## 진행 상황

- 2026-11-26: 기존 변경 16개를 백업했다. 최신 main에서 만든 `feature/57`로 강제 옵션 없이 전환했고 전후 SHA-256이 모두 일치했다.
- 2026-11-26: 준비 문서를 연결한 후 기존 파일 14개의 해시 일치를 확인하고 함께 편집한 두 문서의 추가분만 별도로 대조했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `gh issue list` | PASS | 중복 이슈가 없어 #57을 만들었다. |
| `git switch --track -c feature/57 origin/feature/57` | PASS | `f08b213` 기준 원격 브랜치를 현재 작업 트리에서 추적한다. |
| `./scripts/harness docs` | PASS | PRD와 연결 문서, 생성 문서 검사 통과. |
| `git diff --check` | PASS | 작업 트리 공백 검사 통과. |
| `./scripts/harness check` | BASELINED | Java 265개와 평가 63개 통과. 기존 구조·보안 예외 유지. |
| Compose runtime smoke | SKIPPED | 문서 준비 검증 시 앱을 기동하지 않았다. |
| 장애 추적 기능 검증 | SKIPPED | 서버·화면 구현 단계에서 실행한다. |

## 위험과 롤백

기존 파일은 작업 트리 밖에 백업했다. 함께 편집한 출시 PRD와 제품 인덱스의 추가분을 원본과 구분한다.

## 미해결 항목

[구현 PRD](../../product-specs/order-payment-observability-prd.md)의 서버·화면 구현과 회귀 검증은 대기 중이다.

## 완료 조건

이슈, 추적 브랜치, PRD와 검증 기록을 연결하고 기존 작업 보존을 확인한다.
