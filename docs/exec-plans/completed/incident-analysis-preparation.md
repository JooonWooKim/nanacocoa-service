# 주문·결제 AI 장애 분석 준비

상태: 완료

## 목표

[이슈 #61](https://github.com/JooonWooKim/nanacocoa-service/issues/61)의 구현 PRD와 추적 브랜치를 준비한다.

## 하지 않는 일

기능 구현, 실제 모델 호출, Slack 회신 변경, 패치 생성·적용, 자동 테스트 실행과 배포는 후속 범위다.

## 현재 증거

원격 main `3969a45`의 [PR #60](https://github.com/JooonWooKim/nanacocoa-service/pull/60)과 [병합 후 품질 검사](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45910311772) 통과를 확인했다. 열린 PR과 장애 분석 관련 이슈를 확인했으며 같은 목적의 항목은 없었다.

## 결정

기존 비식별 해결 자료를 사용하고, 모의 모델의 원인 후보와 검증된 근거를 로컬 CLI에 표시하는 범위로 정했다. 실제 모델이나 운영 환경 없이 세 가지 합성 장애로 검증한다.

## 작업 분해

- [x] 원격 기준·중복 이슈 확인과 로컬 문서 백업
- [x] 이슈·추적 브랜치 생성 및 PRD 연결
- [x] 문서·하네스 검사와 준비 기록 보관

## 진행 상황

- 2026-11-27: 기존 수정·미추적 파일 17개를 작업 트리 밖에 백업하고 SHA-256을 기록했다.
- 2026-11-27: `origin/feature/61`을 원격 main `3969a45`에서 생성했다. 로컬 `feature/61`로 전환한 직후 17개 해시가 모두 일치했다. 출시 PRD와 제품 인덱스는 추가분을 대조했고 나머지 15개 해시는 갱신 뒤에도 일치했다.
- 2026-11-27: 초기 문서 검사에서 예정 CLI를 실행 예시로 작성한 부분이 검출됐다. 구현 예정인 하위 명령의 입력 계약으로 정리한 뒤 전체 검사를 다시 실행했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | 현재 브랜치와 업스트림을 확인했다. |
| 원격 main Quality | PASS | `3969a45`의 [병합 후 품질 검사](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45910311772) 통과. |
| `./scripts/harness docs`, `git diff --check` | PASS | 명세와 출시 문서 링크, 공백 검사 통과. |
| `./scripts/harness check` | BASELINED | 기존 Java 테스트·위험 평가와 모의 Slack·해결 자료 fixture 통과. 기존 아키텍처·보안 예외 유지. |
| Compose 런타임과 AI 분석 인수 | SKIPPED | 로컬 Compose가 기동되지 않았으며 기능 인수는 후속 작업이다. |

## 위험과 롤백

공동 편집 문서는 백업을 기준으로 이번 추가분을 확인한다. 사용자 파일이 브랜치 전환과 충돌하면 강제 전환을 피하고 경로를 보고한다.

## 미해결 항목

기능 구현과 합성 장애 인수 검증을 후속으로 진행한다. 실제 모델 및 Slack 운영 연결과 배포는 별도다.

## 완료 조건

[구현 PRD](../../product-specs/incident-analysis-prd.md), 이슈와 추적 브랜치를 연결하고 검사 결과를 보관한다.
