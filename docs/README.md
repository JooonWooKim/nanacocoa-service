# Nanacocoa 지식 베이스

상태: 활성
목적: 에이전트와 유지보수자를 위한 버전 관리되는 기준 정보

먼저 [아키텍처 지도](../ARCHITECTURE.md)를 읽고, 이후 작업에 필요한 문서만
선택해서 확인합니다.

## 설계와 제품

- [설계 문서 인덱스](design-docs/index.md)
- [핵심 엔지니어링 원칙](design-docs/core-beliefs.md)
- [제품 명세 인덱스](product-specs/index.md)
- [평가 카탈로그](product-specs/evaluation-catalog.md)

## 계획

- [실행 계획 절차](exec-plans/README.md)
- [실행 계획 템플릿](exec-plans/PLANS.md)
- [활성 계획](exec-plans/active)
- [완료 계획](exec-plans/completed)
- [기술부채 추적기](exec-plans/tech-debt-tracker.md)

## 운영 절차

- [로컬 개발](runbooks/local-development.md)
- [테스트](runbooks/testing.md)
- [데이터베이스 마이그레이션](runbooks/database-migrations.md)
- [장애 디버깅](runbooks/incident-debugging.md)
- [CI 계약](runbooks/ci.md)
- [유지보수 자동화](runbooks/automation.md)

## 품질

- [품질 점수](quality/QUALITY_SCORE.md)
- [신뢰성](quality/RELIABILITY.md)
- [보안](quality/SECURITY.md)
- [테스트 품질](quality/TESTING.md)
- [관측성](quality/OBSERVABILITY.md)

## 자동 생성 참조 문서

- [저장소 지도](generated/repository-map.md)
- [데이터베이스 스키마](generated/db-schema.md)

자동 생성 참조 문서는 `./scripts/harness generate-docs`로 다시 만들고
`./scripts/harness docs`로 검증합니다.
