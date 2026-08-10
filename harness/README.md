# 하네스 원본

상태: 2026-07-25 검증됨

- `baselines/test-failures.txt`는 정렬된 정확한 엄격 테스트 실패 래칫입니다.
- `baselines/test-failure-fingerprints.txt`는 각 알려진 실패의 failure·error
  종류, 예외 타입, assertion 메시지를 고정합니다.
- `baselines/test-inventory.txt`는 전체 63개 테스트 발견 집합을 고정하며
  skip된 테스트를 허용하지 않습니다.
- `baselines/architecture-exceptions.txt`는 현재 역방향 도메인 의존성의
  정확한 래칫입니다.
- `baselines/security-exceptions.txt`는 조용히 증가하거나 변경되면 안 되는
  정확한 검토 완료 보안·데이터·제공자 기본값 위험 집합입니다.
- `baselines/migration-checksums.sha256`은 기존 Flyway 입력을 보호합니다.
- `evals/catalog.tsv`는 핵심 위험을 정확한 JUnit 증거에 연결합니다.

명령을 통과시키기 위해 기준선을 갱신하지 않습니다. 변경을 조사하고 회귀를
수정하며 검증된 해결 항목만 제거한 뒤 부채 추적기와 품질 증거를 갱신합니다.

이 디렉터리를 변경한 뒤 `./scripts/harness check`를 실행합니다.
