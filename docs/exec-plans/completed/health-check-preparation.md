# Actuator 상태 확인 계약 정비 준비

상태: 완료 · 기능 준비 완료, 코드 구현·검증 대기

## 목표

NC-TEST-001의 다음 구현을 위한 PRD·이슈·추적 브랜치와 증거를 준비한다.

## 하지 않는 일

코드·테스트·기준선 변경, 운영 활성화·배포·커밋·구현 push·PR 생성은 하지 않는다.

## 현재 증거

원격 main은 de5c595이며 PR #29가 병합됐다. 기존 테스트만 /api/health를 요청하고
README와 smoke는 /actuator/health를 사용한다. health 실패 1개가 기존 기준선이다.

## 결정

사용자가 Actuator 통일을 선택했다. 판매자 협의가 필요한 작업은 후순위를 유지한다.
원격 브랜치는 main 기준으로 만들고 현재 작업 트리에서 전환하며 사용자 변경을 보존한다.

## 작업 분해

- [x] 조사
- [x] 준비 문서와 원격 이슈·브랜치
- [x] 검증
- [x] 리뷰와 완료 기록

## 진행 상황

- 2026-11-20: 이 기록은 준비 완료 증거이며 기능 해소 증거가 아니다. 최초 docs 명령 셀 형식 오류를 보완하고 최종 문서/전체 검사를 재실행했다.

- 2026-11-20: 수정/미추적 파일 9개를 /private/tmp/health-preparation-05m_2yz0에 백업했다. preservation.json에 SHA-256을 기록했다.
- 2026-11-20: fetch 후 HEAD와 origin/main의 파일 차이가 없음을 확인했다. 기존 문서를 폐기하거나 stash하지 않고 전환 가능하다.
- 2026-11-20: 같은 health 이슈는 검색되지 않았다. health 검색에 노출된 PR #27은 RETURN 구현의 기존 부채 언급으로 중복 작업이 아니다.

- 2026-11-20: [이슈 #30](https://github.com/JooonWooKim/nanacocoa-service/issues/30)을 생성하고 원격 feature/30을 main de5c595에서 생성했다. 현재 작업 트리에 origin/feature/30을 추적하는 로컬 feature/30으로 전환했다.
- 2026-11-20: 전환 직후 기존 9개 파일의 SHA-256이 백업과 동일함을 확인했다. 제품 명세 인덱스·출시 PRD에 health PRD와 준비 상태를 추가했다. 이전 병합 상태 갱신·판매자 협의 후순위 문구를 보존했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | 기존 변경 9개 보존 |
| `git fetch origin main` | PASS | de5c595 기준, 중복 health 이슈 없음 |
| `git switch --track -c feature/30 origin/feature/30` | PASS | main de5c595 기준 추적 브랜치 전환, 기존 9개 SHA-256 동일 |
| `git diff --name-only origin/main -- src harness scripts` | PASS | 코드·테스트·기준선·실행 스크립트 변경 없음 |
| `./scripts/harness check` 최초 실행 | FAIL | 준비 기록의 명령 셀 형식만 docs 게이트에서 실패. 전체 187개·skip 0개·기존 health 실패 1개는 BASELINED. 문서 형식을 보완해 재실행함 |
| `./scripts/harness docs` | PASS | 준비 기록 형식 보완 후 링크/구조/생성 산출물 정합성 확인 |
| `./scripts/harness check` 최종 재실행 | BASELINED | 전체 187개·skip 0개, 기존 health 실패 fingerprint 1개만 유지. 새 회귀 없음 |
| `./gradlew test` (check 내부) | FAIL | 기존 healthApiReturnsOk HTTP 404 1개. 준비에서 테스트 계약을 바꾸지 않음 |
| `./scripts/harness eval` (check 내부) | BASELINED | 기존 OPS-001 상태 확인 부채 유지 |
| `./scripts/harness smoke` (check 내부) | SKIPPED | 실행 중인 Compose 서비스/앱 port 없음, 임의 localhost에 요청하지 않음 |
| 백업 SHA-256/본문 대조 | PASS | 기존 9개 원문 보존. 인덱스/출시 PRD에는 health 준비 연결만 추가 |

## 위험과 롤백

준비와 구현을 혼동해 실패 기준선을 미리 지우지 않는다. 브랜치 전환 전후 백업과 비교한다.
이 준비에서 추가한 문서 연결만 선택적으로 되돌릴 수 있으며 사용자 변경은 유지한다.

## 미해결 항목

준비 전체 검사는 완료했고, 상태 API 계약 구현과 구현 검증은 후속 요청이며 판매자 정책 협의는 별도 대기다.

## 완료 조건

이슈 #30·추적 브랜치 feature/30·PRD와 준비 검증 결과를 보고한다. 이슈와 브랜치만 원격에 생성했고 문서는 로컬 미커밋이다. 준비 완료와 기능 구현/검증 완료를 구분하며 실패 기준선은 유지했다.
