# 이슈 #36 회원 배송지 관리 준비

상태: 완료 · 기능 준비만 완료, 구현·기능 검증 미수행

## 목표

배송지 PRD·GitHub 이슈·원격/로컬 추적 브랜치·제품/출시 연결과 준비 검사 증거를 확보한다.

## 하지 않는 일

기능 코드·스키마·API 구현, 기능 검증·커밋·구현 push·PR·병합·운영 활성화·배포·이메일 복구.

## 현재 증거

2026-11-22 원격 main 8ae5def, PR #35 병합 확인. 열린 이슈·PR 없음, 전체 이슈 이력에 배송지 중복 없음.
Member.address는 단일 문자열이고 checkout은 이름만 자동 입력한다. 현재 Java 발견 기준은 205개다.
기존 수정 문서 8개·미추적 3개를 /private/tmp/shipping-address-preparation-03kkqjfo/files에 복사하고
preservation.json에 SHA-256을 기록했다. 전환 후 모든 해시가 동일하다.

## 결정

사용자는 ACCOUNT 중 배송지 먼저·기본 배송지 자동 입력을 선택했다.
프로필 주소는 독립 유지하며 자동 이행하지 않는다. 미편집 신규 폼에만 자동 입력하고
이메일 계정 복구는 후속으로 남긴다. 준비 완료는 ACCOUNT 전체나 기능 구현 완료가 아니다.

## 작업 분해

- [x] 조사·초안·원본 백업
- [x] 이슈 생성·추적 브랜치 전환
- [x] PRD·제품/출시 인덱스 연결
- [x] 준비 검사·최종 파일 보존 대조
- [x] 리뷰·completed 기록 보관

## 진행 상황

- 2026-11-22: 중복 없음 확인 후 [이슈 #36](https://github.com/JooonWooKim/nanacocoa-service/issues/36)을 생성하고
  main 8ae5def에서 origin/feature/36 생성·로컬 추적 전환을 완료했다. 기존 11개 파일은 전환 후 동일하다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `GitHub main/issues/pulls 조회` | PASS | main 8ae5def, 이슈 #36 생성 전 열린 이슈/PR 및 배송지 중복 없음 |
| `git fetch / switch / SHA-256 대조` | PASS | feature/36 → origin/feature/36, 원본 11개 보존 |
| `./scripts/harness docs` | PASS | 생성 문서·링크·실행 계획 형식 일치 |
| `git diff --check` | PASS | 공백 오류 없음 |
| `./scripts/harness check` | BASELINED | exit 0, 기존 구조 예외 3개·보안 위험 2개 유지, 새 회귀 없음 |
| `check: test-baseline` | PASS | 전체 Java 205개, 실패·오류·skip 0개 |
| `check: migrations / eval / self-check / entropy` | PASS | 기존 스키마 이력·평가·하네스 계약 유지 |
| `check: doctor / smoke` | SKIPPED | 비밀 환경 내용 미조회·실행 스택 없음, 운영 인수나 기능 검증 통과가 아님 |
| `SHA-256 / 공동 편집 diff 대조` | PASS | 전환 전후 11개 동일, 최종 9개 동일·공동 편집 2개는 의도한 변경만 반영 |

## 위험과 롤백

기존 사용자 문서 덮어쓰기·브랜치 충돌이 주요 위험이다. 강제 전환/삭제/reset을 하지 않는다.
이번 추가분만 백업과 비교해 분리하며 공동 편집 문서는 원본과 새 추가분을 별도로 대조한다.

## 미해결 항목

기능 구현과 ADDRESS-01~08 검증은 다음 요청에서 수행한다. 계정 복구·운영 승인·배포·활성화는 별도다.

## 완료 조건

PRD·이슈·추적 브랜치·인덱스 연결, 문서·diff·전체 check 분류와 기존 파일 보존 확인 후 completed로 이동한다.


- 2026-11-22: PRD·제품 인덱스·출시 PRD를 연결하고 ACCOUNT를 배송지 관리와 계정 복구로
  구분했다. 배송지는 준비 완료/구현 대기이며 계정 복구는 이메일 발송 수단 결정 후속이다.
- 2026-11-22: 전체 check exit 0/BASELINED와 Java 205개 실패·오류·skip 0개를 확인했다.
  문서·diff 검사는 PASS, 실행 스택이 없어 smoke는 SKIPPED다. ADDRESS 신규 시나리오는
  구현 대기·미실행이며 이번 결과를 배송지 기능 검증 통과로 표시하지 않았다.
- 2026-11-22: 최초 11개 파일의 전환 후 해시 일치, 최종 9개 바이트 동일을 확인했다.
  출시 PRD와 제품 인덱스는 의도한 기준일·ACCOUNT 분리·연결 추가만 있음을 원본과 대조했다.
  결과는 백업 디렉터리의 preservation-after.json과 두 preparation.diff에 보관했다.

## 준비 산출물과 잔여 작업

- [배송지 PRD](../../product-specs/shipping-address-prd.md), [출시 PRD](../../product-specs/commerce-launch-prd.md)
- [이슈 #36](https://github.com/JooonWooKim/nanacocoa-service/issues/36), 기능 완료 체크리스트는 미완료 유지
- 로컬 feature/36, 추적 origin/feature/36, 시작 커밋 8ae5defcbff84a1da7f2453ad3365729a975cb28
- 증거: /private/tmp/shipping-address-preparation-03kkqjfo/check.log·check-summary.txt·preservation.json·preservation-after.json
- 문서만 로컬 미커밋. 코드·API·DB 미변경, 커밋·구현 push·PR·병합·운영 활성화 미수행
- ADDRESS-01~08 구현·검증, ACCOUNT 이메일 복구, 이후 DISCOVERY 및 운영 승인은 후속 작업
