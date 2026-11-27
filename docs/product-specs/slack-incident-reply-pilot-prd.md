# 주문·결제 장애 자료 실제 Slack 회신 파일럿 PRD

| 항목 | 내용 |
| --- | --- |
| 상태 | `feature/65` 로컬 구현 · 실제 회신 인수 BLOCKED |
| 우선순위 | P1 운영 자동화 |
| 기준 | 2026-11-27 원격 `main` `98486f6` · [PR #64](https://github.com/JooonWooKim/nanacocoa-service/pull/64) |
| 이슈·브랜치 | [이슈 #65](https://github.com/JooonWooKim/nanacocoa-service/issues/65) · `feature/65` → `origin/feature/65` |
| 대상 | 주문·결제 장애 대응 담당자 |

## 사용자 흐름

담당자가 승인된 전용 테스트 스레드에 합성 승인 미확정 장애의 해결 자료를 회신한다.
명시적 단발 명령으로 실행하고, 확인된 회신 번호 또는 보류·실패 사유를 확인한다.
기존 Slack 수신기와 작업자는 모의 모드를 유지한다. 실제 이벤트 수신, AI 분석과 운영
채널 연결은 후속이다.

| 작업 항목 | 구현 범위 | 인수 기준 |
| --- | --- | --- |
| 명시적 파일럿 | `slack-reply-pilot` 명령과 테스트 팀·채널·스레드 환경 설정 | 설정이 빠지거나 토큰의 팀·봇을 확인할 수 없으면 전송하지 않는다. |
| 실제 스레드 회신 | 기존 비식별 자료 생성과 회신 형식을 재사용하고 Slack의 고정 HTTPS 주소로 회신 | 합성 승인 미확정 사례만 회신한다. 저장된 거래 조회를 먼저 안내하며 고객 데이터·결제 키·로그가 없다. |
| 중복·실패 처리 | 독립 SQLite의 전송 시도·확정 상태와 Slack 메시지 시각 기록 | 같은 대상에 동시 실행하거나 재실행해도 추가 전송하지 않는다. 수락 여부가 모호하면 담당자가 Slack에서 확인한다. |
| 검증·인수 | 모의 HTTP 검증 및 별도로 승인된 테스트 스레드 한 건 인수 | 권한, 요청 제한 및 응답 유실을 자동화로 확인한다. 실제 대상이 미준비면 인수는 BLOCKED다. |

## 실행과 전송 계약

- `INCIDENT_SLACK_PILOT_BOT_TOKEN`, `INCIDENT_SLACK_PILOT_TEAM`,
  `INCIDENT_SLACK_PILOT_CHANNEL`, `INCIDENT_SLACK_PILOT_THREAD_TS`에 승인된 테스트
  대상을 지정한다. 전용 Slack 봇은 지정 채널의 멤버이며 `chat:write`를 사용한다.
  파일럿은 `auth.test` 응답의 팀과 봇을 확인한다. 담당자가 실행 전 실제 테스트 채널과
  원본 스레드를 승인한다. [Slack 인증 확인](https://docs.slack.dev/reference/methods/auth.test/)
- 명령은 사용자 메시지·로그·증거 파일을 입력받지 않는다. 현재 커밋과 고정된 합성 승인
  미확정 이벤트로 비식별 해결 자료를 생성하고, 커밋에 근거 경로가 있을 때만 회신한다.
  스레드 목적지와 고정 회신 문구만 Slack에 전달하고 링크·미디어 미리보기를 끈다.
  [Slack 회신 API](https://docs.slack.dev/reference/methods/chat.postMessage/)
- 팀·채널·원본 스레드를 키로 전송 상태를 임시 작업 산출물과 분리된 전용 파일에 기록한다.
  전송 전에 원자적으로 한 실행만 예약한다. 성공 결과를 저장하고, 발송 중 중단·응답
  유실·성공 저장 실패에서는 같은 예약을 다시 보내지 않는다. 명확한 인증 거부나 요청 제한은
  별도 사유로 나타낸다. 파일럿 명령에는 자동 재시도가 없다.
- 결과에는 상태와 고정 이유, 시도 여부 및 확인된 회신 시각만 표시한다. Slack 응답 원문과
  자격 증명은 결과 및 상태 파일에 보관하지 않는다.

실제 명령은 `./scripts/harness slack-reply-pilot`이다. 수신과 모의 작업자의 상태와
분리한 `.slack-reply-pilot/receipts.sqlite3`를 작업 트리에서 사용한다. 같은 테스트
스레드에는 전송 시도를 한 번만 예약한다. 확인된 거부나 요청 제한도 같은 예약에
기록하므로, 후속 검증은 담당자가 원인과 스레드 상태를 확인한 뒤 계획한다.

## 인수 검증

- 모의 HTTP에서 올바른 스레드, 미설정·팀 불일치·잘못된 봇, 권한 오류, 요청 제한, 전송
  성공 후 재실행, 동시 실행과 전달 미확정을 확인한다.
- 기존 모의 Slack 수신·회신과 전체 하네스를 검증한다.
- 외부 전송 인수는 승인된 실제 테스트 대상에서 합성 회신 한 건을 확인하고 링크 또는 회신
  시각과 결과를 기록한다. 운영 채널 설정과 앱 설치는 별도 승인 범위다.

## 로컬 검증 결과

모의 HTTP 10개, 전체 Python 73개와 H2 Java 268개가 통과했다. 전체 하네스는
기존 아키텍처·보안 예외가 유지된 `BASELINED`다. 테스트 대상과 전용 봇 토큰이
미설정이라 실제 Slack 회신은 `PILOT_CONFIGURATION_UNAVAILABLE`, 전송 0회였다.

- [준비·구현 실행 기록](../exec-plans/completed/slack-incident-reply-pilot.md)
- [모의 Slack 접수와 회신 PRD](slack-incident-intake-prd.md)
- [실행 방법](../runbooks/incident-debugging.md)
- [커머스 출시 PRD](commerce-launch-prd.md)
