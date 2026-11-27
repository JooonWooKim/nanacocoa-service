# 주문·결제 장애 분석 실제 모델 파일럿 구현

상태: 완료

## 목표

[이슈 #63](https://github.com/JooonWooKim/nanacocoa-service/issues/63)의 로컬 파일럿에서 고정 합성 장애를 보내고 모델이 선택한 근거와 원인 후보를 검증한다.

## 하지 않는 일

실제 Slack, 운영 로그, 패치 생성·적용, 추천 테스트 자동 실행과 배포는 후속 범위다.

## 현재 증거

[구현 PRD](../../product-specs/incident-analysis-live-pilot-prd.md)와 기존 모의 하네스의 입출력·검증 경계를 대조했다.

## 결정

기본 모의 모드를 보존하고, 명시적 파일럿만 사례별 구조화 응답을 요청한다. 검증된 후보와 근거 번호를 로컬 설명으로 바꿔 표시한다.

## 작업 분해

- [x] 기존 문서 확인 및 백업
- [x] CLI 분기·제공자 어댑터·합성 사례 구현
- [x] 모의 HTTP, 회귀 및 실제 인수 가능 여부 확인
- [x] 출시 PRD와 실행 기록 갱신

## 진행 상황

- 2026-11-27: `feature/63`의 기존 문서 19개를 백업하고 SHA-256을 기록했다.
- 2026-11-27: 명시적 CLI 모드와 합성 입력·구조화 응답 검증을 추가했다. 기존 모의 분석과 Slack 테스트를 유지했다.
- 2026-11-27: 모의 HTTP와 전체 하네스를 검증했다. 기존 파일 16개의 SHA-256이 일치하며, 공동 편집 문서 3개는 추가분을 대조했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status -sb` | PASS | 현재 브랜치와 추적 원격을 확인했다. |
| `python3 -B src/test/python/test_incident_live_pilot.py -v` | PASS | 모의 HTTP 11개. 세 사례, 노출 경계, 후보·참조 검증, 무재시도·조기 중단·타임아웃. |
| `python3 -B -m unittest discover -s src/test/python` | PASS | 기존 모의 분석과 Slack을 포함한 Python 55개. |
| `./scripts/harness check` | BASELINED | H2 Java 268개와 위험 평가 63개, 모의 HTTP fixture 통과. 기존 아키텍처·보안 예외 유지. |
| `./scripts/harness docs`, `git diff --check` | PASS | 제품·실행 기록 링크와 공백 검사. |
| `./scripts/harness incident-analyze --live-pilot` | BLOCKED | 이 환경에서 전용 프로젝트 키가 없어 `PILOT_KEY_UNAVAILABLE`로 종료했다. 실제 호출 0회. |
| Compose 런타임 | SKIPPED | 로컬 Compose 앱이 기동되지 않았다. |

## 위험과 롤백

실제 호출의 입력은 고정 합성 상태로 제한한다. 모델 출력은 근거 번호와 후보 ID를 대조하고 원문을 기록하지 않는다.

## 미해결 항목

전용 프로젝트에서 최종 인수를 기록하고 원격 반영 여부를 별도로 추적한다.

## 완료 조건

합성 사례와 실패 경계의 자동화 검사, 실제 호출 인수 상태 및 전체 하네스 결과를 기록한다.
