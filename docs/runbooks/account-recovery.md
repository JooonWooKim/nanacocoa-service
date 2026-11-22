# 이메일 비밀번호 복구 운영·검증

상태: 로컬 구현·기술 검증 완료 · 실제 발송·운영 활성화·배포 미수행

## 구성과 활성화 경계

[PRD](../product-specs/account-recovery-prd.md)의 RECOVERY-01~10을 기준으로 한다.
공개 기본 설정은 `src/main/resources/password-recovery.properties`이며 개인
application.yml·.env를 수정하지 않는다. 기본 복구·SMTP 활성화는 모두 false다.

| 환경 변수 | 의미 | 기본값 |
| --- | --- | --- |
| `RECOVERY_ENABLED` | 요청·재설정 허용 | false |
| `RECOVERY_SMTP_ENABLED` | SMTP 실발송 허용 | false |
| `RECOVERY_BASE_URL` | 신뢰된 HTTPS 사이트 기본 URL | 비어 있음 |
| `RECOVERY_SMTP_HOST`, `RECOVERY_SMTP_PORT` | 공급자 SMTP 호스트·포트 | 비어 있음 / 587 |
| `RECOVERY_SMTP_USERNAME`, `RECOVERY_SMTP_PASSWORD` | 배포 환경의 인증 정보 | 비어 있음 |
| `RECOVERY_SMTP_FROM` | 공급자에서 승인된 발신 주소 | 비어 있음 |

SMTP는 STARTTLS를 필수로 사용한다. 연결 3초·읽기/쓰기 5초 제한을 둔다. 공급자
실패·타임아웃은 접수 응답으로 전파하지 않고 `recovery.mail.failed`의 비식별 예외
종류로 계수한다. 성공은 `recovery.mail.sent`다. Actuator의 공개 노출을 늘리지 않는다.
메일·토큰·비밀번호·세션 값·SMTP 예외 원문을 로그나 증거로 내보내지 않는다.

`recovery.mode=mock`은 test 프로필에서만 허용한다. 메모리 수신함은 최대 100개이며
운영 API가 없다. `recovery.allow-local-http`와 `recovery.smtp.allow-plaintext-loopback`은
test 프로필의 localhost/127.0.0.1에만 적용된다. 실제 공급자 연결에는 사용하지 않는다.
토큰 수명은 `recovery.token-lifetime=30m`, 비동기 실행 여유는
`recovery.queue-capacity=32`다. 원문 토큰은 발송 작업 메모리에서만 사용한다.

## 접수·재시도·제한

202는 배달 확인이 아니다. 계정 조회·토큰 생성·발송은 최대 2개 스레드의 제한된
비동기 작업으로 처리한다. 접수 큐가 포화되면 계정 조회 전에 503을 반환한다.
IP별 1시간 20회는 429, 이메일별 60초 간격·1시간 5회 제한은 동일한 202다.
DB 기록과 잠금으로 다중 인스턴스·재시작에서도 제한을 공유한다. forwarded 헤더를
클라이언트 IP로 사용하지 않으므로 프록시 뒤에서는 명시적 신뢰 구성을 별도 검토한다.
오래된 제한 기록은 1시간마다 정리하며 최근 2시간 기록은 유지한다.

SMTP 실패·접수 후 서버 종료·응답 유실 때 자동 재발송/재설정을 수행하지 않는다.
사용자는 제한 시간이 지난 뒤 직접 다시 요청한다. 새 발급은 이전 미사용 링크를
철회한다. 늦게 도착한 이전 링크는 사용할 수 없다. 비밀번호 변경이 성공했다면
모든 기존 로그인을 만료시키며 장바구니·주소·주문·결제를 삭제하거나 재실행하지 않는다.

## 이행과 배포 전 조건

V13은 회원 인증 세대 기본 0, 복구 토큰 해시·발급 credential 해시·만료/소비 상태와
요청 제한을 추가한다. V1~V12를 수정하지 않는다. 토큰 재설정·발급·일반 비밀번호
변경·로그인은 회원 잠금으로 직렬화한다. 재설정은 비밀번호·토큰 소비·세대 증가를
같은 트랜잭션에서 커밋한다. 세션은 로그인 시 세대를 고정하고 이후 DB와 비교한다.

세대 정보가 없는 구버전 세션은 재로그인이 필요하다. 활성화 전 모든 인스턴스가
새 세션 검증을 실행해야 한다. 구버전 애플리케이션과 혼합 운영하면서 복구를 켜지
않는다. 인증 세대 조회 실패는 503으로 차단한다. 변경 전 시작한 요청은 소급 취소하지
않으며 결제 결과는 재로그인 후 저장된 주문을 조회해 확인한다.

실제 공급자·발신 도메인·HTTPS 주소·비밀 설정·전 인스턴스 배포·운영 인수 확인과
명시적 승인이 있어야 실발송을 활성화한다. 이번 작업에서는 이 조건을 완료하지 않는다.

## 격리 검증

```sh
./gradlew test --tests '*Recovery*Test'
node src/test/js/password-recovery.test.cjs
./gradlew recoveryE2e
python3 -B src/test/python/recovery_e2e_http.py
./scripts/harness check
```

`recoveryE2e`는 loopback 18086·메모리 H2·합성 회원·모의 수신함만 사용한다.
고정 합성 계정과 `/api/auth/recovery-fixture`는 test classpath 전용이며 운영 jar에
포함되지 않는다. fixture 수신함을 실제 서비스나 공용 네트워크로 제공하지 않는다.

`RECOVERY_TEST_DB_URL`은 격리 MySQL 8.4 DB, `RECOVERY_MIGRATION_TEST_DB_URL`은
별도의 빈 이행 DB만 지정한다. 운영 DB나 기존 비어 있지 않은 DB를 지정하지 않는다.
SMTP 테스트는 JVM의 loopback 가상 수신기로 정상·거절·타임아웃을 재현한다.
브라우저 확인은 자동화·HTTP 통과와 구분하며 도구 장애는 BLOCKED로 기록한다.

같은 테스트 선택에 DB 환경 변수만 바꾸면 Gradle이 이전 결과를 재사용할 수 있으므로
DB를 바꿀 때는 `--rerun-tasks`를 함께 지정한다. 이행 DB는 재실행마다 새 빈 DB를 사용한다.
`RECOVERY_E2E_DB_URL`은 loopback 격리 MySQL runtime DB만 허용한다. 해당 설정의
`recoveryE2e`에서 `python3 -B src/test/python/recovery_restart_http.py`를 실행하면 READY에서
대기한다. MySQL을 유지한 채 이 작업의 Java 서버만 재시작한 다음 Enter를 입력해
비밀번호·토큰 소비·로그인 차단·요청 제한의 실제 프로세스 재시작 보존을 검사한다.
토큰과 쿠키는 Python 프로세스 메모리에만 유지하며 stdout에는 결과만 출력한다.
