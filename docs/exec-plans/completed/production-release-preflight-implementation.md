# 운영 배포 사전점검 구현

상태: 완료 (로컬 구현 및 검증)

## 목표

[이슈 #52](https://github.com/JooonWooKim/nanacocoa-service/issues/52)의 여섯 가지 사전점검 항목을 명령에서 검증하고 결과를 재현할 수 있다.

## 하지 않는 일

운영 접속, 실결제, 배포, 활성화, API와 DB 변경은 범위 밖이다.

## 현재 증거

기준은 main `34f9153`이다. [준비 기록](../completed/production-release-preflight-preparation.md)에 출시 후보 근거와 브랜치를 기록했다.

## 결정

하네스 명령으로 로컬 JSON을 읽고 비식별 결과를 표준 출력과 표준 오류로 제공한다. 운영 확인은 담당자 증거를 사용한다.

## 작업 분해

- [x] 현재 브랜치·계약 확인과 기존 파일 백업
- [x] 사전점검 명령·합성 회귀 구현
- [x] 전체 하네스와 문서 갱신

## 진행 상황

- 2026-11-26: `feature/52`에서 PRD와 기존 변경 18개를 백업했다.
- 2026-11-26: 여섯 가지 증거를 대조하는 명령을 추가하고, 기준 main의 실제 품질·격리 리허설 증거로 현재 판정을 확인했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | 로컬 `feature/52`가 `origin/feature/52`를 추적한다. 기존 변경 파일 해시를 기록했다. |
| `python3 -B -m unittest discover -s src/test/python -v` | PASS | 사전점검 8개와 기존 정책 3개 통과. 기준선 승인, 종료 코드, 누락·불일치, 원본 입력 유출 차단 검증. |
| `./scripts/harness release-preflight --input <파일>` | BLOCKED | 후보 `34f9153`의 QUALITY·REHEARSAL 통과. POLICY·TARGET·PAYMENT·ROLLOUT 증거가 없어 코드 2. 입력과 출력은 비식별 임시 자료로 보관했다. |
| `./scripts/harness check` | BASELINED | 새 CLI fixture 및 Java 265개·평가 63개 통과. 기존 구조·보안 예외 유지. |
| `./scripts/harness docs` · `git diff --check` | PASS | PRD·출시 PRD·운영 문서와 링크 검사 통과. |
| Compose runtime smoke | SKIPPED | 새 명령은 로컬 파일을 읽으므로 런타임을 시작하지 않았다. 이전 격리 리허설 결과를 사용했다. |

## 위험과 롤백

합성 입력으로 유출과 오판을 검증한다. CLI와 검증 문서만 되돌릴 수 있다.

## 미해결 항목

원격 반영은 대기 중이다. 실제 운영 대상은 확인되지 않았으므로 `production-pending` 별칭으로 기술 증거만 점검했다. 판매자 정책, 운영 서버·실재고, 운영 결제, 배포 승인·롤백 증거는 출시 PRD에서 추적한다.

## 완료 조건

명령의 결과와 상태를 인수 기준대로 검증하고 출시 PRD에 구현 상태를 기록한다.
