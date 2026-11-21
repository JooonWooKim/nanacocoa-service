# OPS · Actuator 상태 확인 계약 정비 PRD

| 항목 | 내용 |
| --- | --- |
| 상태 | feature/30 로컬 구현 · 집중/전체 엄격·test-baseline·eval PASS, check 기존 구조/보안 BASELINED · main 미반영 |
| 우선순위 | 출시 전 기술 검증 우선 · NC-TEST-001 |
| 기준 | 2026-11-20 원격 main `de5c5950c01de64271f4306277ee57824985e77b`, PR #29 병합 확인 |
| 이슈 | [NC-TEST-001 #30](https://github.com/JooonWooKim/nanacocoa-service/issues/30) |
| 브랜치 | `feature/30` → `origin/feature/30` · 구현·문서 로컬 미커밋, main 미반영 |
| 준비 기록 | [상태 확인 계약 정비 준비](../exec-plans/completed/health-check-preparation.md) |
| 대상 | 배포/운영 담당자, 상태 확인 소비자, CI 검증 담당자 |

## 목표와 현재 증거

공식 상태 확인 경로를 기존 `GET /actuator/health`로 통일한다. README와
`scripts/harness.d/smoke.sh`는 이미 이 경로의 HTTP 200과 `status: UP`을 사용한다.
변경 전 실패는 `NanacocoaServerApplicationTests#healthApiReturnsOk`가 구현되지 않은
`/api/health`의 HTTP 200과 `status: ok`를 기대해 404를 받는 계약 불일치였다.
변경 전 SecurityConfig는 두 경로를 익명 허용했다. 이번 구현은 미사용 `/api/health` 허용만 제거했다.
사용자는 별도 API 추가 대신 Actuator 통일을 선택했다.

## 범위와 공개 계약

- 익명 `GET /actuator/health`를 공식 경로로 유지한다. 기존 Actuator JSON을 사용하며
  별도 Controller/Service·SuccessMessage 래핑·DB 테이블·마이그레이션을 추가하지 않는다.
- 정상 집계 상태는 HTTP 200과 최상위 `status: UP`이다. 테스트용 장애 지표로 집계가
  DOWN 또는 OUT_OF_SERVICE가 되면 Actuator 기본 매핑에 따라 HTTP 503을 반환한다.
  항상 정상으로 응답하는 대체 endpoint/고정 응답을 만들지 않는다.
- 익명 정상/장애 응답은 상태만 공개한다. 지표별 details/components·DB 접속 정보·예외·
  자격 증명 등 내부 정보가 노출되지 않도록 한다. 실제 프레임워크 응답을 HTTP로 검증한다.
- 구현된 소비자가 없는 `/api/health`의 불필요한 익명 허용 항목을 제거한다.
  호환 별칭을 추가하지 않으며 해당 경로를 운영 확인에 사용하지 않는다.
- `/actuator/health/**`의 기존 프로브 경로와 다른 인증/CSRF 경계는 이번 정비로 확대하지 않는다.
  README·smoke의 endpoint와 UP 판정 계약을 유지한다.

## 테스트·평가와 실패 기준선

기존 health 테스트를 삭제·skip하지 않고 공식 경로와 응답으로 수정한다. 상태 집계를
mock 응답으로 대체하지 않고 테스트 전용 HealthIndicator를 사용해 정상/장애 응답을
재현한다. 외부 운영 DB·Redis·실제 Toss Payments·S3를 호출하지 않는다.

1. 익명 정상 조회: HTTP 200, `$.status == UP`, 내부 details/components 부재.
2. 테스트용 장애 지표: HTTP 503과 DOWN/OUT_OF_SERVICE, 내부 오류 정보 비노출.
3. 기존 보호 API는 익명 접근을 계속 거부하고 정상 health 조회에 로그인/CSRF 토큰을 요구하지 않는다.
4. OPS-001의 경로/판정 문구를 실제 운영 계약과 일치시킨다. 테스트 명칭을 바꾸면
   테스트 발견 목록과 평가 연결을 함께 검토하며 정상·장애 검증을 줄이지 않는다.
5. 집중 검증 후 전체 엄격 테스트의 실패 0개·skip 0개를 확인한 다음에만
   `test-failures.txt`와 `test-failure-fingerprints.txt`의 해결된 health 항목을 제거한다.
   다른 아키텍처/보안 예외를 삭제하거나 검사를 약화하지 않는다.
6. 전체 엄격 테스트·test-baseline·eval·check를 다시 실행하고 새 실패가 없음을 확인한다.
   NC-TEST-001과 상태 확인 관련 품질/운영 문서를 증거에 맞춰 갱신한다.

## 작업 항목과 인수 기준

| 작업 항목 | 구현 범위 | 인수 기준 |
| --- | --- | --- |
| 상태 확인 계약 | 기존 GET /actuator/health를 공식 경로로 통일 | 정상 상태 HTTP 200, status UP |
| 장애 상태 검증 | Actuator 기존 상태 집계와 HTTP 매핑 | 테스트용 장애 지표에서 HTTP 503, 정상 오인 없음 |
| 접근·정보 보호 | 익명 상태 조회 유지, /api/health 허용 정리 | 로그인 없이 확인 가능, 내부 연결/예외 정보 비노출 |
| 테스트·평가 정합성 | 기존 health 테스트와 OPS-001 대조 | 테스트 삭제/비활성화 없이 정상·장애 상태 통과 |
| 실패 기준선 정리 | 검증된 health 실패 항목만 제거 | 전체 엄격 테스트 실패 0개·skip 0개, 다른 부채 별도 유지 |

## 준비 완료와 구현 완료의 구분

준비 당시에는 문서·이슈·브랜치만 추가하고 코드/기준선을 유지했다.
현재는 별도 구현 요청에 따라 기존 health 테스트 이름을 유지한 채 Actuator 계약으로
수정했고, 실제 HTTP 4개와 기존 세션/CSRF 회귀를 포함한 집중 6개가 통과했다.
전체 엄격 테스트 191개 실패·skip 0개를 확인한 뒤 해결된 health 실패/fingerprint만
제거하고 발견 목록에 신규 4개만 추가했다. 기준선 제거 전 증거는
`build/harness/health-pre-removal-evidence.json`이다. 기존 아키텍처/보안 예외와 점수는 유지한다.
test-baseline과 eval(평가 63개)은 PASS다. 전체 check는 기존 구조 예외 3개·보안 위험 2개만 BASELINED이며 Compose smoke는 스택 부재로 SKIPPED다. 최종 결과는 [구현 기록](../exec-plans/completed/health-check-implementation.md)에 기록한다.
기능은 로컬 구현이며 main 미반영이다. 커밋·push·PR 생성은 수행하지 않았다.
판매자 운영 기준·정책 승인은 후순위 대기를 유지하며 가입/RETURN/실거래를 활성화하지 않는다.

## 근거

- [출시 PRD](commerce-launch-prd.md), [제품 명세 인덱스](index.md)
- [테스트 절차](../runbooks/testing.md), [기술부채](../exec-plans/tech-debt-tracker.md)
- [관측성](../quality/OBSERVABILITY.md), [평가 카탈로그](evaluation-catalog.md)
