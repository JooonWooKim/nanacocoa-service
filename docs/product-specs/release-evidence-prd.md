# 배포 사전점검 증거 자동 수집 PRD

| 항목 | 내용 |
| --- | --- |
| 우선순위 | P0 |
| 이슈·브랜치 | [이슈 #69](https://github.com/JooonWooKim/nanacocoa-service/issues/69) · `feature/69` |
| 구현 기준 | PR #68 병합 후 main `ca07e71ab61944ee47fa1e6a21c902d0d01de691` |
| 연계 | [운영 배포 사전점검](production-release-preflight-prd.md) · [배포 후보 격리 리허설](release-rehearsal-prd.md) |

## 작업표

| 작업 항목 | 구현 범위 | 인수 기준 |
| --- | --- | --- |
| GitHub Quality 조회 | 실행 번호로 저장소·워크플로·main push·후보 SHA와 현재 실행 시도의 작업을 읽는다 | 같은 시도의 Harness check와 MySQL 8.4 quality가 완료·성공해야 품질 통과 판단을 진행한다. 불일치 입력은 거부하고, 실행 중·취소·작업 누락은 BLOCKED로 남긴다. 명시적 검사 실패는 FAIL이다. |
| 비식별 품질 요약 | main Quality의 성공한 Harness 작업에서 SHA·실행 번호·시도·PASS 또는 BASELINED만 게시한다 | 필수 검사 누락과 허용되지 않는 상태를 거부한다. 아티팩트 부재·만료는 BLOCKED, 증거 내용 불일치는 입력 오류다. 기준선 승인은 운영 담당자가 추가한다. |
| 사전점검 입력 | 로컬 리허설 결과 전체를 읽고 기존 매니페스트에 QUALITY와 REHEARSAL을 담는다 | 리허설 상태·SHA·환경·실행 단계를 대조한다. 운영 점검은 기존 `release-preflight`에서 누락 상태로 확인된다. |

## 실행 계약

[CI 실행 방법](../runbooks/ci.md#운영-배포-사전점검)을 따른다. 입력은 전체 후보 SHA,
GitHub Actions 실행 번호, 리허설 결과 파일, 운영 대상 별칭이다. `gh`를 사용해
GitHub Actions 조회 권한으로 읽는다. 사용 중인 로컬 브랜치와 무관하게 지정 후보를 대조한다.

수집 명령은 매니페스트를 생성하면 종료 코드 0을 반환한다. 매니페스트에 FAIL 또는
BLOCKED 행이 들어갈 수 있으며, 종합 판정은 기존 사전점검 명령에서 확인한다.
형식·출처 불일치는 종료 코드 1, 조회 불가 또는 리허설 파일 누락은 2로 끝나며
두 경우 모두 매니페스트를 출력하지 않는다. 명령은 원시 응답·로그·자격 증명 대신
고정된 오류 사유를 표시한다.

main의 성공한 Harness 작업은 `release-quality-<시도>`를 30일 보관한다.
아티팩트 도입 전 실행은 성공했어도 품질 통과 행을 발급할 수 없다. 부분 재실행에서
필수 작업이 빠졌을 때 전체 실행을 다시 시작해 같은 시도의 결과를 확보한다.
기준선이 있는 실행은 BASELINED로 기록하며 승인 증거가 없으면 사전점검에서 보류한다.

## 검증

실행·작업·아티팩트의 SHA와 시도 불일치, 진행 중·취소·실패·누락, 기준선 구분,
리허설 내용 검증, 증거 비식별화와 사전점검 입력 호환성을 회귀 검사로 확인한다.
실제 원격 실행의 아티팩트 게시와 다운로드는 이 변경이 main의 Quality에서 실행된 뒤 확인한다.

2026-11-27 Python 회귀 10개와 Java 268개가 통과했다. 로컬 전체 하네스는 기존 예외로
BASELINED이고, 게시용 요약에도 이 상태가 유지됐다. 실제 main `ca07e71`의 리허설과
기존 Quality로 생성한 사전점검은 REHEARSAL PASS, QUALITY BLOCKED, 종합 BLOCKED다.
[검증 실행 기록](../exec-plans/completed/release-evidence-implementation.md)
