# 유지보수 자동화

상태: 제안, 외부 schedule은 활성화하지 않음

권장 저장소 소유 주기:

| 주기 | 명령 | 목적 |
| --- | --- | --- |
| 모든 변경 | `./scripts/harness check` | 회귀와 drift 게이트 |
| 활성 작업 중 매일 | `./scripts/harness test-baseline` | 새 실패와 해소된 실패 탐지 |
| 매주 | `./scripts/harness entropy` | 부채, 예외, 오래된 계획, 작업 표시, 고립 문서 후보, 중복 이름, 반복 실패, 생성·마이그레이션 drift 탐색 |
| 마이그레이션·패키지 변경 후 | `./scripts/harness generate-docs` | 생성 참조 문서 갱신 |
| 인계 전 | `./scripts/harness evidence` | 비식별화된 상태 보존 |

entropy 명령은 진단만 수행합니다. 파일 삭제, 기준선 갱신, 광범위한
refactor를 수행하지 않습니다.

자동 삭제, merge, 배포, 제공자 호출, 기준선 수락을 schedule하지 않습니다.
저장소에 remote, 범위가 제한된 자격 증명, 리뷰 규칙, 명시적 사용자 승인이
생긴 후에만 hosted 자동화가 유지보수 변경안을 열 수 있습니다.
