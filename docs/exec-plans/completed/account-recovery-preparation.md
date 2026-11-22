# 이메일 비밀번호 복구 준비

상태: 완료 · 기능 준비만 완료, 구현·복구 검증 미수행

## 목표

복구 PRD·GitHub 이슈·원격/로컬 추적 브랜치·제품/출시 연결과 준비 검사 증거를 확보한다.

## 하지 않는 일

코드·API·DB 구현, 복구 기능 검증, 커밋·구현 push·PR·병합·실발송·운영 활성화·배포.

## 현재 증거

2026-11-22 원격 main f2ccaf466587822f63a8af860764f6c7f532a010, PR #37 병합 확인.
전체 이슈 이력에 계정 복구 중복 없음, 열린 PR 0개. 로컬 feature/36의 기존 수정 문서
9개·미추적 문서 3개를 /private/tmp/account-recovery-preparation-1jf_kdx2/files에 백업하고
manifest.json에 SHA-256을 기록했다. 기존 main 미반영 표시는 역사적 기록과 구분한다.
starter-mail 의존성만 있고 SMTP/발송 구현은 없다. 현재 Java 발견 기준은 218개다.

## 결정

사용자는 비밀번호 재설정만, SMTP와 모의 발송, 복구 후 모든 기존 세션 만료를 선택했다.
이메일을 잊은 경우는 후속이며 실제 공급자 선정·운영 발송은 대기한다. 준비 완료를
ACCOUNT 전체 완료나 복구 기능 검증 완료로 표시하지 않는다.

## 작업 분해

- [x] 조사·초안·기존 변경 백업
- [x] 이슈 생성·추적 브랜치 전환
- [x] PRD·제품/출시 인덱스 연결
- [x] 준비 검사·사용자 파일 보존 대조
- [x] 리뷰·completed 보관

## 진행 상황

- 2026-11-22: 원격 main·이슈·PR·브랜치와 현재 작업 트리를 조사하고 초안을 백업 디렉터리에 작성했다.

- 2026-11-22: [이슈 #38](https://github.com/JooonWooKim/nanacocoa-service/issues/38)을 생성하고 main f2ccaf4에서 origin/feature/38 생성·로컬 추적 전환을 완료했다. 원본 12개 해시가 모두 동일하다.
- 2026-11-22: 복구 PRD를 저장하고 제품 인덱스·출시 PRD의 기준 커밋·병합 상태·다음 구현 순서를 갱신했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `GitHub main/issues/pulls/branches 조회` | PASS | main f2ccaf4, 중복 없음·열린 PR 없음 |
| `기존 파일 백업·SHA-256 기록` | PASS | 기존 12개 파일·before.patch·manifest.json 보관 |
| `git fetch / switch / SHA-256 대조` | PASS | feature/38 → origin/feature/38, 전환 후 원본 12개 바이트 동일 |
| `./scripts/harness docs` | FAIL | 최초 실행에서 활성 계획 상태·증거 행 형식 불일치, 저장소 형식으로 수정 후 재검사 |
| `./scripts/harness docs` 재검사 | PASS | 활성 상태·증거 행 형식 수정 후 통과·최종 이동 후 다시 검사 |

## 위험과 롤백

사용자 문서 덮어쓰기·전환 충돌이 주요 위험이다. 강제 switch/reset/삭제하지 않으며
충돌하면 보존한 채 경로를 보고한다. 문서는 원본과 이번 추가분을 분리해 대조한다.
새 이슈·브랜치 생성은 승인된 범위이며 기존 리소스를 임의 삭제하지 않는다.

## 미해결 항목

기능 구현·RECOVERY-01~10 실행, 공급자 선정·실발송 설정·배포·활성화는 후속이다.

## 완료 조건

PRD·실제 이슈·추적 브랜치·제품/출시 연결을 확보하고 문서/diff/check 결과를 분류한다.
사용자 12개 파일의 전환 전후·최종 보존을 확인한 후 증거와 함께 completed로 이동한다.


## 최종 준비 산출물과 검증

- [복구 PRD](../../product-specs/account-recovery-prd.md), [출시 PRD](../../product-specs/commerce-launch-prd.md), [제품 인덱스](../../product-specs/index.md)
- [이슈 #38](https://github.com/JooonWooKim/nanacocoa-service/issues/38) 신규 생성, 구현 체크리스트는 미완료 유지
- 원격 main f2ccaf466587822f63a8af860764f6c7f532a010에서 origin/feature/38 생성
- 현재 작업 트리 로컬 feature/38 → origin/feature/38 추적 전환, HEAD와 원격 브랜치 동일
- 백업·검사 증거: /private/tmp/account-recovery-preparation-1jf_kdx2

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `./scripts/harness docs` | PASS | PRD·실행 계획·제품/출시 연결·생성 참조 문서 검사 |
| `git diff --check` | PASS | 공백 오류 없음 |
| `./scripts/harness check` | BASELINED | exit 0, 기존 아키텍처 예외 3개·보안 예외 2개 유지, 새 실패 없음 |
| `check: test-baseline` | PASS | 전체 Java 218개, 실패·오류·skip 0개, 발견 목록·실패 기준선 변경 없음 |
| `check: migrations / docs / self-check / entropy / eval` | PASS | 마이그레이션·문서·품질 계약 유지, 평가 통과 |
| `check: doctor / smoke` | SKIPPED | .env 내용 미조회, 실행 앱 스택 없음. 운영 인수 완료가 아님 |
| `SHA-256 / 공동 편집 diff 대조` | PASS | 전환 후 12개 바이트 동일, 최종 10개 동일·공동 편집 2개는 기존 변경을 보존한 의도한 갱신만 반영 |
| `RECOVERY-01~10` | SKIPPED | 준비만 완료, 복구 기능 구현·메일/토큰/세션·브라우저 검증은 후속 |

2026-11-22 check.log·check-summary.txt·java-summary.json을 백업 경로에 보관했다.
최초 문서 검사의 FAIL은 실행 계획 상태·증거 행 형식 불일치였으며 형식 수정 후
집중 docs와 전체 check의 docs가 PASS다. 새 기능 테스트나 실패 기준선을 추가·완화하지 않았다.
전환 비교는 preservation-after-switch.json, 최종 보존 대조는 preservation-final.json,
공동 편집 diff는 두 preparation.diff에 남겼다. 12개 원본과 이번 추가분을 분리했다.

준비 완료를 기능 완료로 표시하지 않았다. 문서는 로컬 미커밋이며 코드·공개 API·DB는
변경하지 않았다. 커밋·구현 push·PR 생성·병합·운영 설정 변경·실발송·배포는 수행하지 않았다.
이메일 복구 구현·기술 검증 후 DISCOVERY로 진행한다. 실제 공급자 선정·정책 승인·
운영 활성화·배포 대상 인수는 후속으로 유지한다.
