# 주문·결제 장애 분석 실제 모델 파일럿 준비

상태: 완료

## 목표

[이슈 #63](https://github.com/JooonWooKim/nanacocoa-service/issues/63)의 [구현 PRD](../../product-specs/incident-analysis-live-pilot-prd.md)와 추적 브랜치를 준비한다.

## 하지 않는 일

기능 구현, 실제 모델 호출, Slack·운영 연동과 배포는 후속 범위다.

## 현재 증거

[PR #62](https://github.com/JooonWooKim/nanacocoa-service/pull/62)의 병합과 원격 main `ec0b29e`의 [품질 검사](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45915640080) 통과를 확인했다. 모델 파일럿의 중복 이슈와 열린 PR은 없었다.

## 결정

합성 사례 3종을 전용 프로젝트에서 호출하는 파일럿으로 범위를 정했다. 모델은 허용된 후보와 근거를 선택하며 로컬 CLI가 선택을 검증한다.

## 작업 분해

- [x] 원격 기준·중복 이슈 확인과 로컬 문서 백업
- [x] 이슈·추적 브랜치 생성 및 PRD 연결
- [x] 문서·하네스 검사와 준비 기록 보관

## 진행 상황

- 2026-11-27: 기존 수정·미추적 파일 17개를 백업하고 SHA-256을 기록했다.
- 2026-11-27: 원격 main `ec0b29e` 기준으로 `origin/feature/63`을 생성했다. 로컬 `feature/63` 전환 직후 기존 파일 17개의 해시가 모두 일치했다. 함께 편집한 출시 PRD·제품 인덱스는 추가분을 대조했고, 나머지 15개 해시는 일치했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status -sb` | PASS | `feature/63`의 업스트림을 확인했다. |
| 원격 main Quality | PASS | [병합 후 품질 검사](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45915640080) 통과. |
| `./scripts/harness docs`, `git diff --check` | PASS | PRD·출시 문서 링크와 공백 검사. |
| `./scripts/harness check` | BASELINED | 기존 Java·평가와 분석·모의 Slack fixture 통과. 기존 아키텍처·보안 예외 유지. |
| 로컬 Compose 런타임·실제 모델 인수 | SKIPPED | Compose 앱이 기동되지 않았으며 실제 모델 호출은 구현 후 인수한다. |

## 위험과 롤백

공동 편집 문서는 백업과 추가분을 대조한다. 파일럿 데이터와 비용 한도를 명세에 기록한다.

## 미해결 항목

실제 모델 어댑터 구현과 승인된 전용 프로젝트의 합성 호출 인수는 후속이다.

## 완료 조건

PRD·이슈·추적 브랜치를 연결하고 문서 검사, 전체 하네스와 사용자 파일 보존 결과를 기록한다.
