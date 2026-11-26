# Slack 장애 접수와 해결 자료 회신 구현

상태: 완료

## 목표

[이슈 #59](https://github.com/JooonWooKim/nanacocoa-service/issues/59)의 서명된 합성 요청을 접수해 같은 스레드로 비식별 해결 자료를 모의 회신한다.

## 하지 않는 일

실제 Slack 연결, 운영 로그 조회, AI 분석, 배포는 후속 범위다.

## 현재 증거

[구현 PRD](../../product-specs/slack-incident-intake-prd.md)와 기존 `incident-evidence`의 로컬 입력 계약을 대조했다.

## 결정

별도 Python 프로세스와 SQLite를 사용한다. 수신과 작업 처리를 나누고, 발송 결과가 불명확한 경우 원래 스레드 확인으로 넘긴다.

## 작업 분해

- [x] 기존 변경 확인과 백업
- [x] 서명 수신·작업 상태·증거·모의 회신 구현
- [x] 통합 검증과 출시 PRD 갱신

## 진행 상황

- 2026-11-26: `feature/59`의 기존 문서 19개를 백업하고 SHA-256을 기록했다.
- 2026-11-26: 루프백 수신기, 내구성 있는 SQLite 큐, 기존 비식별 자료와 모의 스레드 회신을 연결했다.
- 2026-11-26: 합성 서명 HTTP와 별도 수신기·작업자 프로세스로 동작을 검증하고 출시 PRD와 실행 절차를 갱신했다. 기존 문서 16개 해시가 일치했고 함께 편집한 3개는 구현 관련 추가분을 대조했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status -sb` | PASS | `feature/59`와 업스트림을 확인했다. |
| `python3 -B src/test/python/test_slack_incident_intake.py -v` | PASS | HTTP·작업자 12개. 승인·시간·중복 접수, 저장 실패, 증거 범위, 재시작과 전달 장애 검증. |
| 수신기·작업자 CLI | PASS | 별도 실행 후 모의 이벤트 HTTP 200, 처리 `SENT`, 해결 자료 `PASS`, 원래 스레드 일치. |
| `python3 -B -m unittest discover -s src/test/python -v` | PASS | 전체 Python 35개. |
| `./scripts/harness check` | BASELINED | H2 Java 268개·평가 63개와 문서·마이그레이션 검사 통과. 기존 구조·보안 예외 유지. |
| `./scripts/harness docs`, `git diff --check` | PASS | 링크, 생성 참조와 공백 검사. |
| Compose 런타임 확인·실제 Slack 연동 | SKIPPED | 기존 Compose 앱 기동과 실제 Slack 채널 회신은 격리 범위 밖이다. |

## 위험과 롤백

쇼핑 앱과 결제 DB를 변경하지 않고 로컬 모의 서비스로 격리한다.

## 미해결 항목

운영 연결과 운영 담당자 인수는 후속이다.

## 완료 조건

HTTP 접수부터 모의 회신까지 정상, 권한 거부, 중복, 재시작과 전달 장애를 검증하고 하네스 결과를 기록한다.
