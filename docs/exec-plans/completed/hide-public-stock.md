# 일반 회원 재고 수량 비공개

상태: 완료

## 목표

공개 상품 API와 화면에서 재고 수량·예약 수량·버전을 제외하고 관리자 전용 조회로 분리합니다.

## 하지 않는 일

품절/판매 중지 상태, 서버 재고 검증, 기존 주문·결제 동작은 유지합니다.

## 현재 증거

ProductsResponse가 재고 수량·예약 수량·버전을 공개하며 checkout이 수량을 사용하고 있었습니다. 기존 변경 파일을 보존합니다.

## 결정

GET /api/products/inventory는 DB 관리자 권한을 재검증하며 InventoryResponse만 반환합니다. 공개 응답은 판매 상태만 유지하고 checkout 재고 상한은 서버가 검증합니다.

## 작업 분해

- [x] 조사
- [x] 구현
- [x] 검증
- [x] 리뷰와 문서화

## 진행 상황

- 2026-10-24: 공개 DTO에서 수량·버전을 제거하고 관리자 전용 API 및 UI 연결을 추가했습니다. 익명·회원·관리자 공개 응답과 관리자 권한 회수 회귀 검증을 보강합니다.

- 2026-10-24: 집중 테스트 PASS, 전체 check BASELINED(117개·skip 0·기존 실패 5개 유지). 아키텍처·문서·보안·마이그레이션 검사와 실행 스택 smoke 통과. 데이터 변경 없이 공개 정보 노출과 관리자 권한 회수 경로를 검토했습니다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short` | PASS | 기존 재고 구현 변경을 유지 |
| `./gradlew test --tests ...InventoryIntegrationTest --tests ...ProductsServiceTest` | PASS | 공개 수량 미노출, 관리자 조회/권한 회수, 기존 재고 동작 |
| `node --check src/main/resources/static/script.js` | PASS | 구문 검사 |
| `./scripts/harness check` | BASELINED | 117개·기존 fingerprint 실패 5개 유지·새 회귀 없음 |

## 위험과 롤백

관리자 정보 노출 회귀와 checkout 재고 검증 우회를 검토합니다. 데이터/마이그레이션 변경은 없습니다.

## 미해결 항목

없음. 실행 중 서비스에는 재빌드·재시작 후 반영됩니다.

## 완료 조건

공개 응답 수량 미포함, 관리자 조회 인가, 공개 화면 수량 미사용, 집중 테스트와 전체 check 실행 및 상태 분류.
