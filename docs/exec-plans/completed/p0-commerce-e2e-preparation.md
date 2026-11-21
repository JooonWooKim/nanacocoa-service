# P0 구매·환불·재입고 종단 검증 준비

상태: 완료 · PRD·이슈·추적 브랜치 준비 완료, 실제 종단 검증 대기

## 목표

P0 종단 검증의 PRD·GitHub 이슈·현재 작업 트리의 추적 브랜치를 준비한다.
준비 완료와 실제 종단 검증 완료를 구분한다.

## 하지 않는 일

기능 코드·테스트·실패 기준선 변경, 실제 종단 검증, 정책 승인·운영 활성화,
배포·커밋·구현 push·PR 생성·병합은 수행하지 않는다.

## 현재 증거

2026-11-21 원격 main afc8cc293975d5cdfd971a0822ca810267aa5e2d를 API와 fetch로 확인했다.
PR #31·#27·#23은 병합됐고 같은 목적의 이슈는 없다. 기존 수정 문서 7개와 미추적 문서
3개를 /private/tmp/p0-e2e-preparation-iv3z5ur1에 백업하고 preservation.json에 SHA-256을 기록했다.
HEAD와 origin/main의 파일 차이가 없어 기존 변경을 유지한 브랜치 전환이 가능하다.

## 결정

사용자가 P0 종단 검증을 선택하고 준비 계획 실행을 요청했다. 판매자 협의는 후순위로
유지하며 합성 정책·가상 데이터·모의 PG를 사용한다. 기존 RETURN·PAY 명세를 참조하는
검증 PRD를 추가하고 기존 이슈 #26·#22와 연결한다. 새 API·스키마는 추가하지 않는다.

## 작업 분해

- [x] 원격 main·이슈·PR 조사와 기존 사용자 파일 백업
- [x] PRD 초안·이슈·추적 브랜치 준비
- [x] 문서·하네스 검사와 결과 분류
- [x] 사용자 변경 보존 확인·리뷰·완료 기록

## 진행 상황

- 2026-11-21: 준비 검사에서 docs·공백·코드/테스트 무변경을 확인했다. 최초 전체 check는 Gradle 사용자 캐시 잠금 파일 접근 제한으로 FAIL(환경 원인)이었고 테스트 실행 전 중단됐다. 승인 경로의 동일 check는 BASELINED로 완료했으며 Java 191개 실패·오류·skip 0개와 eval PASS를 확인했다. 아키텍처 예외 3개·보안 위험 2개는 기존 기준선 그대로다. 실행 중인 프로젝트 Compose 앱이 없어 smoke는 SKIPPED다.
- 2026-11-21: 회귀·보안·데이터 손실 관점에서 새 API/스키마·외부 제공자 호출·운영 활성화가 없고 증거 비식별 원칙과 실제 검증 대기 상태가 유지됨을 검토했다. 기존 8개 파일은 바이트 동일하며 함께 편집한 출시 PRD·제품 인덱스 2개는 기존 모든 행을 유지하고 추가만 했다. 준비 기록을 completed로 이동한다. 최종 기록 반영 후 문서·공백·보존 검사를 다시 확인한다.

- 2026-11-21: [이슈 #32](https://github.com/JooonWooKim/nanacocoa-service/issues/32)를 생성하고 원격 main afc8cc2에서 origin/feature/32를 생성했다. 현재 작업 트리를 이를 추적하는 feature/32로 전환한 직후 기존 10개 파일의 해시가 백업과 모두 같음을 확인했다. PRD 초안을 저장하고 제품 인덱스·출시 PRD에 준비 상태만 추가했다. 실제 종단 검증은 실행하지 않았다.

- 2026-11-21: 원격 이슈 전체와 PR 병합 상태를 확인하고 기존 10개 파일을 백업했다. 최초 fetch는 sandbox의 .git 쓰기 제한으로 실패했고 승인 경로의 재실행은 통과했다. 원격 main과 현재 HEAD의 파일 내용은 동일하다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | feature/30, 기존 사용자 변경 10개 확인 |
| `git fetch origin main` | PASS | 승인 경로로 최신 원격 main 확인 |
| `git diff --exit-code HEAD origin/main` | PASS | 파일 차이 없음 |
| `git switch --track -c feature/32 origin/feature/32` | PASS | main afc8cc2 기준 추적 브랜치 전환, 기존 10개 파일 SHA-256 동일 |
| `./scripts/harness docs` | PASS | PRD·활성 계획·제품 인덱스·출시 연결, 생성 문서 정합성 확인 |
| `git diff --check` | PASS | 공백 오류 없음 |
| `./scripts/harness check` 최초 실행 | FAIL | sandbox의 Gradle cache 잠금 접근 제한, 테스트 실행 전 중단. 제품 회귀로 분류하지 않음 |
| `./scripts/harness check` 승인 경로 재실행 | BASELINED | 전체 191개 실패·오류·skip 0개, docs·migrations·self-check·entropy·eval PASS, 기존 구조/보안 예외 유지 |
| `./gradlew test` (check 내부) | PASS | 전체 191개 실패·오류·skip 0개 |
| `./scripts/harness smoke` (check 내부) | SKIPPED | 실행 중인 Nanacocoa Compose 서비스와 앱 port 없음, 임의 localhost에 요청하지 않음 |
| `git diff --exit-code origin/main -- src scripts harness build.gradle settings.gradle` | PASS | 코드·테스트·기준선·빌드·실행 스크립트 변경 없음 |
| `사용자 파일 백업/현재 내용 대조` | PASS | 8개 SHA-256 동일, 공동 편집 2개는 기존 행 삭제·변경 없이 추가만 수행 |

## 위험과 롤백

기존 변경을 폐기하거나 강제 전환하지 않는다. 전환 전후 백업 해시를 비교한다.
이 작업이 추가한 문서와 연결만 선택적으로 되돌릴 수 있고 사용자 변경은 유지한다.
종단 검증 증거 없이 기존 BLOCKED 항목이나 정책 승인을 완료 처리하지 않는다.

## 미해결 항목

준비 범위의 미해결 항목은 없다. 이슈 #32의 실제 브라우저·격리 MySQL 종단 검증과
기존 RETURN 후반 BLOCKED 해소는 후속 작업이다. 운영 승인·활성화도 별도로 남아 있다.

## 완료 조건

PRD는 docs/product-specs/p0-commerce-e2e-prd.md이며 이슈 #32와 연결했다.
로컬 feature/32는 origin/feature/32를 추적하고 둘 다 main afc8cc2에서 시작한다.
준비 검사와 보존 확인을 완료했으며 기록은 completed에 보관한다.
문서는 로컬 미커밋이다. 실제 종단 검증·운영 승인이나 원격 문서 반영 완료로 취급하지 않는다.
