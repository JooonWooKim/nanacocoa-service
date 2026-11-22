# P1 회원 장바구니 기능 준비

상태: 완료 · 기능 준비만 완료, 구현·기능 검증 미수행

## 목표

회원 장바구니·복수 상품 주문 PRD, GitHub 이슈, 원격/로컬 추적 브랜치를 준비한다.

## 하지 않는 일

코드·스키마 구현, 커밋·구현 push·PR 생성·병합, 운영 정책 승인·활성화·배포.

## 현재 증거

2026-11-22 GitHub main은 `3ed0c59bf49c47426e0d92805adfa2e8c3fac114`다.
이슈 33개 범위 확인에 CART 중복 없음, 열린 이슈·PR 없음, 원격은 main만 존재한다.
현재 feature/32 HEAD와 origin/main의 커밋 트리는 동일하다.
사용자 수정 7개·미추적 3개를 `/private/tmp/cart-preparation-hzzv31y7`에 복사하고
`preservation.json`에 SHA-256을 기록했다. 초안도 같은 임시 경로에 작성했다.

## 결정

CART → ACCOUNT → DISCOVERY 순서를 따른다. 사용자는 승인 확정 후 장바구니 정리를
선택했다. 기존 바로 구매·주문 멱등성·결제 복구를 유지하며 판매자 협의는 후순위다.

## 작업 분해

- [x] 조사·초안·사용자 파일 백업
- [x] 이슈 생성 및 추적 브랜치 전환
- [x] PRD·제품 인덱스·출시 PRD 연결
- [x] 준비 검사·파일 보존 확인
- [x] 리뷰와 완료 기록 보관

## 진행 상황

- 2026-11-22: 사용자 승인 계획으로 준비 착수. 원격 main과 관련 이슈·PR 대조 완료.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `GitHub branches/issues/pulls 조회` | PASS | main 3ed0c59, 중복 이슈·열린 PR 없음 |
| `git status·트리 비교·SHA-256 백업` | PASS | 수정 7개·미추적 3개 보관 |
| `./scripts/harness docs` 초회 | FAIL | 활성 상태·날짜 bullet·명령 backtick 형식 누락. 템플릿 검사 형식 수정 후 재검사 PASS |
| `./scripts/harness docs` | PASS | 생성 문서·링크·실행 기록 계약 일치 |
| `git diff --check` | PASS | 공백 오류 없음 |
| `./scripts/harness check` | BASELINED | exit 0, 기존 구조 예외 3개·보안 위험 2개 유지, 새 회귀 없음 |
| `check: test-baseline` | PASS | Java 192개, 실패·오류·skip 0개, 실패 기준선 없음 |
| `check: migrations / eval / self-check / entropy` | PASS | 기존 데이터 이력·평가·하네스 계약 유지 |
| `check: doctor / smoke` | SKIPPED | 환경 파일 내용 미조회·포트 소유자 미판정, 실행 Compose 스택 없음. 운영 인수 통과가 아님 |
| `사용자 파일 보존 대조` | PASS | 전환 직후 10개 동일, 최종 8개 동일·공동 편집 2개는 CART 추가분만 반영 |

## 위험과 롤백

사용자 파일을 덮어쓰지 않는다. 브랜치 전환 충돌 시 강제 전환 없이 경로를 보고한다.
되돌릴 때 이번 추가분만 제거하고 백업과 비교한다. 기존 변경에 reset/checkout을 사용하지 않는다.

## 미해결 항목

기능 구현·기능 검증은 다음 요청에서 수행한다. 운영 승인·활성화는 별도다.

## 완료 조건

PRD·이슈·추적 브랜치·연결 문서 확보, 파일 보존 대조와 검사 결과 분류 후 completed로 이동한다.

2026-11-22 [이슈 #34](https://github.com/JooonWooKim/nanacocoa-service/issues/34) 생성,
main 3ed0c59에서 원격 feature/34 생성 후 로컬 추적 브랜치로 전환했다.
전환 직후 기존 10개 파일 해시 모두 동일함을 확인했다. PRD·제품 인덱스·출시 PRD를 연결했다.

- 2026-11-22: 문서 검사 초회에서 실행 계획 표현 형식 3개 오류를 확인하고 수정했다.
  재검사 PASS, 전체 check exit 0/BASELINED, Java 192개 실패·오류·skip 0개를 확인했다.
  아키텍처·보안은 기존 예외만 유지하며 신규 실패 없음. smoke는 SKIPPED다.
- 2026-11-22: 기존 8개 파일은 바이트 동일하며 출시 PRD와 제품 인덱스는
  백업 대비 CART 행 갱신·연결 추가분만 있음을 확인했다. 비교 결과는
  `/private/tmp/cart-preparation-hzzv31y7/preservation-after.json`, 공동 편집 diff도 같은
  백업 디렉터리에 보관했다. 모든 문서는 로컬 미커밋이고 구현 push·PR은 만들지 않았다.

## 준비 산출물

- [구현 PRD](../../product-specs/cart-prd.md), [출시 PRD](../../product-specs/commerce-launch-prd.md)
- [이슈 #34](https://github.com/JooonWooKim/nanacocoa-service/issues/34) · 기능 완료 체크리스트는 미완료 유지
- 로컬 `feature/34`, 추적 `origin/feature/34`, 시작 SHA `3ed0c59bf49c47426e0d92805adfa2e8c3fac114`
- 검사 로그 `/private/tmp/cart-preparation-hzzv31y7/check.log` 및 `build/harness/check-summary.txt`
- CART-01~10은 구현 대기로 미실행. 운영 승인·활성화·배포 및 ACCOUNT/DISCOVERY는 잔여 작업이다.
