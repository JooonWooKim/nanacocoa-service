# Nanacocoa 에이전트 지도

Nanacocoa는 정적 페이지, MySQL, Redis, Flyway, Toss Payments와 선택적 S3
이미지 저장소를 사용하는 Java 17 / Spring Boot 3.5 커머스 백엔드입니다.

## 먼저 읽을 문서

- 시스템 및 의존성 지도: [ARCHITECTURE.md](ARCHITECTURE.md)
- 문서 인덱스: [docs/README.md](docs/README.md)
- 로컬 환경 구성: [docs/runbooks/local-development.md](docs/runbooks/local-development.md)
- 테스트 전략: [docs/runbooks/testing.md](docs/runbooks/testing.md)
- 품질 상태: [docs/quality/QUALITY_SCORE.md](docs/quality/QUALITY_SCORE.md)
- 활성 실행 계획: [docs/exec-plans/active](docs/exec-plans/active)
- 알려진 기술부채: [docs/exec-plans/tech-debt-tracker.md](docs/exec-plans/tech-debt-tracker.md)

## 주요 명령

```sh
./scripts/harness help
./scripts/harness doctor
./scripts/harness check
./scripts/harness test
./scripts/harness eval
./scripts/harness smoke
./scripts/harness evidence
```

`check`는 로컬 CI 계약입니다. 문서화된 정확한 테스트 실패 기준선만
허용하며, 새로운 실패나 검토되지 않은 실패 해소가 발생하면 실패합니다.
`test`는 항상 Gradle을 엄격하게 실행하므로 기준선 부채가 해결될 때까지
실패 상태를 유지합니다.

## 아키텍처 불변식

- 웹 요청 흐름은 `Controller -> Service/Facade -> Repository`를 유지합니다.
- Controller는 요청 바인딩·검증, 하나의 애플리케이션 경계 호출, 응답 래핑만
  담당합니다. Repository 직접 접근, Entity 변경, 트랜잭션 관리는 금지합니다.
- Service와 Facade가 비즈니스 검증과 트랜잭션 조정을 담당합니다.
- Repository는 영속성 접근만 포함하고 `JpaRepository`를 확장합니다.
- 새로운 응답·오류 형식을 만들지 말고 `SuccessMessage`,
  `NanacocoaException`, `ErrorCode`, 전역 예외 처리기를 재사용합니다.
- 관련 없는 작업에서는 기존 `dto.reqeust` 패키지 철자를 유지합니다.
- 결제 상태 전이, 멱등성, 락, 보상, 복구는 집중 테스트가 필요한 고위험
  동작으로 취급합니다.
- 적용된 Flyway 마이그레이션을 수정하지 않습니다. 새 버전을 추가하고
  `./scripts/harness migrations --accept-new`로 등록합니다.

세부 내용은 [ARCHITECTURE.md](ARCHITECTURE.md)를 참고하고 기계적으로
강제되는 규칙은 `./scripts/harness architecture`로 확인합니다.

## 안전 원칙

- 변경되었거나 추적되지 않은 사용자 파일을 보존합니다. 현재 저장소에는
  최초 커밋이 없으므로 어떤 파일도 폐기 가능하다고 가정하지 않습니다.
- `.env`, 자격 증명, 결제 키, 세션 값, 고객 데이터를 출력하거나 커밋하지
  않습니다.
- 테스트에서 실제 Toss Payments나 S3를 호출하지 않습니다.
- 명시적 사용자 승인 없이 `docker compose down -v`, Docker 볼륨 삭제,
  배포, push, PR 생성·병합, sandbox·승인 설정 완화를 수행하지 않습니다.
- Gradle wrapper를 사용합니다. 효과와 호환 버전이 검증된 경우에만
  의존성을 추가합니다.

## 작업공간 격리

코드 변경 작업은 현재 작업 트리를 직접 수정하지 않고, 전용 Git worktree와
`codex/<작업명>` 브랜치를 생성하여 수행합니다. 검증 완료 후 변경 내용과
증거를 사용자에게 보고하며, 명시적 승인 없이 병합하거나 worktree를
삭제하지 않습니다.

## 계획과 완료 조건

여러 단계로 진행되거나 위험한 작업은
[docs/exec-plans/PLANS.md](docs/exec-plans/PLANS.md)를
`docs/exec-plans/active/`로 복사해 진행 중 갱신하고, 완료 후 증거와 함께
`completed/`로 이동합니다.

변경은 다음 조건을 모두 만족해야 완료됩니다.

1. 관련 테스트와 `./scripts/harness check`를 실행했습니다.
2. 실패를 PASS, FAIL, BLOCKED, SKIPPED, BASELINED 중 하나로 분류했습니다.
3. 아키텍처, 문서, 마이그레이션, 보안 검사가 계속 통과합니다.
4. 실제 동작과 문서가 일치합니다.
5. 회귀, 보안, 데이터 손실 위험 관점에서 변경 내용을 검토했습니다.
