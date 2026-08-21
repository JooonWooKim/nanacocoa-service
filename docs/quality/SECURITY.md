# 보안

상태: 활성

## 경계

- 세션 인증은 HTTP 세션에 저장된 Spring Security context로 설정합니다.
- Service·Facade는 principal이 없으면 거부하고, 인가가 바뀔 수 있는 곳에서는
  현재 회원 상태를 다시 조회합니다.
- 정적·인증·상품 route와 Actuator health·info는 명시적으로 허용하며 나머지
  route는 `SecurityConfig`에 따라 인증이 필요합니다.
- Toss 시크릿 키와 S3 자격 증명은 환경 설정으로만 주입합니다. 브라우저에
  필요한 Toss 클라이언트 키는 인증된 설정 API가 해당 값만 반환합니다.
- 자동화 테스트에서는 실제 외부 제공자를 호출할 수 없습니다.

## 저장소 보호 장치

`./scripts/harness security`는 저장소 소유 텍스트와 자동화에서 다음을
검사합니다.

- 일반적인 AWS·실제 제공자·개인 키 자격 증명 패턴
- `.env.example`의 placeholder가 아닌 민감값
- 파괴적인 Docker 볼륨·광범위 파일 시스템·hard reset 명령
- 자격 증명·결제 식별자의 의심스러운 로그 출력
- `harness/baselines/security-exceptions.txt`에 있는 정확한 검토 완료
  보안·데이터·제공자 기본값 위험

이 검사는 의도적으로 `.env`를 읽지 않습니다. 경량 검사는 심층 방어 수단이며
성숙한 hosted secret scanner를 대체하지 않습니다.

## 검토된 미해결 위험

다음 위험이 남아 있는 동안 보안 게이트는 PASS만이 아니라 BASELINED를
보고합니다.

- NC-SEC-001: 브라우저 세션 인증을 사용하지만 CSRF가 비활성화되어 있습니다.
- NC-SEC-002: 로그아웃이 POST뿐 아니라 GET도 허용합니다.
- NC-SEC-003: 로그인 성공 시 명시적 세션 ID 회전이 없습니다.
- NC-DATA-001: Flyway가 비어 있지 않은 스키마를 버전 1로 기준화할 수 있습니다.
- NC-OPS-001/002: 애플리케이션과 Compose 기본값이 실제 Toss URL과 자동
  reconciliation을 함께 사용할 수 있습니다.
- NC-OPS-003: 기본 설정과 debug profile 중 어디에 둘지 결정하기 전까지
  MySQL을 의도적으로 host loopback에 공개합니다.

`.env.example`은 새 로컬 환경에서 연결할 수 없는 loopback Toss URL을 사용하고
reconciliation을 비활성화합니다. 이는 로컬 위험을 줄이지만 안전하지 않은
애플리케이션·Compose 기본값을 해결하거나 기존 `.env`를 변경하지 않습니다.
Redis 시작 과정은 비밀번호를 권한 600의 tmpfs 설정 파일에 쓰고 해당 파일
경로만으로 Redis를 실행하므로 비밀값이 컨테이너 프로세스 인자에 직접
들어가지 않습니다.

## 필수 동작

- 비밀번호, token, 세션·cookie 값, 결제 키, 멱등성 키, 제공자 payload, 고객
  상세 정보를 로그에 남기지 않습니다.
- 결제 설정 응답과 정적 JavaScript에 Toss 시크릿 키를 포함하지 않습니다.
- 민감 입력을 되풀이하지 않으면서도 오류를 유용하게 유지합니다.
- HTTP 경계에서 요청 데이터를, Service·Entity에서 비즈니스 불변식을
  검증합니다.
- 테스트 통과만을 위해 CSRF·인증 규칙을 약화하거나 route를 허용하지 않습니다.
- 자동 검증 중 실제 제공자 기본값으로 결제 가능한 스택을 시작하지 않습니다.
- 인증·상태 확인 기준선 실패는 의도한 계약이 명시적으로 조정될 때까지
  NC-TEST-001로 취급합니다.
