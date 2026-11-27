# 장애 자료 실제 Slack 회신 파일럿

상태: 완료

## 목표

[이슈 #65](https://github.com/JooonWooKim/nanacocoa-service/issues/65)의 합성 결제 장애 요약을 승인된 Slack 테스트 스레드에 회신할 수 있게 한다.

## 하지 않는 일

실제 이벤트 수신, 운영 채널 연결, AI 분석과 배포는 후속이다.

## 현재 증거

[모의 Slack 구현 PRD](../../product-specs/slack-incident-intake-prd.md)와 기존 회신 형식을 확인했다. 기준은 원격 main `98486f6`다.

## 결정

별도 단발 명령이 고정 합성 이벤트만 사용하며 전송 전후 상태를 전용 SQLite 파일에 기록한다.

## 작업 분해

- [x] 이슈·추적 브랜치 준비 및 기존 상태 확인
- [x] 실제 회신 어댑터와 모의 검증 구현
- [x] 출시 PRD 갱신과 인수 상태 확인

## 진행 상황

- 2026-11-27: `feature/65`로 전환했으며 기존 파일 21개의 SHA-256이 일치했다. 함께 편집한 4개는 추가분을 대조했고 나머지 17개 해시는 갱신 뒤에도 일치했다.
- 2026-11-27: 실제 Slack 어댑터와 전송 영수증을 구현했다. 모의 HTTP에서 재실행과 동시 실행, 응답 유실 시 중복 차단을 확인했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status -sb` | PASS | 추적 브랜치와 기존 변경을 확인했다. |
| 원격 main Quality | PASS | [PR #64 병합 후 검사](https://github.com/JooonWooKim/nanacocoa-service/actions/runs/45920970770) 통과. |
| `python3 -B src/test/python/test_slack_reply_pilot.py -v` | PASS | 모의 HTTP 10개. 고정 대상과 합성 요약, 권한·제한, 동시 실행·전송 불확실성 확인. |
| `python3 -B -m unittest discover -s src/test/python` | PASS | 모의 Slack 수신을 포함한 Python 73개. |
| `./scripts/harness check` | BASELINED | H2 Java 268개와 위험 평가 63개 통과. 기존 아키텍처·보안 예외 유지. |
| `./scripts/harness docs`, `git diff --check` | PASS | 문서 링크·생성 참조와 공백 검사. |
| `./scripts/harness slack-reply-pilot` | BLOCKED | 승인된 테스트 채널과 스레드 설정 및 전용 봇 토큰 미설정. 실제 회신 0회. |

## 위험과 롤백

성공 확인이 불확실한 전송을 재실행하지 않도록 전송 진입 단계부터 저장한다.

## 미해결 항목

실제 전송 인수는 승인된 대상이 준비되면 별도로 진행한다.

## 완료 조건

합성 전송·실패·경합 회귀와 전체 하네스 결과 및 실제 인수 상태를 기록한다.
