# 로컬 개발

상태: 현재 스크립트·설정 기준 검증됨

## 선행 조건

- Java 17
- 저장소 Gradle wrapper
- 전체 스택 실행용 Docker와 Compose plugin
- 로컬 비밀값을 명시적으로 초기화할 때만 필요한 OpenSSL

신규 회원가입은 승인된 정책 게시와 `POLICY_SIGNUP_ENABLED=true`가 모두 필요합니다.
기본 false에서는 기존 회원 로그인·조회만 유지합니다.
[정책 게시·가입 운영 절차](service-policy.md)를 참고합니다.

비밀값 내용을 읽지 않고 진단합니다.

```sh
./scripts/setup-release-config.sh
./scripts/harness doctor
```

공통 템플릿은 `deploy/templates/`에 있습니다. 초기화는 누락된 실행 파일만
만듭니다. 기존 파일의 내용이 다르면 경로를 보고하므로 공통 설정으로 검증할 때는
깨끗한 체크아웃에서 실행합니다.

## Docker 없는 테스트

집중 단위·Service 테스트와 H2 기반 테스트 프로필은 `.env`가 필요하지 않습니다.

```sh
./scripts/harness test-unit
./scripts/harness test-baseline
```

NC-TEST-001은 feature/30에서 해소했으며 엄격 테스트 191개 실패·skip 0개를 확인했습니다.
main 반영은 별도이며, check에 남은 아키텍처·보안 BASELINED를 제품 테스트 실패와 구분합니다.

## 전체 로컬 스택

`.env.example`을 검토한 뒤 로컬 전용 비밀값을 명시적으로 초기화합니다.

```sh
./scripts/init-env.sh
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
./scripts/harness smoke
```

초기화 스크립트는 기존 값을 보존하고 요청한 경우에만 `.env`를 만들며 로컬
파일 권한을 설정합니다. `.env`를 출력하거나 커밋하지 않습니다. 기본으로
`TOSS_PAYMENTS_ENABLED=false`와 `PAYMENT_RECONCILIATION_ENABLED=false`를 사용합니다.
checkout은 주문 생성과 SDK 로딩 전에 결제 불가를 안내합니다.
결제 테스트와 운영 설정은 [결제 환경 운영 절차](payment-environment.md)를 참고합니다.

하네스는 기존 스택을 중지하거나 볼륨을 삭제하지 않습니다.
[README.md](../../README.md)의 운영자 소유 종료 절차를 따르며 일상 자동화에
볼륨 삭제를 추가하지 않습니다.

## 설정

운영 유사 MySQL 설정은 `application.yml`과 `compose.yaml`의 환경 변수에서
가져옵니다. 테스트는 `test` 프로필과 H2를 사용하고, 일반 애플리케이션
테스트에서는 Flyway를 비활성화하며, 전용 Flyway·JPA 검증 테스트로
마이그레이션 호환성을 확인합니다. 이 마이그레이션 테스트는 H2 MySQL 모드를
사용하며 실제 MySQL 8.4 자동화는 NC-DATA-002로 남아 있습니다.
