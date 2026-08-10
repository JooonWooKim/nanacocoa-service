# Nanacocoa 아키텍처

상태: 2026-07-25 작업 트리 기준 검증됨
갱신 조건: 도메인, 계층 경계, 외부 연동, 영속성 모델, 인증·결제 흐름 변경

## 시스템 구성

Nanacocoa는 하나의 Spring Boot 애플리케이션에서 정적 쇼핑 페이지와 JSON
API를 제공합니다. MySQL은 영구 데이터의 기준 저장소이고, Redis는 분산
결제 락을 지원하며, Toss Payments는 결제 제공자입니다. 상품 이미지는
설정에 따라 mock 구현 또는 S3를 사용합니다.

```text
브라우저
  -> Spring Security / MVC
     -> Controller
        -> Service 또는 PaymentFacade
           -> Repository -> JPA -> MySQL
           -> ProductImageStorage -> mock 또는 S3
           -> OrderPaymentLock -> 로컬/Redis 락
           -> TossPaymentsClient -> Toss Payments
  -> Actuator 상태 확인
```

## 도메인 지도

| 영역 | 책임 | 진입 경계 | 영속성 |
| --- | --- | --- | --- |
| `member` | 회원가입, 로그인, 세션 식별 | `AuthController` / `AuthService` | `MemberRepository` |
| `products` | 상품 목록과 이미지 등록 | `ProductsController` / `ProductsService` | `ProductsRepository` |
| `order` | 서버 가격 기반 주문과 배송 정보 | `OrderController` / `OrderService` | `OrderRepository` |
| `payment` | 승인, 취소, 멱등성, 락, 복구 | `PaymentController` / `PaymentFacade` | 결제 Repository |
| `common` | 응답, 예외, 보안, 세션, 사용자 상세 | 공통 프레임워크 어댑터 | 회원 조회는 기록된 예외 |

생성된 패키지 목록은
[docs/generated/repository-map.md](docs/generated/repository-map.md)에 있습니다.

## 계층 의존 방향

정상 요청 방향은 다음과 같습니다.

```text
controller -> service/facade -> repository -> entity/database
                         \----> 제공자/저장소/락 어댑터
```

- Controller는 요청·응답 DTO, 공통 웹·보안 타입, 하나의 Service·Facade
  경계에만 의존할 수 있습니다.
- Service와 Facade는 비즈니스 동작과 트랜잭션을 조정합니다.
- Repository는 Entity와 Spring Data에만 의존합니다.
- Entity는 상태 불변식을 구현하며 웹 계층에 의존하지 않습니다.
- 응답 DTO는 Entity에서 매핑할 수 있지만 Controller가 Entity를 직접
  반환하지 않습니다.

`common.userdetails`는 현재 `member` Entity·Repository 타입에 의존합니다.
이 역방향 의존성은 이상적 구조로 숨기지 않고 기준선과 기계적 래칫으로
고정했으며 NC-ARCH-001로 추적합니다.

### 기계적 강제 범위

`./scripts/harness architecture`는 고정된 파일명 목록이 아니라 모든
`@RestController`와 `JpaRepository` 선언을 찾습니다. 현재 Controller 위치,
정확히 하나의 Service·Facade import 경계, Controller의 Repository·Entity
참조와 트랜잭션 금지, Repository 패키지·인터페이스 형태, Repository의
상위 계층 import·default method 금지, 비웹 계층의 Controller import 금지,
요청 패키지 철자 일관성, 정확한 `common -> domain` 예외 집합을 강제합니다.

이는 소스 수준의 구조 검사이며 완전한 Java 의미 분석기가 아닙니다. 런타임
호출 그래프나 비즈니스 정확성을 증명하지 않으며, 해당 영역은 집중 테스트,
리뷰, 평가 카탈로그가 담당합니다.

## 트랜잭션과 데이터

- 회원·상품·주문 변경은 Service 계층 트랜잭션을 사용합니다.
- 결제 처리는 외부 제공자 호출과 `PaymentTransactionalService`의
  `REQUIRES_NEW` 영속성 상태 전이를 분리합니다.
- `PaymentFacade`는 인증, 락, 제공자 호출, 보상, 응답 매핑을 조정합니다.
- 낙관적 버전과 멱등성 컬럼이 주문·결제 상태를 보호합니다.
- `PaymentRecoveryService`는 대기·불확실 상태의 제공자 결과를 조정합니다.
- `src/main/resources/db/migration`의 Flyway 파일이 운영 스키마 이력입니다.
  MySQL 프로필에서 Hibernate는 `ddl-auto=validate`를 사용합니다.
- H2 테스트 프로필은 일반 애플리케이션 테스트에서 Flyway를 비활성화하며,
  전용 마이그레이션·스키마 테스트가 운영 호환 DDL을 별도로 검증합니다.

생성된 스키마 입력은 [docs/generated/db-schema.md](docs/generated/db-schema.md)에
기록합니다. 마이그레이션 불변성은
`harness/baselines/migration-checksums.sha256`을 기준으로 검사합니다.

## 인증과 인가

Spring Security는 `SecurityConfig`에 따라 정적 자원, 인증·상품 API, 주문 생성,
Actuator health·info를 허용하며 나머지 요청은 인증된 세션이 필요합니다.
`SessionLoginService`는 Spring Security context를 HTTP 세션에 저장합니다.
상품·주문·결제 Service는 현재 DB 인가 상태가 중요한 경우 회원을 다시
조회합니다.

인증 통합 테스트에는 현재 알려진 계약 실패가 있습니다. 해당 목록은
`harness/baselines/test-failures.txt`와 NC-TEST-001에 기록되어 있습니다.
기준선은 현재 동작이 올바르다는 의미가 아닙니다.

## 결제 흐름

### 승인

1. `PaymentController`가 요청과 principal을 검증합니다.
2. `PaymentFacade`가 주문 범위 락을 획득합니다.
3. `PaymentTransactionalService`가 소유권, 금액, 상태, fingerprint, 멱등성
   키를 검증한 뒤 대기 중 시도를 저장합니다.
4. 초기 트랜잭션 밖에서 `TossPaymentsClient`를 호출합니다.
5. 새 트랜잭션이 승인, 확정 실패, 불확실 상태 또는 보상을 기록합니다.

### 취소와 복구

취소는 별도의 취소 Entity와 멱등성 키를 사용합니다. 불확실한 결과는 재시도
메타데이터를 유지합니다. `PaymentRecoveryService`는 동일한 주문 락 아래에서
권위 있는 제공자 조회·재시도를 수행하고 Entity 상태 전이 메서드로만 상태를
변경합니다.

평가 카탈로그는 이러한 위험을 기존 테스트에 연결합니다.
[docs/product-specs/evaluation-catalog.md](docs/product-specs/evaluation-catalog.md).

## 외부 경계

- `TossPaymentsClient`는 타입이 지정된 결제 경계입니다. 테스트에서는 이를
  mock 처리하며 실제 제공자를 호출하면 안 됩니다.
- `ProductImageStorage`는 설정에 따라 mock 또는 S3 동작을 선택합니다.
- `OrderPaymentLock`은 로컬·Redis 락 동작을 추상화합니다.
- 비밀값은 환경 변수로만 주입하며 소스, 증거, 로그, 생성 문서에 나타나면
  안 됩니다.

## 변경 절차

경계가 변경되면 다음을 수행합니다.

1. 이 지도와 관련 runbook·품질 문서를 갱신합니다.
2. 구조·동작 테스트를 추가하거나 갱신합니다.
3. `./scripts/harness generate-docs`로 문서를 재생성합니다.
4. `./scripts/harness check`와 관련 엄격 테스트를 실행합니다.
5. 활성 실행 계획과 품질·부채 상태를 갱신합니다.
