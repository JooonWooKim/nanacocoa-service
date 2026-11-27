# CI 계약

상태: 공통 설정과 GitHub 품질 workflow 로컬 구현 · 원격 실행 확인 대기

제공자 중립 CI 진입점은 다음과 같습니다.

```sh
./scripts/setup-release-config.sh
./scripts/harness check
```

아키텍처, 보안, 마이그레이션, 문서, 하네스 자체 검사, 엔트로피 진단,
기준선 래칫 테스트, 위험 평가, 선택적 읽기 전용 smoke 검사,
`git diff --check`를 실행합니다. 로그와 기계 판독 상태는 `build/harness`에
저장합니다.

## 호스팅 실행기 요구사항

- Java 17
- Python 3
- 실행 가능한 Gradle wrapper
- POSIX shell과 표준 Unix 텍스트 도구
- 운영 비밀값 없음
- runner의 일반 정책 아래 의존성 해석에만 사용하는 네트워크
- 전체 Gradle 테스트에 적절한 timeout
- 대체된 이전 실행 취소
- wrapper·build 입력을 key로 사용하는 Gradle 의존성 cache
- 실패 시 하네스 게이트 상태, JUnit 집계, 실패 테스트 식별자 업로드
- 하네스 계약 주변에 `continue-on-error` 사용 금지

`.github/workflows/quality.yml`은 `main` 대상 일반 PR과 `main` push에서
읽기 권한으로 공통 설정과 기존 하네스를 실행합니다. 동일 PR·브랜치의 이전
실행은 취소합니다. 실패 요약은 원문 로그·테스트 출력과 분리해 7일간 보관합니다.
PR 설명 자동화는 별도 workflow에서 실행합니다. NC-CI-001의 원격 실행 확인과
브랜치 보호 설정은 별도입니다.

NC-TEST-001은 feature/30에서 해소해 제품 실패 기준선이 비어 있습니다. NC-ARCH-001과 검토된 보안 위험이 변경되지 않으면 `check`는
BASELINED를 표시하면서 종료 코드 0을 반환할 수 있습니다. 기준선 증가나
조용한 해소는 실패하며 리뷰가 필요합니다. 엄격한 release gate는 빈 기준선과
`./scripts/harness test` 통과를 추가로 요구해야 합니다.

## 격리 MySQL 품질 검사

`./scripts/harness mysql-quality`는 Java 17, Python 3, 로컬 Docker daemon을 사용합니다.
공용 템플릿과 합성 DB만 사용하며, 앞선 H2 검사와 분리된 Gradle 작업을 실행합니다.
컨테이너는 호스트에서 동적 loopback 포트로만 접속할 수 있고 실행이 끝나면 정리됩니다.
요약은 `build/harness/mysql-quality/summary.json`에서 확인합니다.
GitHub는 실패 시 이 파일을 보관합니다. [검사 범위와 인수 기준](../product-specs/mysql-quality-gate-prd.md)

## 배포 후보 격리 리허설

검토한 후보를 체크아웃한 뒤 로컬 Docker와 Compose에서 실행합니다.

```sh
./scripts/harness release-rehearsal --candidate "$(git rev-parse HEAD)"
```

후보 이미지와 임시 MySQL·Redis를 기동하고, 합성 데이터가 앱 재시작과 별도 DB 복원 뒤에도
같은지 확인합니다. stdout JSON의 종료 코드는 PASS 0, FAIL 1, BLOCKED 2입니다.
PASS의 `check` 객체를 같은 SHA의 사전점검 입력 `checks` 배열에 넣습니다. JSON에는
상태, 완료 단계, 비식별 증거만 남습니다. [리허설 PRD](../product-specs/release-rehearsal-prd.md)

## 운영 배포 사전점검

후보의 `main` Quality 실행 번호와 [리허설 JSON](#배포-후보-격리-리허설)을 확보한 뒤
기술 증거 입력을 생성합니다. `QUALITY_RUN_ID`는 GitHub Actions 실행 번호입니다.

```sh
./scripts/harness release-evidence --candidate "$(git rev-parse HEAD)" \
  --quality-run "$QUALITY_RUN_ID" --rehearsal rehearsal.json --target production > preflight.json
./scripts/harness release-preflight --input preflight.json
```

두 작업이 같은 실행 시도에 있어야 합니다. 일부 작업만 재실행한 시도에서 작업이 빠졌다면
전체 작업을 다시 실행한 뒤 결과를 확인합니다. `release-quality-<시도>` 요약은 30일간 보관합니다.
기준선 승인과 운영 증거는 담당자가 추가합니다. [증거 수집 PRD](../product-specs/release-evidence-prd.md)

운영 담당자는 비식별 증거를 [사전점검 PRD](../product-specs/production-release-preflight-prd.md)의
JSON 형식으로 준비한 뒤 `./scripts/harness release-preflight --input <파일>`을 실행합니다.
콘솔 안내는 표준 오류, JSON은 표준 출력으로 전달됩니다. 종료 코드 0은 증거 요건 충족,
1은 실패, 2는 미충족입니다. 후보 커밋과 대상 환경이 일치하는지 확인하고
실제 배포는 출시 승인 담당자의 절차를 따릅니다.

## PR Description 자동화

`PR Description` workflow는 PR이 열리거나 커밋이 추가되거나 다시 열리거나
review 준비 상태가 될 때 실행됩니다. `workflow_dispatch`에서 PR 번호를 입력해
수동으로도 실행할 수 있습니다.

이 workflow는 `pull_request_target`의 쓰기 토큰을 사용하므로 다음 경계를
유지합니다.

- 기본 branch의 workflow와 Node 스크립트만 checkout합니다.
- PR head, merge ref, artifact를 checkout하거나 실행하지 않습니다.
- 권한은 `contents: read`, `pull-requests: write`로 제한합니다.
- PR 파일과 커밋은 GitHub REST API에서 데이터로만 읽습니다.
- PR별 동시 실행은 하나만 유지하며 새 커밋이 오면 이전 실행을 취소합니다.

실제 설정, 외부 전송 범위, 폴백과 수동 실행 절차는
[automation.md](automation.md#ai-pr-description)를 따릅니다.
