# 구매·환불·재입고 종단 검증

상태: 테스트 환경 구현·기술 시나리오 검증 완료 · 운영 인수 별도

## 안전 경계와 시작

Java 17과 Gradle wrapper를 사용한다. 일반 애플리케이션·운영 설정을 바꾸지 않는다.
테스트 서버는 src/test classpath에만 있으며 운영 bootJar에 포함되지 않는다.
고정 loopback port 18082가 비어 있어야 한다. 기존 서비스를 중지하지 않는다.

```sh
./gradlew commerceE2e
```

E2E_READY가 출력되면 브라우저에서 http://127.0.0.1:18082/checkout.html?id=1 을 연다.
종료는 실행 터미널의 Ctrl+C다. H2 메모리 DB는 프로세스 종료 시 사라지므로
깨끗하게 재현하려면 이 작업의 서버만 재시작한다.

이 작업은 application-e2e 설정 이름으로 실행하며 기존 비공개 application.yml과 .env를
읽지 않는다. 명령행에서 test 프로필·H2·Flyway·JPA validate·mock 이미지 저장소를 고정한다.
실제 PG와 자동 복구·주문 만료·가입은 끄고 합성 RETURN 정책만 테스트 메모리에서 설정한다.
정책 test-only-v1은 실제 운영 승인이 아니다.

## 가상 데이터와 시나리오

이메일은 buyer@example.test, admin@example.test, other@example.test이고 공통 가상
비밀번호는 E2eFixture123! 이다. 이 문자열은 폐기 가능한 fixture이며 실제 계정에 사용하지 않는다.
관리자는 실제 로그인과 현재 DB 관리자 확인을 통과한다.

| 상품 ID | 1개 결제 금액 | 모의 PG 시나리오 |
| --- | --- | --- |
| 1 | 41000 | 정상 승인·환불 |
| 2 | 42000 | 최초 승인 응답 유실, 제공자 조회는 승인 완료 |
| 3 | 43000 | 명시적 승인 거절 |
| 4 | 44000 | 최초 환불 응답 유실, 제공자 조회는 환불 완료 |
| 5 | 45000 | 최초 환불 거절, 명시적 재시도 성공 |
| 6 | 46000 | 정상 승인·환불, 출고·회수·재판매 불합격 검수용 |

금액별 시나리오는 해당 상품 1개 구매가 기준이다. SDK 대역은 테스트 checkout에서만 주입되며
가상 결제 키를 만들고 기존 성공 콜백으로 돌아온다. 주문·승인·취소·복구·재고 API는
실제 애플리케이션 로직을 실행한다. 대역은 결제사 UX·실제 PG 호환성을 검증하지 않는다.

## 실제 HTTP 검증

별도 터미널에서 실행한다. 표준 라이브러리만 사용하며 대상은 고정 loopback 서버다.

```sh
mkdir -p build/e2e
python3 src/test/python/commerce_e2e_http.py > build/e2e/http-evidence.json
```

각 실행은 새 주문을 생성하므로 재실행 전 서버를 재시작하는 것을 권장한다.
6개 시나리오에서 실제 쿠키·로그인·회전 CSRF·멱등성·소유권·관리자 권한을 검증한다.
환불·재입고 중복은 반환 결과와 관리자 전용 비식별 DB snapshot을 대조한다.
승인·환불 확인 중 복구는 기본 30초 재시도 시간을 기다린다. DB 시간을 수정하지 않는다.
성공 출력에는 가상 주문/신청 ID, PG 호출 횟수, 상품 재고, 주문·환불 상태만 포함한다.
실패 시 assertion으로 중단하며 부분 결과를 전체 PASS로 취급하지 않는다.

관리자 전용 GET /api/admin/e2e-snapshot은 commerce-e2e 프로필의 테스트 코드에서만 제공한다.
키·세션·비밀번호·고객 정보는 반환하지 않는다. 프로세스 안의 providerCalls는 실제 외부 호출이
아닌 모의 제공자 메서드 호출 횟수다. 중복 멱등 요청은 추가 PG 호출이 없어야 한다.

## 실제 브라우저

checkout의 배송 정보는 가상 값으로 입력하고 1개를 결제한다. 주문 내역에서 신청하고
관리자로 다시 로그인해 returns.html에서 승인·검수·환불·재입고를 수행한다.
상품 6은 shipping.html 출고 후 반품 신청·승인·회수·검수를 수행한다.
환불/재입고 확인창 취소 시 변경이 없는지와 수락 후 저장 상태를 각각 확인한다.
확인 중인 거래는 payment-operations.html에서 복구 가능 시각 이후 명시적으로 복구한다.
구매자로 재로그인해 같은 주문을 조회하고 결과·후속 행동을 대조한다.

Chrome 확장 제어가 native confirm에서 멈추면 Safari 네이티브 앱으로 실제 화면과
확인창을 조작할 수 있다. 이번 검증은 Safari의 실제 취소/확인 버튼으로 완료했다.
스크롤 밖의 컨트롤은 첫 클릭이 스크롤만 수행할 수 있으므로 새 상태를 확인한 후 입력한다.
Safari textarea는 포커스 후 붙여넣기로 입력값이 표시되는지 확인한다.
사용할 수 있는 브라우저가 모두 막히면 해당 조작은 BLOCKED로 남긴다.
HTTP로 상태를 생성한 뒤 화면을 읽은 증거는 조회 검증으로만 분류한다.
가상 주소·전화번호라도 고객 응답 전체나 콜백의 결제 키를 증거에 복사하지 않는다.

## MySQL과 전체 회귀

격리 MySQL 8.4.10의 서로 다른 빈 DB를 RETURNS_TEST_DB_URL,
PAYMENT_OPERATIONS_TEST_DB_URL, INVENTORY_TEST_DB_URL, SUBMISSION_MIGRATION_TEST_DB_URL,
POLICY_FILE_MIGRATION_DB_URL로 지정해 실행한다. 테스트는 root/빈 비밀번호를 사용하므로
작업 전용 loopback 컨테이너만 지정한다. 기존 사용자 DB·볼륨은 사용하지 않는다.

```sh
./gradlew test --tests '*ReturnRequestIntegrationTest' --tests '*PaymentOperationsIntegrationTest' --tests '*InventoryIntegrationTest' --tests '*FlywayMigrationTest' --tests '*FilePolicyConsentMigrationTest'
node --test src/test/js/*.test.cjs
./scripts/harness check
```

이번 검증에 사용한 격리 실행 명령은 다음과 같다. 이름이 이미 있으면 새 작업 이름으로
바꾸고 기존 컨테이너를 재사용하거나 삭제하지 않는다. 준비 완료 후 생성한 빈 DB에서만 실행한다.

```sh
docker run -d --name nanacocoa-e2e-32 --tmpfs /var/lib/mysql:rw,noexec,nosuid,size=1g -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -p 127.0.0.1::3306 mysql:8.4.10
docker exec nanacocoa-e2e-32 mysqladmin ping --silent
docker exec nanacocoa-e2e-32 mysql -e 'CREATE DATABASE returns_e2e; CREATE DATABASE operations_e2e; CREATE DATABASE inventory_e2e; CREATE DATABASE submission_e2e; CREATE DATABASE policyfile_e2e;'
e2e_mysql_port=$(docker port nanacocoa-e2e-32 3306 | sed 's/.*://')
RETURNS_TEST_DB_URL="jdbc:mysql://127.0.0.1:$e2e_mysql_port/returns_e2e?useSSL=false&allowPublicKeyRetrieval=true" \
PAYMENT_OPERATIONS_TEST_DB_URL="jdbc:mysql://127.0.0.1:$e2e_mysql_port/operations_e2e?useSSL=false&allowPublicKeyRetrieval=true" \
INVENTORY_TEST_DB_URL="jdbc:mysql://127.0.0.1:$e2e_mysql_port/inventory_e2e?useSSL=false&allowPublicKeyRetrieval=true" \
SUBMISSION_MIGRATION_TEST_DB_URL="jdbc:mysql://127.0.0.1:$e2e_mysql_port/submission_e2e?useSSL=false&allowPublicKeyRetrieval=true" \
POLICY_FILE_MIGRATION_DB_URL="jdbc:mysql://127.0.0.1:$e2e_mysql_port/policyfile_e2e?useSSL=false&allowPublicKeyRetrieval=true" \
./gradlew test --rerun-tasks --tests '*ReturnRequestIntegrationTest' --tests '*PaymentOperationsIntegrationTest' --tests '*InventoryIntegrationTest' --tests '*FlywayMigrationTest' --tests '*FilePolicyConsentMigrationTest'
docker stop nanacocoa-e2e-32
docker rm nanacocoa-e2e-32
```

중지·삭제 대상은 이 작업이 생성한 컨테이너뿐이다. Docker 볼륨 삭제 명령은 사용하지 않는다.

MySQL 집중 실행 후 최종 check는 위 DB 환경 변수를 해제한 별도 실행에서 수행한다.
Compose smoke와 이 테스트 서버의 검증은 별개다. 테스트 서버를 HARNESS_APP_PORT로
지정해 운영 스택 smoke 완료로 취급하지 않는다.
