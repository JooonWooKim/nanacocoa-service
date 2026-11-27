# PR 생성과 Codex 리뷰 요청 자동화

상태: 완료

## 목표

PR 생성 요청 한 번으로 GitHub PR과 `@codex review` 댓글을 확인할 수 있게 한다.

## 하지 않는 일

브랜치 push, PR 병합, GitHub Actions 설정 변경 및 기존 PR에 새 리뷰를 일괄 요청하는 작업은 범위 밖이다.

## 현재 증거

PR #64에서는 PR 생성 후 별도 댓글을 게시했으며 Codex가 요청을 접수했다. 현재 하네스에 이 두 작업을 연결하는 명령은 없다.

## 결정

하네스에서 푸시된 브랜치의 PR을 열거나 재사용한 뒤 요청 댓글 하나를 보장한다. 저장소 지침과 Codex 스킬은 이 명령으로 같은 경로를 사용한다.

## 작업 분해

- [x] 기존 저장소와 PR 생성 흐름 확인
- [x] 하네스·스킬·저장소 지침 연결
- [x] 모의 GitHub 검증과 결과 기록

## 진행 상황

- 2026-11-27: `feature/63`의 기존 작업 트리 상태를 확인했다.
- 2026-11-27: `pr-create` 하네스 명령과 저장소의 기본 PR 생성 흐름을 연결했다. `nanacocoa-pr-review` 스킬을 사용자 Codex 스킬 위치에 설치했다.
- 2026-11-27: 신규 PR과 리뷰 요청, 응답 유실과 부분 실패 복구를 모의 GitHub로 검증했다. 실제 PR #64와 댓글을 읽기 전용으로 조회해 조회 필드와 작성자 역할을 확인했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status -sb` | PASS | 기존 사용자 문서는 이번 변경 경로에 포함되지 않는다. |
| `python3 -B src/test/python/test_pr_create.py -v` | PASS | 신규 요청·기존 PR·두 번째 페이지 댓글·부분 실패·경합을 포함한 모의 GitHub 8개. |
| `python3 -B -m unittest discover -s src/test/python` | PASS | 전체 Python 63개. |
| 스킬 YAML과 하네스 연결 | PASS | 표준 스킬 검사기의 PyYAML이 설치되어 있지 않아 Ruby YAML로 이름·설명·명령 연결을 확인했다. |
| `./scripts/harness check` | BASELINED | 기존 아키텍처·보안 예외 유지. 새 자동화 fixture와 Java·위험 평가 통과. |
| `./scripts/harness docs`, `git diff --check` | PASS | 문서 링크와 공백 검사. |
| 신규 실제 PR 작성 | SKIPPED | 모의 GitHub로 쓰기를 검증했고 실제 GitHub는 기존 PR 조회만 수행했다. |

## 위험과 롤백

외부 쓰기는 사용자가 요청한 PR과 해당 리뷰 댓글로 한정한다. 응답 유실 시 GitHub 조회로 완료 여부를 확인한다.

## 미해결 항목

실제 새 PR의 동작은 다음 승인된 PR 생성 요청에서 확인한다.

## 완료 조건

새 PR, 기존 PR 재사용, 중복 댓글 방지와 부분 실패 복구를 모의 GitHub로 검증한다.
