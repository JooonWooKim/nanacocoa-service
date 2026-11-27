# 배포 후보 롤백 리허설 구현

상태: 완료 · 로컬 구현과 검증

## 목표

이전 버전의 배포 전 백업을 사용해 후보 이행과 이전 앱 복원을 격리 검증한다.

## 하지 않는 일

운영 배포 승인과 트래픽을 연 뒤 생긴 거래의 복구는 운영 담당 절차에 둔다.

## 현재 증거

[이슈 #71](https://github.com/JooonWooKim/nanacocoa-service/issues/71) · 원격 main `074f591` · [PR #70](https://github.com/JooonWooKim/nanacocoa-service/pull/70) 병합.

## 결정

이전·후보·복원에 독립 Compose 프로젝트를 사용한다. 후보와 복원 스택은 동일 배포 전 백업을 각각 빈 DB에 가져온 다음 앱을 띄운다.

## 작업 분해

- [x] 이슈·추적 브랜치 생성과 파일 보존 확인
- [x] 명령 구현 및 회귀 검증
- [x] 실제 격리 리허설, 출시 PRD와 하네스 검사

## 진행 상황

- 2026-11-27: 브랜치 전환 전후 기존 사용자 파일 22개의 SHA-256 일치를 확인했다.
- 2026-11-27: main 기준의 두 커밋과 임시 Git 저장소의 합성 V19 추가 이행을 실제 Docker에서 검증했다. 두 실행 모두 후보에서 기존 거래를 읽었고, 별도 DB로 복원한 이전 앱에서도 결제 완료 주문을 읽었다.
- 2026-11-27: [PR #70 병합 후 Quality](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45945499074)의 아티팩트 게시·다운로드와 BASELINED 보존을 확인해 출시 PRD에 반영했다. 기존 파일 18개는 바이트 단위로 같고 4개는 기존 변경을 보존하며 함께 편집했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | feature/71이 origin/feature/71을 추적 |
| `./scripts/harness release-rollback --previous <SHA> --candidate <SHA>` | PASS | 실제 main `ca07e71` → `074f591` · `ev-2f239ee236db46dd95776d3bd8251273` |
| `./scripts/harness release-rollback` · 합성 V19 | PASS | 별도 임시 저장소 `64d514d` → `3d145bf` · `ev-31fed394902d4ca2a4c401835474d501` |
| `python3 -B src/test/python/test_release_rollback.py` | PASS | 새 회귀 9개 |
| `python3 -B src/test/python/test_release_rehearsal.py` | PASS | 기존 회귀 9개 |
| `./scripts/harness check` | BASELINED | Java 268개와 평가 63개 통과. 기존 아키텍처·보안 예외 유지 |
| `./scripts/harness docs` | PASS | 제품 PRD와 실행 기록 및 운영 절차 링크 |
| `git diff --check` | PASS | diff 공백 검사 |

## 위험과 롤백

배포 전 백업과 후보에서 바뀐 DB를 분리한다. 임시 프로젝트만 정리하고 상태를 비식별화한다.

## 미해결 항목

실제 출시 후보와 이전 버전 확정, 운영 인수와 배포 승인.

## 완료 조건

실제 Docker 기동, V19 후보 이행과 복원 후 이전 앱의 주문 조회, 후보 및 복원 실패 분류와 전체 하네스를 확인했다.
