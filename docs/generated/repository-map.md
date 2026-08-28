# 자동 생성 저장소 지도

상태: 자동 생성
원본: Java 소스·테스트 경로, resource, script, root build·배포 파일
재생성: `./scripts/harness generate-docs`

이 파일을 직접 수정하지 않습니다. docs 검사가 현재 소스 입력과 비교합니다.

## 목록

| 영역 | Java 파일 | 주요 역할 |
| --- | ---: | --- |
| `common` | 10 | 공통 응답, 오류, 보안, 세션, 사용자 상세 |
| `member` | 7 | 식별, 회원가입, 로그인, 세션 |
| `order` | 10 | 주문, 배송, 서버 가격 계산 |
| `payment` | 30 | 승인, 취소, 락, 복구 |
| `products` | 14 | 상품 목록과 이미지 저장 |

- 주요 Java 파일: 72
- 테스트 Java 파일: 15
- Flyway 마이그레이션: 3
- 정적 자원·페이지: 17

## 주요 패키지 디렉터리

```text
src/main/java/com/nanacocoa/server
src/main/java/com/nanacocoa/server/common
src/main/java/com/nanacocoa/server/common/exception
src/main/java/com/nanacocoa/server/common/response
src/main/java/com/nanacocoa/server/common/security
src/main/java/com/nanacocoa/server/common/security/config
src/main/java/com/nanacocoa/server/common/session
src/main/java/com/nanacocoa/server/common/userdetails
src/main/java/com/nanacocoa/server/member
src/main/java/com/nanacocoa/server/member/controller
src/main/java/com/nanacocoa/server/member/dto
src/main/java/com/nanacocoa/server/member/dto/reqeust
src/main/java/com/nanacocoa/server/member/dto/response
src/main/java/com/nanacocoa/server/member/entity
src/main/java/com/nanacocoa/server/member/repository
src/main/java/com/nanacocoa/server/member/service
src/main/java/com/nanacocoa/server/order
src/main/java/com/nanacocoa/server/order/controller
src/main/java/com/nanacocoa/server/order/dto
src/main/java/com/nanacocoa/server/order/dto/reqeust
src/main/java/com/nanacocoa/server/order/dto/response
src/main/java/com/nanacocoa/server/order/entity
src/main/java/com/nanacocoa/server/order/repository
src/main/java/com/nanacocoa/server/order/service
src/main/java/com/nanacocoa/server/payment
src/main/java/com/nanacocoa/server/payment/config
src/main/java/com/nanacocoa/server/payment/controller
src/main/java/com/nanacocoa/server/payment/dto
src/main/java/com/nanacocoa/server/payment/dto/reqeust
src/main/java/com/nanacocoa/server/payment/dto/response
src/main/java/com/nanacocoa/server/payment/entity
src/main/java/com/nanacocoa/server/payment/facade
src/main/java/com/nanacocoa/server/payment/lock
src/main/java/com/nanacocoa/server/payment/pg
src/main/java/com/nanacocoa/server/payment/repository
src/main/java/com/nanacocoa/server/payment/service
src/main/java/com/nanacocoa/server/products
src/main/java/com/nanacocoa/server/products/controller
src/main/java/com/nanacocoa/server/products/dto
src/main/java/com/nanacocoa/server/products/dto/reqeust
src/main/java/com/nanacocoa/server/products/dto/response
src/main/java/com/nanacocoa/server/products/entity
src/main/java/com/nanacocoa/server/products/repository
src/main/java/com/nanacocoa/server/products/service
src/main/java/com/nanacocoa/server/products/storage
```

## 테스트 클래스

```text
src/test/java/com/nanacocoa/server/NanacocoaServerApplicationTests.java
src/test/java/com/nanacocoa/server/migration/FlywayMigrationTest.java
src/test/java/com/nanacocoa/server/migration/JpaSchemaValidationTest.java
src/test/java/com/nanacocoa/server/order/service/OrderServiceTest.java
src/test/java/com/nanacocoa/server/payment/config/PaymentLockConfigTest.java
src/test/java/com/nanacocoa/server/payment/controller/PaymentClientConfigControllerTest.java
src/test/java/com/nanacocoa/server/payment/controller/PaymentControllerTest.java
src/test/java/com/nanacocoa/server/payment/facade/PaymentFacadeTest.java
src/test/java/com/nanacocoa/server/payment/lock/PaymentLockManagerTest.java
src/test/java/com/nanacocoa/server/payment/service/PaymentClientConfigServiceTest.java
src/test/java/com/nanacocoa/server/payment/service/PaymentRecoveryServiceTest.java
src/test/java/com/nanacocoa/server/payment/service/PaymentStateTest.java
src/test/java/com/nanacocoa/server/payment/service/PaymentTransactionalServiceTest.java
src/test/java/com/nanacocoa/server/products/service/ProductsServiceTest.java
src/test/java/com/nanacocoa/server/products/storage/S3ProductImageStorageTest.java
```

## 운영 진입점

- 빌드: `./gradlew`, `build.gradle`
- 하네스: `./scripts/harness`
- 환경 초기화: `scripts/init-env.sh`
- 지식 root: `AGENTS.md`, `ARCHITECTURE.md`, `docs/`
