# 회원 장바구니 운영·검증

상태: feature/34 로컬 구현 · main 미반영

## 계약

회원별 GET /api/cart, POST /api/cart/items, PUT·DELETE /api/cart/items/{id}를 제공한다.
담기는 UUID Idempotency-Key로 재전송하며 수량 변경·삭제는 expectedVersion을 요구한다.
삭제 버전은 query parameter다. 같은 키의 다른 담기·버전/가격 변경은 409,
타인·없는 항목은 같은 404다. 조회·변경은 no-store, 변경은 세션 CSRF를 요구한다.
수량은 문자열·소수 변환 없이 양의 int만 받는다. 재고 예약은 주문 생성에서만 한다.

## 결제 후 정리

서버 승인 확정과 READY가 함께 커밋된 뒤 별도 트랜잭션에서 처리한다.
기본 cart.cleanup.fixed-delay는 30s, initial-delay는 30s이며 회당 최대 100개다.
실제 PG·S3·payment.reconciliation 플래그와 무관한 DB 전용 작업이다.

DB 장애 시 READY가 유지되어 다음 실행이 재처리한다. 승인·재고 확정·보상 취소를
장바구니 정리 목적으로 다시 실행하지 않는다. order 내부 ID만 경고 로그에 남긴다.
cart_checkouts.state와 cart_checkout_items.outcome(WAITING/REMOVED/PRESERVED)을 확인한다.
PRESERVED는 주문 후 수정·삭제·재등록된 항목을 보호한 정상 결과다. 환불로 자동 재담기하지 않는다.
정리 결과나 항목 버전을 수동으로 초기화하면 중복 차감 위험이 있으므로 변경하지 않는다.

## 검증

```sh
./gradlew test --tests '*CartIntegrationTest'
node --test src/test/js/cart.test.cjs
./gradlew commerceE2e
# 별도 터미널: 테스트 서버 전용, 실제 PG 호출 없음
python3 -B src/test/python/cart_e2e_http.py
```

CART_TEST_DB_URL은 빈 격리 MySQL 8.4 테스트 DB만 지정한다. 미지정 시 H2다.
V11은 기존 데이터 이행을 요구하지 않으며 새 테이블만 추가한다. 적용 이력은 수정하지 않는다.
배포·운영 설정·정책 승인과 main 반영은 별도다.
[PRD](../product-specs/cart-prd.md), [테스트 운영](testing.md).
