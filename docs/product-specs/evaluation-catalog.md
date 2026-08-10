# 평가 카탈로그

상태: 검증됨
기계 판독 원본: `harness/evals/catalog.tsv`
실행: `./scripts/harness eval`

평가 계층은 기존 JUnit 테스트를 중복하지 않으면서 제품·엔지니어링 위험을
이름이 있고 기계가 읽을 수 있는 증거로 변환합니다.

| 그룹 | 평가 대상 | 현재 상태 |
| --- | --- | --- |
| APP | Spring 애플리케이션 context | 통과 기준선 |
| DB | Flyway 이력과 JPA 검증 | 통과 기준선 |
| AUTH | 로그인, 잘못된 자격 증명, 회원가입·세션 | NC-TEST-001의 BASELINED 실패 |
| ORDER | 서버 계산 금액과 인가 | 통과 기준선 |
| PAY | 승인, 멱등성, 동시성, 복구, 취소 | 집중 테스트 통과 |
| STORAGE | mock S3 어댑터 계약 | 집중 테스트 통과 |
| SEC | 미인증 주문 거부 | 통합 테스트 통과 |
| OPS | 기계 판독 가능한 애플리케이션 상태 | NC-TEST-001의 BASELINED 실패 |

각 카탈로그 행은 ID, 위험 설명, 정확한 JUnit 테스트 ID, 성공 조건, 문서
경로를 가집니다. `eval`은 현재 Gradle XML을 읽어
`build/harness/eval/results.tsv`를 생성합니다.

`BASELINED`는 알려진 실패 위험이 계속 드러나며 변경되지 않았다는 뜻입니다.
제품 요구사항을 통과했다는 의미가 아닙니다. 실패가 추가·삭제·이름 변경되면
검토가 끝날 때까지 기준선 검사가 실패합니다.
