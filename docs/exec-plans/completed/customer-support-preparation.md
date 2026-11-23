# 고객센터 FAQ·1:1 문의 준비

상태: 완료 · 기능 준비

## 목표

FAQ·1:1 문의 PRD, GitHub 이슈, 로컬·원격 추적 브랜치와 준비 검증 증거를 확보한다.

## 하지 않는 일

기능 코드·API·DB 구현, 인수 검증, 커밋·구현 푸시·PR 생성·배포·활성화.

## 현재 증거

main 0765330에 PR #41이 반영됐다. 기존 FAQ와 문의 링크는 페이지 내 안내 영역으로 이동한다. 중복 이슈와 열린 PR은 없었다. 기존 수정 11개·미추적 3개를 작업 트리 밖에 백업했다.

## 결정

FAQ는 검토된 저장소 파일과 승인 정책을 사용한다. 회원 텍스트 문의는 본인 주문 한 건을 선택 연결할 수 있다. 운영자 답변은 한 건이며 회원은 사이트에서 확인한다.

## 작업 분해

- [x] 상태 조회·초안·백업
- [x] 이슈·추적 브랜치 전환
- [x] PRD·출시 PRD·제품 인덱스 연결
- [x] 검사와 증거 보관

## 진행 상황

- 2026-11-23: [이슈 #42](https://github.com/JooonWooKim/nanacocoa-service/issues/42) 생성, feature/42 → origin/feature/42 전환. 사용자 파일 14개 전환 전후 해시 일치.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git fetch / git switch / SHA-256` | PASS | 14개 원본과 해시는 /private/tmp/support-prep-upyw6hcb 에 보관했다. |
| `./scripts/harness docs / git diff --check` | PASS | 문서·공백 검사 통과 |
| `./scripts/harness check` | BASELINED | exit 0. 기존 아키텍처 예외 3개·보안 예외 2개, 신규 회귀 없음. |
| `check: Java / migrations / eval / self-check / entropy` | PASS | Java 245개, 실패·오류·skip 0개. 이행 이력과 기준선 유지. |
| `check: doctor / smoke` | SKIPPED | 비공개 환경 파일 내용 미조회, 실행 중인 Compose 앱 없음. |
| `최종 SHA-256 / 편집 diff 대조` | PASS | 14개 중 12개 바이트 동일. 출시 PRD·인덱스에 추가된 고객센터 범위만 확인. |
| `SUPPORT-01~06` | SKIPPED | 기능 구현 후 실행. |

[고객센터 PRD](../../product-specs/customer-support-prd.md)를 연결했다. 로컬 증거는 백업 디렉터리의 check.log, check-summary.txt, java-summary.json, after-switch.json, preservation-final.json과 공동 편집 diff에 보관했다.

## 위험과 롤백

전환 충돌 시 강제 전환하지 않고 원본과 경로를 보존한다. 공동 편집 문서는 백업 대비 추가분을 대조한다. FAQ에 미승인 운영 정책을 게시하지 않는다.

## 미해결 항목

SUPPORT-01~06 구현·인수, 출시 FAQ 최종 검토와 실제 정책 승인·배포·운영 활성화, 후속 주문 상태 알림.

## 완료 조건

이슈·추적 브랜치·PRD 연결 확인, 준비 검사 분류, 사용자 파일 보존 대조 후 completed에 보관한다.
