# 관측성

상태: 부분 적용, NC-OBS-001

## 사용 가능한 신호

- Spring Boot Actuator가 health와 info 설정을 제공합니다.
- Compose가 app, MySQL, Redis health check를 정의합니다.
- `docker compose logs --tail=200 app`으로 제한된 애플리케이션 로그를 봅니다.
- `./scripts/harness smoke`가 Compose를 검증하고 로컬 Actuator 상태를 조회합니다.
- `./scripts/harness evidence`는 정확한 저장소 소스·실행 비트 fingerprint와
  하나의 조정된 check 실행 ID가 일치하는 상태만 수집합니다. 오래되었거나
  서로 다른 실행의 상태는 제외합니다.
- Gradle HTML·XML과 하네스 TSV·status 파일은 기계 판독 가능한 증거입니다.

## 에이전트 디버깅 계약

1. `doctor`와 `smoke`를 실행합니다.
2. 자격 증명·고객·제공자 payload 없이 제한된 로그를 보존합니다.
3. `ARCHITECTURE.md`의 계층 지도를 따릅니다.
4. 가장 좁고 이름이 있는 평가·테스트로 재현합니다.
5. 구현하고 다시 실행한 뒤 `evidence`를 수집합니다.

## 보류된 계측

검증된 correlation ID 정책, 구조화 로그 schema, OpenTelemetry tracing,
로컬 metrics·trace 스택이 없습니다. 지금 무거운 스택을 추가하는 것은
추측성 작업이므로 다음 상황에서 재검토합니다.

- 운영 유사 장애를 현재 제한된 신호로 국소화할 수 없을 때
- 시작·사용자 흐름 지연 시간이 인수 조건이 될 때
- 여러 Service에 프로세스 간 상관관계가 필요할 때
- 비식별화 테스트와 생명주기 책임이 정의될 때

Actuator health가 구성되어 있지만 `/api/health` 통합 기대값은 현재 실패합니다.
하네스 작업 중 검토되지 않은 두 번째 상태 확인 surface를 추가하지 말고
NC-TEST-001에서 계약을 해결합니다.
