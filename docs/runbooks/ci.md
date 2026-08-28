# CI 계약

상태: 로컬 구현 완료, remote와 기본 branch 확인, hosted 품질 게이트는 NC-CI-001로 차단됨

제공자 중립 CI 진입점은 다음과 같습니다.

```sh
./scripts/harness check
```

아키텍처, 보안, 마이그레이션, 문서, 하네스 자체 검사, 엔트로피 진단,
기준선 래칫 테스트, 위험 평가, 선택적 읽기 전용 smoke 검사,
`git diff --check`를 실행합니다. 로그와 기계 판독 상태는 `build/harness`에
저장합니다.

## 호스팅 실행기 요구사항

- Java 17
- 실행 가능한 Gradle wrapper
- POSIX shell과 표준 Unix 텍스트 도구
- 운영 비밀값 없음
- runner의 일반 정책 아래 의존성 해석에만 사용하는 네트워크
- 전체 Gradle 테스트에 적절한 timeout
- 대체된 이전 실행 취소
- wrapper·build 입력을 key로 사용하는 Gradle 의존성 cache
- 실패 후 `build/reports/tests`, `build/test-results`, `build/harness` 업로드
- 하네스 계약 주변에 `continue-on-error` 사용 금지

현재 저장소의 host는 GitHub, remote는
`https://github.com/JooonWooKim/nanacocoa-service.git`, 기본 branch는
`main`입니다. `.github/workflows/pr-description.yml`은 PR metadata 자동화만
담당하며 품질 게이트가 아닙니다. NC-CI-001을 해소하려면 별도의 hosted job이
제공자 전용 테스트 로직 없이 `./scripts/harness check`를 호출해야 합니다.

NC-TEST-001, NC-ARCH-001, 검토된 보안 위험이 변경되지 않으면 `check`는
BASELINED를 표시하면서 종료 코드 0을 반환할 수 있습니다. 기준선 증가나
조용한 해소는 실패하며 리뷰가 필요합니다. 엄격한 release gate는 빈 기준선과
`./scripts/harness test` 통과를 추가로 요구해야 합니다.

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
