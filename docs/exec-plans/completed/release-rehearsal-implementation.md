# 배포 후보 격리 리허설 구현

상태: 완료 · feature/67 로컬 구현·기술 검증

## 목표

현재 HEAD와 일치하는 후보 커밋으로 임시 Compose를 실행하고, 합성 데이터 보존과 복원을 확인한 뒤 사전점검에 사용할 REHEARSAL 증거를 발급한다.

## 하지 않는 일

후보의 공용 설정과 기존 상태 확인·가입·결제 계약을 재사용한다. 운영 계정과 데이터, 실제 결제·S3, 배포 대상은 별도 인수 범위다.

## 현재 증거

2026-11-27 원격 main은 PR #66 병합 커밋 `a818ec73437a3ebc31009704ee8996d46ce95ac0`다. [이슈 #67](https://github.com/JooonWooKim/nanacocoa-service/issues/67)과 `origin/feature/67`을 만들고 추적 브랜치로 전환했다. 사용자 파일 22개를 작업 트리 밖에 복사하고 전후 SHA-256이 일치함을 확인했다.

## 결정

커밋 스냅숏을 빌드하고 실행마다 별도의 Compose 프로젝트, 루프백 포트, 임시 DB를 사용한다. 합성 덤프는 같은 임시 MySQL 안의 별도 빈 스키마에 복원한다.

## 작업 분해

- [x] 기준 커밋, 중복 이슈와 사용자 파일 확인
- [x] 명령과 회귀 fixture 구현
- [x] 실제 Docker 격리 리허설 및 품질 검사
- [x] 출시 PRD·관련 문서 갱신과 사용자 파일 대조

## 진행 상황

- 2026-11-27: 후보 스냅숏과 공용 템플릿으로 이미지, MySQL 8.4.10, Redis를 기동했다. 첫 시도에서 내부망만으로는 앱 포트 게시가 되지 않아 Docker Desktop에서 `APP_PORT_INVALID`가 발생했다. 앱의 루프백 게시 브리지를 추가하고 다시 실행해 확인했다.
- 2026-11-27: 합성 기록 6개 테이블을 앱 재시작과 별도 빈 스키마 복원 뒤 대조했다. 사전점검이 `REHEARSAL` 행을 PASS로 인정했다. 기존 파일은 18개가 바이트 단위로 같고 함께 편집한 4개에서는 이전 내용을 보존했다.
- 2026-11-27: 활성 계획의 문서 검사에 필요한 항목을 보완했다. 이후 문서 검사와 전체 하네스를 통과했다. [출시 PRD](../../product-specs/commerce-launch-prd.md)와 [운영 실행 방법](../../runbooks/ci.md#배포-후보-격리-리허설)을 갱신했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | 브랜치 전환 전후 사용자 파일 22개 SHA-256 일치 |
| `./scripts/harness release-rehearsal --candidate <HEAD SHA>` | PASS | 8단계 완료, `ev-797cc9ef4b674c0b9af9979abdbfe51a` |
| `python3 -B src/test/python/test_release_rehearsal.py` | PASS | 독립 회귀 9개 |
| `./scripts/harness release-preflight --input <리허설 매니페스트>` | BLOCKED | `REHEARSAL`은 PASS. 나머지 5개 행을 생략한 입력에서 `CHECK_INCOMPLETE` 확인 |
| `./scripts/harness docs` | PASS | 문서 링크와 생성 문서 검사 |
| `git diff --check` | PASS | diff 공백 검사 |
| `./scripts/harness check` | BASELINED | Java 268개와 평가 63개 통과. 기존 아키텍처·보안 예외 유지 |
| `./scripts/harness smoke` | SKIPPED | 상시 실행 중인 Compose 앱 없음. 별도 후보 리허설에서 격리 스택 검증 및 정리 |

## 위험과 롤백

후보가 일치하지 않거나 격리를 확인하지 못하면 스택을 기동하지 않는다. 정리 범위는 실행이 만든 프로젝트와 후보 이미지 태그다.

## 미해결 항목

실제 출시 후보가 확정된 뒤 해당 SHA에 대해 실행하고 운영 인수 증거와 대조한다.

## 완료 조건

실제 Docker, 회귀 fixture, 사전점검 증거 호환성, 전체 하네스를 확인했다. `feature/67`에서 로컬 검증을 완료했다.
