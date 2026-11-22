# 회원 배송지 운영·검증

상태: feature/36 로컬 구현 · main 미반영

## API 계약

본인 `/api/members/me/shipping-addresses`에 GET 목록·POST 등록, `/{id}`에 PUT 수정·DELETE 삭제,
`/{id}/default`에 PUT 기본 지정을 제공한다. 성공은 HTTP 200·기존 SuccessMessage로
revision·checkoutAccount·items를 반환한다. 원문 주소는 인증된 본인에게만 반환하고 no-store다.
등록/수정의 address는 label·recipientName·phoneNumber·postalCode·basicAddress·detailAddress다.
등록은 makeDefault·expectedRevision과 UUID Idempotency-Key를 요구한다.
수정과 기본 지정은 expectedVersion·expectedRevision을 요구하며 삭제는 두 값을 query로 받는다.
클라이언트는 X-Checkout-Account로 이전 계정의 입력을 다른 계정에 쓰지 않도록 한다.
소유권은 세션에서 결정한다. 모든 변경은 CSRF를 요구한다.

주소록/항목 변경 충돌·다른 내용의 등록 키 재사용은 ADDRESS_02/409,
타인/없는 주소는 동일한 ADDRESS_01/404다. 입력 오류는 기존 COMMON_01/400을 사용한다.
우편번호 5자리, 배송지명 80·수령인 100·연락처 30자, 조합된 전체 주소 500자 제한이다.

## 데이터와 복구

최초 주소는 기본이며 추가 주소는 명시적으로 기본 지정할 때만 바뀐다. 기본 주소 삭제 후
자동 승격하지 않는다. 남은 주소가 있어도 새 등록을 임의 기본 지정하지 않는다.
모든 주소 삭제 뒤 다시 첫 주소를 등록하면 기본이 된다. 회원 프로필과 기존 주문은 변경하지 않는다.

V12는 새 테이블만 추가한다. 회원 잠금 아래 revision·version을 확인하고 단일 기본 포인터와
소유권 복합 FK로 보호한다. 등록 원문을 재전송 기록에 복제하지 않고 fingerprint를 저장한다.
응답 유실 시 목록을 조회하고 같은 요청 키/원본 요청으로 사용자가 재시도한다. 자동 POST 재시도는 없다.
과거 등록 키나 revision을 수동 초기화하면 중복 등록 위험이 있으므로 변경하지 않는다.

## checkout과 개인정보

새 주문서의 미편집 배송 정보에만 기본값을 한 번 입력한다. 기본값 없음·조회 실패 시
직접 입력할 수 있다. 명시적으로 다른 배송지를 선택하면 현재 폼이 바뀐다고 안내한다.
입력을 수정해도 주소록에 자동 저장하지 않는다. 접수된 주문의 복구는 기존 스냅샷을 사용한다.
전체 주소록을 브라우저 저장소·URL·로그·검증 증거에 기록하지 않는다. 계정 전환·로그아웃과
뒤로 가기 캐시에서 이전 주소 원문을 제거한다. 화면 검증은 합성 데이터만 사용한다.

## 검증 명령

```sh
./gradlew test --tests '*ShippingAddressIntegrationTest'
node --test src/test/js/shipping-addresses.test.cjs
./gradlew commerceE2e
# 별도 터미널, loopback 합성 환경만 사용
python3 -B src/test/python/shipping_address_e2e_http.py
```

격리 MySQL 8.4에서는 ADDRESS_TEST_DB_URL과 빈 ADDRESS_MIGRATION_TEST_DB_URL을 사용한다.
환경 전환 시 Gradle 캐시 재사용을 피하려면 --rerun-tasks로 실행한다. 운영 DB를 지정하지 않는다.
테스트는 실제 PG·S3를 호출하지 않으며 이메일 복구·운영 활성화·배포는 별도다.
[구현 PRD](../product-specs/shipping-address-prd.md).
