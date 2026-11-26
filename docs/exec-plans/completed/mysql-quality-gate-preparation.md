# 격리 MySQL 8.4 품질 검사 준비

상태: 완료 (구현 준비)

## 목표

[이슈 #54](https://github.com/JooonWooKim/nanacocoa-service/issues/54)의 로컬·GitHub 실행과 검증 범위를 명시하고 구현 브랜치를 준비한다.

## 하지 않는 일

실행 스크립트와 workflow 구현, 실제 운영 접속, 운영 설정과 브랜치 보호 변경은 후속 범위다.

## 현재 증거

[PR #53](https://github.com/JooonWooKim/nanacocoa-service/pull/53)의 병합 커밋 `b3b8031`에서 [main 품질 검사](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45762640926)가 통과했다. 기존 MySQL 검증 절차는 [종단 검증 runbook](../../runbooks/commerce-e2e.md)에 있다.

## 결정

공용 Compose의 MySQL 버전과 기존 통합 테스트를 재사용한다. GitHub MySQL 검사는 H2 게이트와 별도 작업으로 준비한다.

## 작업 분해

- [x] 최신 main과 중복 이슈·PR 확인, 기존 파일 백업
- [x] 이슈와 추적 브랜치 생성, 구현 PRD와 출시 문서 연결
- [x] 문서·diff·전체 하네스 확인과 준비 기록 보관

## 진행 상황

- 2026-11-26: 기존 변경·미추적 파일 16개를 백업했다. 최신 main 기준 `feature/54`로 강제 옵션 없이 전환하고 16개 SHA-256 일치를 확인했다.
- 2026-11-26: 기존 파일 14개가 그대로임을 확인하고 함께 편집한 출시 PRD와 인덱스의 준비 변경만 따로 대조했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `gh issue list` | PASS | MySQL 품질 검사 중복 이슈가 없어 #54를 생성했다. |
| `git switch --track -c feature/54 origin/feature/54` | PASS | 최신 main `b3b8031`에서 원격 브랜치를 만들고 로컬이 추적한다. |
| `./scripts/harness docs` | PASS | 구현 PRD·출시 PRD·인덱스의 링크와 생성 문서 검사 통과. |
| `git diff --check` | PASS | 작업 트리 공백 검사 통과. |
| `./scripts/harness check` | BASELINED | Java 265개와 평가 63개 통과. 기존 구조·보안 예외 유지. |
| Compose runtime smoke | SKIPPED | 문서 준비 검증 시 스택을 기동하지 않았다. |
| 신규 MySQL 품질 검사 | SKIPPED | 로컬 실행 경로와 GitHub 작업은 후속 구현 단계에 검증한다. |

## 위험과 롤백

사용자 파일은 작업 트리 밖에 백업했다. 출시 PRD와 제품 인덱스의 추가분만 원본과 대조한다.

## 미해결 항목

[구현 PRD](../../product-specs/mysql-quality-gate-prd.md)의 스크립트·GitHub 작업과 실제 MySQL 실행은 이후 구현 단계에서 수행한다.

## 완료 조건

PRD와 이슈·추적 브랜치를 연결하고 문서·하네스 결과와 기존 파일 보존 증거를 남긴다.
