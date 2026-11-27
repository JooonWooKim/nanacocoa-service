# 배포 사전점검 증거 자동 수집

상태: 완료 · 로컬 구현과 검증

## 목표

GitHub Quality 실행과 REHEARSAL 증거를 후보 SHA로 대조해 사전점검 입력을 만든다.

## 하지 않는 일

운영 증거 수집과 배포 승인은 기존 운영 담당 절차를 따른다.

## 현재 증거

[이슈 #69](https://github.com/JooonWooKim/nanacocoa-service/issues/69) · main `ca07e71` · [Quality 실행](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45942515238) 성공. 기존 CI는 성공한 하네스 상태 요약을 게시하지 않는다.

## 결정

main push의 같은 실행 시도에서 끝난 두 작업과 하네스 요약을 대조한다. 로컬 리허설 응답을 읽어 기존 사전점검 입력을 생성한다.

## 작업 분해

- [x] 이슈·추적 브랜치 준비와 기존 파일 백업
- [x] 워크플로 요약·수집 구현
- [x] 회귀, 실제 조회, 문서 및 품질 검사

## 진행 상황

- 2026-11-27: 원격 main을 기준으로 브랜치를 전환한 뒤 기존 파일 22개의 해시가 일치함을 확인했다.
- 2026-11-27: 실제 후보 `ca07e71`의 Docker 리허설 8단계를 통과했다. 실제 실행 45942515238의 Quality는 두 작업이 성공했으나 새 아티팩트가 없어서 수집기는 QUALITY를 BLOCKED로 남겼다. 사전점검은 REHEARSAL을 PASS로 인정했다.
- 2026-11-27: 전체 하네스 결과로 비식별 요약을 만들어 BASELINED를 확인했다. 기존 파일 18개는 바이트 단위로 같고, 함께 편집한 4개는 리허설 병합 상태와 증거 수집 변경만 추가·갱신했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | feature/69가 origin/feature/69를 추적 |
| `python3 -B src/test/python/test_release_evidence.py` | PASS | 회귀 10개 |
| `./scripts/harness release-rehearsal --candidate <SHA>` | PASS | `ca07e71`의 8단계 및 임시 자원 정리 |
| `./scripts/harness release-evidence` | PASS | 실제 GitHub 실행을 대조하고 매니페스트 생성. 새 아티팩트가 없는 QUALITY는 BLOCKED |
| `./scripts/harness release-preflight --input <매니페스트>` | BLOCKED | REHEARSAL PASS, QUALITY와 운영 인수 증거 대기 |
| `python3 -B scripts/harness.d/release-evidence.py --publish-quality build/harness/check-summary.txt` | PASS | 실제 로컬 하네스 요약을 BASELINED로 분류 |
| `./scripts/harness check` | BASELINED | Java 268개, 평가 63개 통과. 기존 아키텍처·보안 예외 유지 |
| `./scripts/harness docs` | PASS | PRD·실행 기록·운영 절차 링크 검사 |
| `git diff --check` | PASS | diff 공백 검사 |
| `main Quality 아티팩트 게시·다운로드` | BLOCKED | 워크플로 변경이 main에서 실행될 때 확인 |

## 위험과 롤백

신뢰할 실행이나 요약이 확인되지 않으면 품질 통과 증거를 만들 수 없다. 기존 사전점검·리허설 계약을 그대로 사용할 수 있다.

## 미해결 항목

새 CI 아티팩트의 실제 생성·다운로드는 구현의 원격 실행에서 확인한다.

## 완료 조건

후보·시도 검증, 기준선 보존, 실제 사전점검 호환성을 확인한다.
