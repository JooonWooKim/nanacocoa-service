# 장애 디버깅

상태: 활성

가장 좁고 안전한 피드백 루프를 사용하고 코드를 변경하기 전에 증거를
보존합니다.

## 1. 선행 조건 진단

```sh
./scripts/harness doctor
./scripts/harness smoke
docker compose ps
```

`smoke`는 비운영 placeholder로 Compose를 검증하고 `127.0.0.1`만 조회합니다.
스택을 시작·중지·삭제하지 않습니다.

## 2. 제한된 증거 수집

```sh
docker compose logs --tail=200 app
./scripts/harness evidence
```

자격 증명, cookie, authorization header, 결제 키, 멱등성 키, 고객 데이터,
완전한 제공자 payload를 붙여 넣거나 저장하지 않습니다. 장애 산출물을
저장소에 추가하기 전에 비식별화합니다.

## 3. 계층 분류

- 시작·설정: 애플리케이션 프로필, 필수 환경, 의존 서비스 상태
- 데이터베이스: Flyway 순서·checksum, 스키마 검증, 연결 상태
- 인증: 세션 생성, principal 해석, 현재 회원 상태
- 결제: 주문 락, 멱등성 fingerprint, 저장된 상태 전이, 제공자 분류,
  보상·복구
- 이미지 저장소: 선택된 모드, 형식 검증, mock·실제 경계

[ARCHITECTURE.md](../../ARCHITECTURE.md)에서 요청과 데이터 경로를 확인합니다.
가장 집중된 기존 테스트로 재현하고 가능하면 수정 전에 회귀 테스트를
추가합니다.

## 4. 검증과 피드백

집중 테스트, 영향받는 엄격 테스트, `./scripts/harness check`,
`./scripts/harness evidence`를 실행합니다. 같은 실패 패턴이 반복되면 기계적
보호 장치를 추가하거나 가장 작은 기준 문서를 갱신합니다.
