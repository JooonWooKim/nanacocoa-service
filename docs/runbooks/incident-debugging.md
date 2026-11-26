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

### 주문·결제 해결 자료

화면의 요청 번호를 사용해 승인된 로그 조회 경로에서 `INCIDENT_EVENT` 항목을
찾습니다. 자동 복구는 같은 실행 번호의 배치와 개별 처리 항목을 함께 수집합니다.
로그 접두사 뒤 JSON 객체만 검토해 `events`에 넣습니다. 배포 또는 실행 기록에서
확인한 40자리 커밋을 `candidateCommit`에 적고 같은 커밋을 체크아웃합니다.
수정 후보를 대조할 때는 담당자가 검토한 저장소 상대 경로를 `changedPaths` 배열에
추가합니다. 이동된 파일은 이전 경로와 새 경로를 모두 넣습니다.

```sh
./scripts/harness incident-evidence --input incident.json --request-id 155dd4fe-3483-4fde-8930-fcb520b705f1
./scripts/harness incident-evidence --input incident.json --run-id ed58be6b-ccb1-4fde-b01f-1cf4efd2516c
```

- `PASS`는 해당 번호의 자료 생성을 뜻합니다. 실패 또는 보류 단계, 관련 코드·절차,
  권장 검사를 확인하고 격리 환경의 실제 검사 결과를 별도로 남깁니다.
- `BLOCKED`는 후보 커밋이나 같은 번호의 실패·보류 증거를 확인해야 할 때 표시됩니다.
- `FAIL`은 입력의 형식·필드·값을 점검하라는 신호입니다. 명령은 오류의 원본 값을 출력하지 않습니다.

미확정 결제는 저장된 거래 상태를 먼저 조회합니다. 격리 검증과 운영 복구는 각각 담당자가
기존 절차로 실행합니다. [입력 계약](../product-specs/order-payment-observability-prd.md)
· [미확정 결제 운영 절차](payment-environment.md)

### 모의 Slack 접수와 회신

Python 3.9 이상으로 저장소에서 아래 명령을 실행하면 임시 루프백 포트와 SQLite에서
서명된 합성 이벤트를 접수하고 별도 프로세스로 스레드 회신을 확인합니다.

```sh
python3 -B src/test/python/test_slack_incident_intake.py -v
```

직접 실행할 때는 수신기와 작업자가 같은 로컬 SQLite 파일을 사용합니다.
아래 값은 모의 이벤트에만 쓰는 합성 설정 예시입니다.

```sh
mkdir -p build/slack-incident
env INCIDENT_SLACK_MODE=mock INCIDENT_SLACK_SIGNING_SECRET=synthetic-local-signing-secret \
  INCIDENT_SLACK_APP=A123 INCIDENT_SLACK_TEAM=T123 INCIDENT_SLACK_CHANNEL=C123 \
  INCIDENT_SLACK_USER=U123 INCIDENT_SLACK_BOT=U999 \
  python3 -B scripts/slack_incident_intake.py --database build/slack-incident/queue.db serve
```

수신기는 `127.0.0.1:18090`의 `POST /integrations/slack/events`를 사용합니다.
서명된 합성 이벤트와 로컬 비식별 증거를 준비합니다. 이벤트 예시는 위 테스트의
`envelope`와 `post`에 있으며, 증거는 기존 `incident-evidence` JSON 계약을 따릅니다.
별도 터미널에서 아래 명령으로 큐를 처리합니다. 계속 처리하려면 `--once`를 생략합니다.

```sh
python3 -B scripts/slack_incident_intake.py --database build/slack-incident/queue.db \
  worker --evidence build/slack-incident/incident.json --once
sqlite3 -readonly build/slack-incident/queue.db \
  'SELECT id, state, evidence_status, delivery_reason FROM jobs;'
sqlite3 -readonly build/slack-incident/queue.db \
  'SELECT channel, thread_ts, text FROM mock_replies;'
```

`jobs.evidence_status`는 자료 생성 결과이고 `state`는 처리·발송 상태입니다.
`BLOCKED / DELIVERY_UNKNOWN`은 응답이 끊겼거나 전송 중 작업자가 종료돼 담당자가 원래
스레드를 확인해야 하는 상태입니다. 중단된 발송은 60초 처리 기한 뒤 다음 큐 검사에서
판정합니다. 확실히 거절된 비율 제한은 1~60초 대기를 지켜 최초 포함 최대 세 번
시도합니다. 작업이 끝나면 수신기와 계속 실행한 작업자를 `Ctrl+C`로 종료합니다.
[Slack 접수 PRD](../product-specs/slack-incident-intake-prd.md)

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
