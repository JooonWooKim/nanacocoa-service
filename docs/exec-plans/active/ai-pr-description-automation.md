# AI 기반 PR Description 자동 생성

상태: 활성

## 목표

PR 생성과 커밋 추가 시 GitHub API의 변경 파일과 커밋을 기반으로 한국어
설명을 생성하고, 작성자가 쓴 본문을 보존하면서 관리 마커 사이만 갱신합니다.
OpenAI 호출이 실패해도 결정론적 요약과 자원 목록은 계속 제공해야 합니다.

## 하지 않는 일

- 애플리케이션 HTTP API와 Controller-Service-Repository 흐름을 변경하지 않습니다.
- PR head를 checkout하거나 PR에서 제공한 코드를 실행하지 않습니다.
- CI 통과 여부나 병합 가능 여부를 판단하거나 PR을 자동 병합하지 않습니다.
- 실제 OpenAI 또는 Toss Payments API를 테스트에서 호출하지 않습니다.

## 현재 증거

- remote는 `origin=https://github.com/JooonWooKim/nanacocoa-service.git`입니다.
- 기본 브랜치는 `main`이고 현재 저장소에 `.github` workflow가 없습니다.
- `docs/runbooks/ci.md`와 `docs/runbooks/automation.md`는 remote가 없던 상태를
  전제로 작성되어 갱신이 필요합니다.
- 현재 feature 변경에는 Java/API, 정적 페이지, 테스트, 문서가 함께 포함되어
  파일 경로 기반 분류가 필요합니다.

## 결정

- `pull_request_target`에서는 기본 브랜치의 신뢰된 스크립트만 checkout하고
  `contents: read`, `pull-requests: write`만 허용합니다.
- PR 파일과 커밋은 GitHub REST API로 읽으며 shell에 삽입하지 않습니다.
- Node 표준 라이브러리만 사용하고 OpenAI Responses API에는
  `gpt-5.6-luna`, 명시적 낮은 추론, Structured Outputs를 사용합니다.
- 민감 경로와 바이너리를 제외하고 파일당 8 KiB, 전체 60 KiB로 제한한
  마스킹 patch만 외부 API로 전송합니다.
- 작성자 본문은 `pr-auto:start`와 `pr-auto:end` 마커 외부에서 보존합니다.

## 작업 분해

- [x] 조사
- [x] 구현
- [x] 검증
- [x] 리뷰와 문서화

## 진행 상황

- 2026-08-22: `origin/main`에서 `codex/pr-description-automation` 전용
  worktree를 생성하고 저장소·GitHub·OpenAI 계약을 확인했습니다.
- 2026-08-22: workflow, Node 생성기, fixture와 15개 테스트를 구현하고
  CI·자동화 runbook을 실제 운영 계약에 맞게 갱신했습니다.
- 2026-08-22: Node 테스트, 문서, 아키텍처, 마이그레이션 검사는 통과했고
  테스트 9개 실패는 기존 fingerprint와 일치해 BASELINED로 분류했습니다.
- 2026-08-22: 전체 check의 나머지 3개 실패는 `origin/main`부터 없는
  `.env.example`, `compose.yaml`, `application.yml`과 남아 있는 보안 기준선의
  불일치로 확인했습니다. 관련 없는 기준선 수락이나 환경 파일 복원은 하지
  않았습니다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `node .github/scripts/pr-description.test.mjs` | PASS | 세부 테스트 15개 통과 |
| `node --test .github/scripts/pr-description.test.mjs` | PASS | Node 18 test runner 격리 실행 통과 |
| `./scripts/harness docs` | PASS | 지식 graph와 생성 산출물 일관성 유지 |
| `./scripts/harness test-baseline` | BASELINED | 테스트 63개, 기존 fingerprint 실패 9개 유지 |
| `./scripts/harness check` | FAIL | 환경 템플릿·보안 기준선 drift·Compose smoke 3개 기존 실패 |
| `git diff --check` | PASS | whitespace 오류 없음 |
| Ruby YAML parse | PASS | workflow YAML 구문 정상 |

## 위험과 롤백

- `pull_request_target`의 쓰기 토큰·secret 노출을 막기 위해 PR head를 checkout,
  import, build 또는 실행하지 않습니다.
- diff의 프롬프트 주입은 도구 없는 구조화 출력, 출력 정제, 결정론적 자원 목록으로
  경계를 제한합니다.
- 잘못된 마커나 GitHub API 오류에서는 본문을 변경하지 않습니다.
- 롤백은 workflow와 전용 스크립트를 제거하고 두 runbook을 이전 상태로 되돌립니다.

## 미해결 항목

- `main` 병합 후 repository secret 등록과 실제 `workflow_dispatch` 검증은
  저장소 관리자가 수행해야 합니다.
- 저장소의 `.env.example`, `compose.yaml`, `application.yml` 부재와 보안
  예외 기준선 불일치를 별도 범위에서 해결하거나 명시적으로 수락해야 전체
  `./scripts/harness check`가 통과할 수 있습니다.

## 완료 조건

- [x] 스크립트 단위·통합 테스트와 `./scripts/harness check` 결과를 분류했습니다.
- [x] 문서가 실제 secret, trigger, 데이터 전송, 폴백 동작과 일치합니다.
- [x] 보안·데이터 손실·회귀 관점의 최종 검토를 기록했습니다.
- [ ] 저장소 기존 환경·보안 기준선 drift를 해결해 전체 check를 통과합니다.
- [ ] `main` 병합 후 실제 GitHub/OpenAI 경로를 수동 실행으로 확인합니다.
