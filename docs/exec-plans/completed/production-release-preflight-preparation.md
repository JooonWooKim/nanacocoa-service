# 운영 배포 사전점검 준비

상태: 완료 (구현 준비)

## 목표

[이슈 #52](https://github.com/JooonWooKim/nanacocoa-service/issues/52)의 구현 범위와 인수 기준을 만들고 구현 브랜치를 준비한다.

## 하지 않는 일

명령 구현, 운영 접속, 판매자 승인, 실결제, 배포와 운영 활성화는 별도다.

## 현재 증거

[PR #51](https://github.com/JooonWooKim/nanacocoa-service/pull/51)이 병합된 `34f9153`의 [main 품질 검사](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45515506630)가 통과했다. 11월 25일에는 합성 가입·구매와 격리 배포 리허설을 반복 검증했다.

## 결정

로컬 증거 파일을 읽는 CLI를 후속 구현 대상으로 선택했다. 격리 기술 검증과 운영 대상 증거를 각각 확인한다. 모든 필수 증거가 갖춰지면 운영 담당자가 배포를 검토한다.

## 작업 분해

- [x] 최신 main과 기존 GitHub 이슈·PR 확인, 사용자 파일 보존
- [x] 이슈·추적 브랜치 생성, PRD와 인덱스·출시 PRD 연결
- [x] 문서·diff·하네스 확인 및 준비 기록 보관

## 진행 상황

- 2026-11-26: 기존 파일 16개를 백업하고 최신 main에서 `feature/52`를 생성했다. 강제 전환 없이 추적 브랜치로 전환했으며 16개 해시의 일치를 확인했다.
- 2026-11-26: PRD, 출시 PRD와 제품 인덱스를 연결했다. 기존 파일 14개는 해시가 같았고, 함께 편집한 두 문서는 이번 작업의 추가분을 백업과 대조했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `gh issue list` | PASS | 같은 목적의 이슈가 없어 #52를 생성했다. |
| `git switch --track -c feature/52 origin/feature/52` | PASS | 로컬과 원격이 main `34f9153`을 가리키며 전환 후 기존 파일의 SHA-256이 일치했다. |
| `./scripts/harness docs` | PASS | 구현 예정 명령을 준비 상태로 명시한 뒤 문서·링크 검사 통과. |
| `git diff --check` | PASS | 작업 트리 공백·줄 끝 검사 통과. |
| `./scripts/harness check` | BASELINED | 기존 아키텍처·보안 예외 유지. Java 265개와 평가 63개 통과. |
| Compose runtime smoke | SKIPPED | 준비 검증에 별도 런타임을 실행하지 않았다. |

## 위험과 롤백

기존 문서는 작업 트리 밖에 보관했다. 함께 갱신한 출시 PRD와 제품 인덱스의 변경은 백업본과 별도로 대조한다.

## 미해결 항목

[구현 PRD](../../product-specs/production-release-preflight-prd.md)의 CLI 및 검증을 후속 작업에서 수행한다. 운영 정책과 대상을 확인할 때까지 운영 배포는 보류한다.

## 완료 조건

문서와 이슈, 추적 브랜치, 검증 결과를 연결하고 작업 트리에서 문서를 확인할 수 있다.
