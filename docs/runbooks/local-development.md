# 로컬 개발

상태: 현재 스크립트·설정 기준 검증됨

## 선행 조건

- Java 17
- 저장소 Gradle wrapper
- 전체 스택 실행용 Docker와 Compose plugin
- 로컬 비밀값을 명시적으로 초기화할 때만 필요한 OpenSSL

비밀값 내용을 읽지 않고 진단합니다.

```sh
./scripts/harness doctor
```

## Docker 없는 테스트

집중 단위·Service 테스트와 H2 기반 테스트 프로필은 `.env`가 필요하지 않습니다.

```sh
./scripts/harness test-unit
./scripts/harness test-baseline
```

NC-TEST-001이 남아 있어 엄격한 `./scripts/harness test`는 현재 실패합니다.
기준선을 인식하는 명령을 엄격 테스트 통과로 해석하지 않습니다.

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
파일 권한을 설정합니다. `.env`를 출력하거나 커밋하지 않습니다. 템플릿은
의도적으로 `TOSS_PAYMENTS_BASE_URL`을 연결할 수 없는 loopback으로 설정하고
결제 reconciliation을 비활성화합니다. 하네스 전용 스택에서는 해당 값을
바꾸거나 결제 endpoint를 실행하지 않습니다. 실제 제공자는 명시적 배포
결정과 자격 증명이 필요합니다. `TOSS_PAYMENTS_CLIENT_KEY`도 기본값이 비어
있어 checkout은 주문 생성 전에 결제 설정 오류를 안내합니다. 결제 활성화에는
서로 매칭되는 클라이언트·시크릿 키가 필요합니다.

하네스는 기존 스택을 중지하거나 볼륨을 삭제하지 않습니다.
[README.md](../../README.md)의 운영자 소유 종료 절차를 따르며 일상 자동화에
볼륨 삭제를 추가하지 않습니다.

## 설정

운영 유사 MySQL 설정은 `application.yml`과 `compose.yaml`의 환경 변수에서
가져옵니다. 테스트는 `test` 프로필과 H2를 사용하고, 일반 애플리케이션
테스트에서는 Flyway를 비활성화하며, 전용 Flyway·JPA 검증 테스트로
마이그레이션 호환성을 확인합니다. 이 마이그레이션 테스트는 H2 MySQL 모드를
사용하며 실제 MySQL 8.4 자동화는 NC-DATA-002로 남아 있습니다.
