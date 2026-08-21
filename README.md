# Nanacocoa 공개 소스 스냅샷

> [!IMPORTANT]
> 이 저장소는 코드 열람을 위한 공개용 **비실행 스냅샷**입니다.
> Spring Boot 런타임 설정, 테스트 설정, Docker/Compose 설정 및 환경 변수
> 템플릿은 비공개로 관리되며 이 저장소에 포함되지 않습니다. 따라서 이
> 저장소를 clone한 상태만으로는 애플리케이션 실행, 전체 테스트 또는 배포가
> 불가능합니다.

실제 비밀값, 결제 키, AWS 자격 증명, 데이터베이스 비밀번호 및 운영 환경
설정은 공개 Git 이력에 저장하지 않습니다. 아래 문서는 내부 프로젝트의
구조와 운영 계약을 설명하기 위한 참고 자료이며, 누락된 비공개 설정 없이는
명령이 완전하게 동작하지 않을 수 있습니다.

## 기존 Docker 배포 문서 (내부 참고)

Spring Boot 3.5, MySQL, Redis를 단일 Docker Compose 서버에서 실행합니다.
Spring Boot와 MySQL은 각각 호스트의 loopback 주소에만 바인딩되며, Redis는
호스트에 포트를 공개하지 않습니다.

## 개발 하네스

저장소 구조, 로컬 환경, 아키텍처, 문서, 마이그레이션, 보안, 테스트와
핵심 위험 평가를 하나의 진입점에서 실행할 수 있습니다.

```bash
./scripts/harness help
./scripts/harness doctor
./scripts/harness check
```

`check`는 로컬 CI 계약입니다. 현재 알려진 테스트 실패는 `BASELINED`로
명확히 표시하고, 실패 집합이 늘거나 검토 없이 달라지면 실패합니다.
엄격한 전체 테스트는 `./scripts/harness test`로 실행합니다.

에이전트와 개발자를 위한 구조·규칙 지도는
[AGENTS.md](AGENTS.md)와 [ARCHITECTURE.md](ARCHITECTURE.md), 세부 운영 지식은
[docs/README.md](docs/README.md)를 참고하세요.

## 사전 준비

- Docker Engine과 Docker Compose 플러그인
- OpenSSL
- 외부 HTTPS 요청을 `127.0.0.1:8080`으로 전달할 Nginx 또는 Caddy
- 배포 서버에만 보관할 `.env` 파일

## 환경 변수 설정

```bash
./scripts/init-env.sh
```

스크립트는 `.env`가 없으면 `.env.example`에서 생성하고, 비어 있는 필수 비밀값을 각각 안전한 난수로 채웁니다. 이미 설정된 값은 변경하지 않으며 `.env` 권한을 `600`으로 제한합니다.

필요하면 실행 후 `.env`에서 아래 값을 직접 변경할 수 있습니다.

- `DB_USERNAME`, `DB_PASSWORD`: 애플리케이션 전용 MySQL 계정
- `MYSQL_ROOT_PASSWORD`: MySQL 관리 계정 비밀번호
- `REDIS_PASSWORD`: Redis 인증 비밀번호
- `JWT_SECRET`: 최소 32바이트 이상의 무작위 JWT 서명 키

`.env`는 Git에서 제외되며 커밋하면 안 됩니다. `docker compose` 실행 전에 초기화 스크립트를 한 번 실행해야 합니다.

S3를 사용하는 경우 `PRODUCT_IMAGE_STORAGE_MODE=s3`로 변경하고 버킷, 리전,
공개 URL 및 AWS 자격 증명을 입력합니다. `.env.example`은 자동 결제 복구를
비활성화하고 Toss 주소를 연결 불가능한 로컬 주소로 둔 안전한 개발
기본값입니다. 결제 기능을 사용하는 실제 배포에서만
`TOSS_PAYMENTS_BASE_URL`, `TOSS_PAYMENTS_CLIENT_KEY`, `TOSS_PAYMENTS_SECRET_KEY`,
`PAYMENT_RECONCILIATION_ENABLED`를 함께 명시적으로 설정합니다.
브라우저에는 공개용 클라이언트 키만 전달되며, 서로 매칭되는 API 개별 연동
클라이언트 키와 시크릿 키를 사용해야 합니다. 시크릿 키는 서버 환경 변수
밖으로 노출하지 않습니다.

## 실행

먼저 최종 Compose 설정이 유효한지 확인한 후 전체 스택을 빌드하고 시작합니다.

```bash
./scripts/init-env.sh
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
```

애플리케이션 상태를 확인합니다.

```bash
curl --fail http://127.0.0.1:8080/actuator/health
docker compose logs --tail=200 app
```

앱 시작 시 Flyway가 `src/main/resources/db/migration`의 마이그레이션을 MySQL에 자동 적용합니다. `schema-mysql.sql`은 컨테이너 초기화 스크립트로 별도 실행하지 않습니다.

## 운영 명령

```bash
# 이미지 재빌드 및 재배포
docker compose up -d --build --wait

# 전체 로그 확인
docker compose logs -f

# 컨테이너를 내리되 MySQL 데이터는 보존
docker compose down
```

MySQL 데이터는 `mysql-data` named volume에 저장됩니다. `docker compose down -v`는 이 볼륨과 데이터까지 삭제하므로 초기화가 명확히 필요한 경우에만 사용합니다. Redis는 결제 분산 락 전용이며 재시작 시 락 상태가 초기화되도록 영속화하지 않습니다.

## 네트워크와 헬스체크

- `app`: `127.0.0.1:${APP_PORT:-8080}`에서만 접근 가능
- `mysql`: Compose 내부의 `mysql:3306`과 호스트의 `127.0.0.1:${MYSQL_PORT:-3306}`에서 접근
- `redis`: Compose 내부의 `redis:6379`으로만 접근하며 비밀번호 필수
- 앱은 MySQL과 Redis 헬스체크가 모두 성공한 후 시작
- 앱 헬스체크는 `/actuator/health` 사용
