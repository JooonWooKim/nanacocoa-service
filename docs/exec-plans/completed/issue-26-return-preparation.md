# 이슈 #26 취소·반품·환불 기능 준비

상태: 완료 · 기능 준비 완료, 정책 승인·기능 구현·검증 대기

## 목표

취소·반품·환불 PRD 초안, 이슈와 추적 브랜치를 준비하고 문서·하네스 결과를 기록한다.
기능 구현이 아니라 준비 작업의 실행 기록이다.

## 하지 않는 일

- 코드·스키마·운영 정책을 구현하거나 승인하지 않는다.
- 커밋, 구현 변경 push, PR 생성·병합, 실제 PG·S3 호출을 수행하지 않는다.
- 기존 사용자 문서와 별도 worktree를 변경하지 않는다.

## 현재 증거

- 원격 main은 `1eaf066060e12ae2769cc700959852d4ed276b09`이며 PR #25 병합을 포함한다.
- 미확정 결제 운영은 PR #23으로 병합됐다. 새 RETURN 기능의 기존 이슈·열린 PR은 없었다.
- 기존 로컬 HEAD `f572186`과 원격 main의 추적 파일 차이는 없었다.
- 변경 문서 1개와 미추적 문서 3개의 SHA-256을 비교해 브랜치 전환 후 내용 보존을 확인했다.
- [이슈 #26](https://github.com/JooonWooKim/nanacocoa-service/issues/26),
  로컬 `feature/26`, 추적 원격 `origin/feature/26`을 준비했다.
- [PRD 초안](../../product-specs/cancellation-return-prd.md),
  [출시 PRD](../../product-specs/commerce-launch-prd.md)를 연결한다.

## 결정

- 사용자는 정책 미확정 상태에서 RETURN PRD 초안을 먼저 준비하는 안을 선택했다.
- 정책 승인은 구현 선행 조건이며 기간·비용을 임의 확정하지 않는다.
- 전체 취소·반품·환불과 검수 재입고를 대상으로 한다.
- 기존 결제·배송·재고 경계를 재사용하고 직접 API를 통한 승인·검수 우회를 방지한다.
- 기존 사용자 변경이 포함된 작업 트리에서 검사하되 관련 없는 문서는 고치지 않는다.

## 작업 분해

- [x] 우선순위·원격 main·이슈·PR 및 사용자 변경 조사
- [x] PRD 초안 작성과 이슈·원격 브랜치 생성
- [x] 추적 브랜치 전환과 기존 변경 보존 확인
- [x] 제품 인덱스와 출시 PRD 연결
- [x] 문서·하네스 검증 및 결과 분류
- [x] 최종 리뷰와 준비 완료 보고 자료 정리

## 진행 상황

- 2026-11-19: 계획 모드에서 RETURN 초안 준비 범위를 확인하고 실행 요청을 받았다.
- 2026-11-19: 원격 main 확인·fetch, 관련 이슈·PR 대조 후 이슈 #26과 원격 feature/26을 생성했다.
- 2026-11-19: 현재 작업 트리에서 추적 브랜치로 전환하고 기존 사용자 문서 4개 해시를 검증했다.
- 2026-11-19: PRD 초안과 문서 연결을 작성했다. 정책 승인·기능 구현·검증은 대기 중이다.
- 2026-11-19: 문서 검사는 PASS. 최초 check는 sandbox의 Gradle 캐시 잠금 접근 제한으로 실패해 BLOCKED로 분류하고 승인된 권한으로 재실행했다.
- 2026-11-19: 전체 check는 BASELINED. 152개 테스트 중 skip 0개, 기존 health 실패 1개가 정확한 fingerprint와 일치했고 새 회귀는 없었다. 실행 중인 Compose app·MySQL·Redis와 runtime health smoke도 통과했다.
- 2026-11-19: 회귀·권한 우회·중복 환불·재입고·데이터 보존 관점에서 초안을 검토했다. 코드·마이그레이션 변경 없이 준비 결과만 기록하고 completed로 이동했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git ls-remote origin refs/heads/main` | PASS | 원격 main 1eaf066 확인 |
| `git diff --name-status HEAD origin/main` | PASS | 전환 전 추적 파일 차이 없음 |
| `git switch --track origin/feature/26` | PASS | 최신 main 기반 브랜치와 upstream 확인 |
| `python3 사용자 문서 SHA-256 비교` | PASS | 기존 문서 4개 내용 보존 |
| `./scripts/harness docs` | PASS | 로컬 링크·지식 graph·생성 문서 일관성 유지 |
| `./scripts/harness check` 최초 제한 환경 실행 | BLOCKED | Gradle wrapper 캐시 잠금 접근 제한. 권한을 부여받아 재실행해 해소 |
| `./scripts/harness check` | BASELINED | 새 회귀 없음. build/harness/check-summary.txt와 logs의 게이트별 증거 |
| `check: test-baseline` | BASELINED | 152개, skip 0개. healthApiReturnsOk 실패 1개 유지, 엄격 테스트 FAIL |
| `check: architecture / security` | BASELINED | 기존 common 의존 예외 3개·보안 예외 2개 유지, 검사 통과 |
| `check: migrations / docs / harness-self-check / entropy` | PASS | 기존 계약과 Flyway checksum 유지 |
| `check: smoke` | PASS | Compose app·MySQL·Redis 정상, runtime actuator health 통과 |
| `check: eval` | BASELINED | OPS-001의 기존 health 부채 유지, 다른 위험 평가 통과 |
| `git diff --check` | PASS | whitespace 오류 없음 |

## 위험과 롤백

- 정책을 승인된 사실로 오인하지 않도록 초안·미구현 상태와 구현 착수 조건을 명시한다.
- 신청과 환불·재입고를 별도로 정의하고 기존 잠금 순서를 역전하지 않도록 초안에 기록한다.
- 취소·재입고 API의 승인·검수 우회와 재시작 뒤 중복 환불 위험을 인수 기준에 포함한다.
- 되돌릴 때 이 준비 작업의 문서 추가·변경만 선택적으로 되돌린다. 기존 사용자 파일,
  원격 이슈·브랜치는 자동 삭제하지 않는다. 이번 작업은 DB·결제 상태를 변경하지 않는다.

## 미해결 항목

- 정책 승인과 기능 구현·검증·출시 승인은 후속 작업이다.
- 기존 NC-TEST-001의 health 실패는 이번 준비에서 수정하지 않는다.
- 준비 문서는 미커밋이며 원격 main에 반영되지 않았다.
- check는 제품이 완전히 올바르다는 뜻이 아니며, 기존 NC-TEST-001 및 아키텍처·보안 부채는 유지한다.

## 완료 조건

- [x] PRD·이슈·원격 및 로컬 추적 브랜치 준비
- [x] 기존 사용자 변경 보존 확인
- [x] 검사 실행과 결과·잔여 부채 기록
- [x] 회귀·보안·데이터 손실 위험 검토 및 완료 보고 자료 정리
