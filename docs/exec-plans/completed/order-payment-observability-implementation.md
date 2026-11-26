# 주문·결제 장애 추적 및 해결 자료 구현

상태: 완료

## 목표

[이슈 #57](https://github.com/JooonWooKim/nanacocoa-service/issues/57)의 화면과 비식별 로그를 연결하고, 요청 번호별 검증 자료를 만든다.

## 하지 않는 일

Slack·AI 연동, 실제 운영 로그 접속, 운영 결제·배포와 상태 전이 변경은 후속 범위다.

## 현재 증거

[구현 PRD](../../product-specs/order-payment-observability-prd.md)는 요청·자동 복구 식별과 주어진 증거의 오프라인 분석을 요구한다.

## 결정

서버 발급 UUID와 제한된 이벤트 JSON을 사용한다. 담당자가 관련 테스트를 확인하고 직접 실행하는 오프라인 명령을 제공한다.

## 작업 분해

- [x] 기존 변경 백업 및 요청·복구 흐름 대조
- [x] 서버·화면·해결 자료 구현
- [x] 회귀 검증 및 출시 문서 갱신

## 진행 상황

- 2026-11-26: `feature/57`의 기존 변경 파일 18개를 백업했다.
- 2026-11-26: 주문·결제 네 요청과 결제 복구 작업에서 정형 이벤트를 남겼다. checkout·운영 결제 화면과 오프라인 해결 자료 명령을 연결했다.
- 2026-11-26: Chrome 모의 승인 거절과 실제 HTTP 요청 번호를 정형 로그와 대조했다. 구현 PRD·출시 PRD·운영 절차에 증거와 잔여 범위를 기록했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status -sb` | PASS | 현재 브랜치와 스테이징되지 않은 사용자 변경을 확인하고 SHA-256을 기록했다. |
| `./gradlew --offline test` | PASS | H2 Java 268개. 실패·건너뜀 없이 신규 요청 격리·복구 로그·권한 및 503 회귀를 포함했다. |
| `./scripts/harness mysql-quality` | PASS | 격리 MySQL 8.4.10 이행·재고·결제·반품 68개. |
| `node --test src/test/js/*.test.cjs` | PASS | JavaScript 11개 파일. 운영 화면의 유효한 번호·비정상 헤더·응답 유실·재조회·중복 방지. |
| `python3 -B src/test/python/test_incident_evidence.py` | PASS | 재고·승인·복구 실패별 안내, 출력 정렬, 후보 불일치, 허용 목록과 변경 범위 검증 5개. |
| `python3 -B src/test/python/commerce_e2e_http.py` | PASS | 로컬 H2·모의 PG 여섯 시나리오. 기존 멱등성·복구·환불·재입고와 네 API의 서로 다른 요청 번호 56개를 확인했다. |
| Chrome checkout | PASS | 합성 승인 거절 화면과 HTTP 422 로그의 번호 `47390725-65b5-4daa-9cbb-9cbf236f69a8` 일치. 주문서 복귀 뒤 번호가 지워졌다. |
| `./scripts/harness incident-evidence` | PASS | 브라우저 승인 거절 및 HTTP 승인 미확정 이벤트로 각각 한 번호의 자료를 생성했다. 로컬 실행은 기준 HEAD `61dc8c0`과 작업 트리의 구현을 사용했다. |
| `./scripts/harness docs`, `git diff --check` | PASS | 문서 링크, 생성 참조, 공백 검사. |
| `./scripts/harness check` | BASELINED | 평가 63개와 DB·문서·테스트 통과. 기존 구조 예외 3개, 보안 예외 2개 유지. Compose 실행 상태 검사는 SKIPPED였다. |
| 운영자 Chrome 클릭·배포 대상 인수 | SKIPPED | 개별 복구는 실제 모의 PG HTTP와 JavaScript로 확인했다. 운영자 Chrome과 배포 대상은 별도 인수 범위다. |

## 위험과 롤백

요청 번호는 업무 데이터와 분리하고, 기록 형식과 권장 테스트를 합성 환경에서 검증했다. 기존 사용자 변경 파일 중 함께 편집한 PRD 세 개는 최초 백업과 추가분을 비교했고 나머지 15개는 SHA-256이 일치했다.

## 미해결 항목

main 반영과 실제 운영 인수를 추적한다. Slack·AI 연동은 후속 범위다.

## 완료 조건

서버·화면·CLI에서 정상과 실패 경로를 검증하고 출시 PRD에 실제 상태를 남긴다.
