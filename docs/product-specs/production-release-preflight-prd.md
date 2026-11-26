# 운영 배포 사전점검 PRD

| 항목 | 내용 |
| --- | --- |
| 상태 | 로컬 구현 및 합성 회귀 검증 · main 반영 대기 |
| 우선순위 | P0 OPS-RELEASE-PREFLIGHT |
| 기준 | 2026-11-26 원격 main `34f9153cef1fa86b11ab4bcdfafa1497188f7c99` |
| 추적 | [이슈 #52](https://github.com/JooonWooKim/nanacocoa-service/issues/52) · `feature/52` → `origin/feature/52` |
| 대상 | 후보 배포를 검토하는 개발자와 운영 담당자 |

## 목표

같은 배포 후보의 기술 검증과 실제 운영 대상의 인수 증거를 모아 어떤 조건이 충족됐는지 표시한다. 11월 25일의 격리 점검에서 구매 기능 6/6과 배포 준비 2/6을 확인했다. 이 점검은 배포 판단에 필요한 증거를 정리하며 증거의 진위와 최종 판단은 담당자가 확인한다. 배포·공개 후 결과는 출시 기록에서 추적한다.

## 입력과 결과

`./scripts/harness release-preflight --input <파일>`은 Python 3로 로컬 JSON 증거 한 벌을 읽고 사람용 요약을 표준 오류에, 기계 판독 JSON을 표준 출력에 기록한다. 최대 64 KiB의 UTF-8 일반 파일을 사용한다. 최상위 필드는 `schemaVersion`(정수 1), `candidateCommit`(소문자 16진수 40자리 Git SHA), `targetEnvironment`(운영 주소와 구분되는 별칭), `checks`(점검 배열)다.

각 점검의 키는 `id`, `status`, `candidateCommit`, `environment`, `role`, `verifiedAt`, `evidenceId`다. QUALITY는 환경 별칭 `ci`, REHEARSAL은 `isolated`, 운영 점검은 지정된 대상 별칭을 사용한다. 별칭과 역할은 영문 소문자로 시작하는 최대 32자의 영문 소문자·숫자 단어를 하이픈으로 이은 이름이다. 대상 별칭으로 `ci`와 `isolated`를 지정할 수 없다. 확인 시각은 시간대가 있는 RFC 3339 형식이다.

`BASELINED` 항목에는 별도 `baselineApproval` 객체로 승인자의 `role`, `verifiedAt`, `evidenceId`를 기록한다. 증거 식별자는 `ev-`와 소문자 16진수 16~64자리로 구성한다. 비식별 증거 보관자만 그 식별자를 원본과 대조할 수 있게 관리한다.

합성 입력 예시는 QUALITY만 충족하므로 종료 코드 2가 나온다. 운영 검토용 입력에는 아래 표의 ID 여섯 개를 모두 넣는다.

```json
{
  "schemaVersion": 1,
  "candidateCommit": "1212121212121212121212121212121212121212",
  "targetEnvironment": "production",
  "checks": [{
    "id": "QUALITY",
    "status": "PASS",
    "candidateCommit": "1212121212121212121212121212121212121212",
    "environment": "ci",
    "role": "release-operator",
    "verifiedAt": "2026-11-26T04:00:00Z",
    "evidenceId": "ev-0000000000000001"
  }]
}
```

| ID | 필수 증거 | 인수 기준 |
| --- | --- | --- |
| QUALITY | 같은 후보의 원격 품질 검사 | 후보 SHA가 일치하고 검사가 통과했거나 검토된 기준선에 대한 승인 기록이 있음 |
| REHEARSAL | 격리 기동·Flyway/JPA·백업 복원 | 같은 후보의 격리 결과를 운영 인수와 구분함 |
| POLICY | 판매자 정책 승인 | 게시할 원문·가입 동의와 배송·취소·반품·환불의 승인 증거가 있음 |
| TARGET | 운영 서버·실재고 인수 | 지정 대상의 기동·이행·복구·실재고 등록 증거를 같은 후보로 확인함 |
| PAYMENT | 운영 결제 검증 | 지정 대상의 운영 결제 설정과 제한된 구매 검증에 관한 담당자 기록을 확인함 |
| ROLLOUT | 배포 승인 및 롤백 준비 | 승인 역할·시각, 배포 승인과 롤백 절차의 증거를 확인함 |

정상 입력은 ID별 허용 필드와 결과 상태를 검사한다. 누락된 행이나 필수 증거, 승인 없는 기준선, 필수 점검의 `SKIPPED`는 미충족으로 판정한다. 중복 JSON 키·점검 ID, 알 수 없는 필드·상태, 잘못된 형식, 커밋·별칭 불일치는 `FAIL`로 처리한다. 잘못된 입력의 값과 원본 경로는 진단에 반사하지 않는다.

| 조건 | 결과 | 종료 코드 |
| --- | --- | --- |
| 모든 필수 점검이 PASS이거나 승인 기록이 있는 BASELINED | 검토 가능 | 0 |
| 형식·후보·대상 불일치 또는 필수 점검 FAIL | FAIL | 1 |
| 실패 없이 증거 누락, BLOCKED 또는 SKIPPED 존재 | BLOCKED | 2 |

JSON의 `result`는 `REVIEWABLE`, `FAIL`, `BLOCKED` 중 하나다. 요약은 후보 SHA와 점검 ID·분류·고정 사유 코드·종료 결과만 포함한다. 도구는 전달된 증거에 대해 검토 가능 여부를 판단한다. 외부 승인 검증과 실제 배포는 기존 담당 절차에 둔다.

## 검증

- 정상 및 허용된 기준선 승인 입력은 코드 0과 고정된 점검 순서를 확인한다.
- 파일·행·담당 역할·시각·승인 증거 누락, SKIPPED, BLOCKED 입력은 코드 2를 확인한다.
- JSON 문법·중복·지원하지 않는 상태, 후보나 환경 불일치, 명시적 실패와 누락이 함께 있는 입력은 코드 1을 확인한다.
- 민감 정보 표시용 토큰, 고객 식별자, 로그가 담긴 비허용 필드를 거부하고 입력값이 stdout·stderr·빌드 증거로 유출되지 않는지 합성 데이터로 확인한다.
- 문서, diff, 하네스 자체 검사와 전체 `./scripts/harness check`를 실행한다. 기존 실패 및 구조·보안 기준선을 유지한다.

2026-11-26 합성 CLI 검증 8개를 통과했다. 원격 main `34f9153`의 품질 검사 및 격리 리허설 증거로 실행한 사전점검은 2/6 충족으로 `BLOCKED`(종료 코드 2)다. 운영 정책·대상 및 실재고·운영 결제·배포 승인과 롤백 준비 증거가 남아 있다.

## 운영 증거 확보 순서

| 순서 | 담당 역할 | 확인할 내용 |
| --- | --- | --- |
| 1 · POLICY | 판매자·서비스 운영 | 게시 원문과 버전, 가입 동의, 배송비·취소·반품·환불 기준을 확정하고 승인 시각과 문서 식별자를 남긴다. |
| 2 · TARGET | 배포 운영·재고 담당 | 정책 승인과 병행해 후보 커밋과 실운영 대상 별칭을 확정한다. 승인된 접근 범위에서 기동 상태, DB 이행 및 복원 리허설, 실제 상품과 재고의 대조 증거를 남긴다. |
| 3 · PAYMENT | 결제 운영 | 대상 인수 후 운영 결제 설정과 미확정 거래 대응 담당자를 확인한다. 제한된 실구매 검증은 별도 승인 후 진행하고 비식별 결과만 기록한다. |
| 4 · ROLLOUT | 출시 승인 담당 | 대상 인수와 병행해 이전 이미지, DB 백업·복원 지점, 구매 중지 기준, 진행 중 거래 대조 및 복구 담당자를 정리한다. 같은 후보의 정책·대상·결제 증거를 확인한 뒤 배포 승인 기록을 남긴다. |

운영 증거는 승인된 내부 경로에 보관하고 사전점검 입력에는 비식별 식별자만 넣는다. 운영 역할의 실제 담당자와 대상 별칭이 확정되면 같은 후보 SHA의 증거를 모아 다시 실행한다.

- [커머스 출시 PRD](commerce-launch-prd.md)
- [배포 검증 재현성과 PR 품질 검사](release-readiness-prd.md)
- [병합 후 main 품질 검사](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45515506630)
- [준비 실행 기록](../exec-plans/completed/production-release-preflight-preparation.md)
- [구현 및 사전점검 기록](../exec-plans/completed/production-release-preflight-implementation.md)
- [운영 정책](../runbooks/service-policy.md)
- [재고 운영과 복구](../runbooks/inventory.md)
- [결제 운영](../runbooks/payment-environment.md)
