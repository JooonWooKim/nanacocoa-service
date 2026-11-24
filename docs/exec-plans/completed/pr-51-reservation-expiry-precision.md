# PR #51 주문 예약 만료 시각 정밀도 수정

상태: 완료 (로컬 수정 및 주문 회귀 검증)

## 목표

첫 주문 응답과 재요청·조회 응답이 저장된 재고 예약 만료 시각과 일치한다.

## 하지 않는 일

API 형식, 예약 기간, DB 이행, 운영 설정과 원격 브랜치는 수정 범위 밖이다.

## 현재 증거

[GitHub 품질 검사](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45312063595)에서 Java 264개 중 주문 제출 회귀 1개가 실패했다. 첫 응답의 소수점 9자리와 DB에서 읽은 6자리가 달랐다.

## 결정

예약을 기록하는 엔티티에서 기존 DB의 마이크로초 정밀도로 맞춘다. 이 값은 예약 만료 판단과 HTTP 응답에 함께 사용된다.

## 작업 분해

- [x] 원격 실패 로그와 로컬 코드 대조, 기존 파일 백업
- [x] 고정 시각 재현 및 로컬 수정
- [x] H2·격리 MySQL 주문 회귀 검증
- [x] 출시 PRD와 결과 기록 갱신

## 진행 상황

- 2026-11-24: `feature/50`에서 고정값 회귀 테스트의 수정 전 assertion 실패를 확인했다. 엔티티에서 시각을 정규화하고 주문 HTTP 테스트의 DB·제출 조회 대조를 추가했다.
- 2026-11-24: 공용 설정 전체 테스트와 H2·격리 MySQL 검증 결과를 [출시 PRD](../../product-specs/commerce-launch-prd.md)에 연결했다.

## 명령과 증거

| 검사 | 상태 | 증거 |
| --- | --- | --- |
| 원격 품질 검사 | FAIL | `submissionHttpContractScopesKeysAndReplaysStoredOrder`의 만료 시각 비교 실패 |
| 고정값 테스트 수정 전 | FAIL | DB 정밀도보다 많은 9자리 시각을 그대로 보관 |
| 고정값 테스트 및 H2 재고 통합 | PASS | 만료 직전·정각 경계, 첫 응답·재요청·조회·DB 값 일치, 예약 중복 방지 |
| `./gradlew --offline test --tests '*InventoryIntegrationTest'` | PASS | 격리 MySQL 8.4.10에서 33개 통과. Flyway 이행·JPA 검증 및 같은 HTTP·DB 계약 확인 |
| `./gradlew --offline test` | PASS | 공용 설정에서 265개 통과, 실패·오류·skip 0개. 발견 목록의 변경은 신규 회귀 테스트 1개 |
| `./scripts/harness check` | BASELINED | Java 265개와 평가 63개 통과. 기존 구조·보안 예외 유지. 새 테스트가 포함된 저장소 지도를 재생성하고 문서·마이그레이션 검사 통과 |
| Compose runtime smoke | SKIPPED | 전체 하네스 실행 시 별도 Compose 스택을 기동하지 않았다. 격리 MySQL 검증은 위에 기록했다 |
| 수정 커밋의 GitHub 재검사 | BLOCKED | 로컬 수정의 원격 반영 대기 |

## 위험과 롤백

예약 생성 시 1마이크로초 미만을 버린다. 기존 주문에 데이터 이행은 필요 없다. 코드 변경은 해당 정밀도 처리만 되돌릴 수 있다.

## 미해결 항목

수정 후 GitHub 검사는 원격에 반영되면 확인한다.

## 완료 조건

고정값 회귀, 기존 주문 계약, 기준선을 확인하고 실제 결과를 출시 PRD에 남긴다.
