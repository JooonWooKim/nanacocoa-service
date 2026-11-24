# 배포 검증 재현성과 PR 품질 검사 PRD

| 항목 | 내용 |
| --- | --- |
| 상태 | 로컬 구현·격리 검증 완료 · GitHub 실행과 main 반영 대기 |
| 우선순위 | P0 OPS-RELEASE-READINESS |
| 기준 | 2026-11-24 원격 main `9fc323407e306662e9154603401fb492e8bafc81`, [PR #49](https://github.com/JooonWooKim/nanacocoa-service/pull/49) 반영 |
| 이슈·브랜치 | [이슈 #50](https://github.com/JooonWooKim/nanacocoa-service/issues/50) · `feature/50` → `origin/feature/50` |
| 대상 | 출시 후보를 검증하는 개발자, PR 검토자, 배포 담당자 |

## 목표와 인수 기준

깨끗한 체크아웃에 공통 설정을 적용해 전체 하네스와 격리 배포 리허설을 재현한다. GitHub PR과 `main`에서도 같은 하네스 결과를 확인한다.

| 작업 항목 | 구현 범위 | 인수 기준 |
| --- | --- | --- |
| 깨진 문서 링크 | 배송 관리 실행 기록에서 누락된 과거 링크를 제거하고, 결제 복구 기록의 빌드 산출물 링크를 경로 표기로 변경 | 깨끗한 체크아웃에서 `./scripts/harness docs` 통과 |
| 공통 설정 | 비밀값 없는 Docker·Compose·Spring 런타임 및 테스트 설정과 환경 변수 예시, 누락 파일 초기화 스크립트 | 공용 템플릿만으로 검증 재현. 기존 파일이 같으면 그대로 사용하고 다르면 복사 전 경로를 보고. 사용자 설정과 `.env`를 보존 |
| GitHub PR 검사 | 일반 PR·main push에서 Java 17과 Gradle wrapper로 기존 하네스 실행, 실패 증거 보관 | 신규 실패는 GitHub 검사에 표시. 기존 구조·보안 기준선 유지, PR 설명 작업과 독립 실행 |
| 격리 배포 리허설 | 별도 Compose 프로젝트에서 앱·MySQL·Redis, V18·JPA 검증, health, 재시작, 합성 DB 백업·복원 | 개인 로컬 설정 없이 재현. 합성 주문·결제·동의 기록을 복원하고 가입·실결제 기본 비활성을 확인 |

깨끗한 main 스냅샷에서는 배송 관리 기록의 과거 출시 PRD 링크, 결제 복구 기록의 미생성 검사 요약 링크와 공통 설정이 없을 때의 생성 저장소 지도 불일치가 확인됐다. 배송 관리 기록의 기존 출시 명세 링크를 유지한다. 빌드 산출물은 생성 후 볼 수 있는 경로로 표기하고, 공통 설정 적용 뒤 생성 지도가 일치하는지 확인한다.

## 설정과 초기화 계약

버전 관리할 공통 파일과 실행 환경의 기존 경로를 아래처럼 연결한다. `scripts/setup-release-config.sh`는 저장소 루트를 기준으로 매핑을 적용한다.

| 공용 템플릿 | 실행 경로 |
| --- | --- |
| `deploy/templates/Dockerfile.template` | `Dockerfile` |
| `deploy/templates/dockerignore` | `.dockerignore` |
| `deploy/templates/compose.template.yml` | `compose.yaml` |
| `deploy/templates/application.template.yml` | `src/main/resources/application.yml` |
| `deploy/templates/application-test.template.yml` | `src/test/resources/application-test.yml` |
| `deploy/templates/env.example` | `.env.example` |

- 초기화 전에 모든 대상과 원본을 확인한다. 대상이 없으면 생성하고 같은 내용의 일반 파일은 건너뛴다. 다른 내용, 디렉터리, 심볼릭 링크가 있으면 경로만 출력하고 파일 복사 전에 실패한다. 기존 로컬 파일은 조작하지 않는다. `.env` 초기화는 기존 `scripts/init-env.sh`의 명시적 실행으로 유지한다.
- 환경 예시에는 비밀값을 비워 두고 운영 자격 증명은 기존 외부 주입 방식을 유지한다. 격리 검증 기본값은 실제 PG·자동 복구·가입·운영 정책·메일·외부 알림 비활성, 이미지 저장소 mock이다. 테스트 프로필과 합성 데이터를 운영 이미지에 포함하지 않는다.
- 운영형 Compose smoke와 H2 테스트 health를 구분한다. H2 테스트 설정에서 외부 Redis 의존을 제거하고, Compose에서 앱의 MySQL·Redis 의존과 실제 health를 따로 확인한다. 기존 헬스 계약과 기준선을 유지한다.

## GitHub 검사 및 증거

- 별도 품질 workflow는 일반 `pull_request`와 `main` push에서 실행한다. 운영 시크릿과 쓰기 권한 없이 공통 설정을 초기화한 뒤 `./scripts/harness check`를 그대로 호출한다. 동일 PR·브랜치의 이전 실행을 취소하고 종료 코드와 기준선 분류를 보존한다.
- 검사가 실패하면 허용된 검사 이름·상태, JUnit 집계 및 테스트 식별자만 추출해 7일간 보관한다. 원본 로그, 테스트 XML, 환경 파일, 고객 데이터와 인증 값은 업로드 대상에서 제외한다. 기존 PR 설명 자동화와 브랜치 보호 설정은 별도다.
- 출시 PRD에는 깨끗한 체크아웃, 격리 Compose, GitHub 실행을 각각 확인한 뒤 실제 결과를 기록한다. 운영 대상 인수, 판매자 정책, 실재고, 운영 PG 확인과 활성화는 별도이며 운영 배포 보류 상태를 유지한다.

## 2026-11-24 구현 검증

| ID | 검증 | 결과 | 증거 |
| --- | --- | --- | --- |
| RELEASE-01 | 문서 링크, 공용 설정 생성·재실행·충돌 | PASS | 깨끗한 체크아웃에서 초기화 전 문서 검사 통과. 최초 6개 파일 생성, 재실행 변경 없음, 내용 충돌과 심볼릭 링크에서 복사 전 중단, `.env` 보존 확인 |
| RELEASE-02 | Java 17, 기존 테스트·마이그레이션·평가·전체 하네스 | BASELINED | Java 264개와 평가 63개 통과. 문서·이행·보안·아키텍처 검사에서 신규 위반 없이 기존 구조·보안 예외만 유지. Compose 실행 전 runtime smoke는 SKIPPED |
| RELEASE-03 | GitHub PR·main push 품질 workflow와 실패 증거 | BLOCKED | workflow YAML과 로컬 하네스 명령 및 실패 증거 추출 검증은 PASS. GitHub 실행 링크와 원격 실패 표시는 변경 반영 후 확인 |
| RELEASE-04 | 격리 Compose 이미지, V18·JPA·health, 재시작과 백업·복원 | PASS | 앱·MySQL 8.4·Redis 정상 기동. 재시작과 DB 복원 후 합성 주문·결제·동의 일치. 기존 주문 조회, 신규 가입·실결제 기본 비활성, 이미지에 테스트 설정 미포함 확인 |
| RELEASE-05 | 모의 PG 기반 가입·구매 회귀 | PASS | 합성 정책에서 필수 동의 누락·중복 가입 차단, 선택 수신 거절 상태 가입·로그인, 주문·결제 재요청과 승인 1회 및 판매 재고 확정 확인 |

API·스키마·거래 규칙은 기존 계약을 유지한다. 실제 배포 대상 인수, 운영 정책 승인, 실재고·실결제 확인과 활성화는 출시 승인 기준에서 추적한다. 운영 배포는 보류한다.

- [커머스 출시 PRD](commerce-launch-prd.md)
- [CI 계약](../runbooks/ci.md)
- [기술부채 추적기](../exec-plans/tech-debt-tracker.md)
- [준비 실행 기록](../exec-plans/completed/release-readiness-preparation.md)
- [구현 및 검증 기록](../exec-plans/completed/release-readiness-implementation.md)
