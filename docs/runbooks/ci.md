# CI 계약

상태: 로컬 구현 완료, hosted 제공자는 NC-CI-001로 차단됨

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

현재 저장소에는 최초 커밋과 remote가 없어 host, 기본 branch, pull request
event 모델을 안전하게 정할 수 없습니다. remote 구성 후 제공자 workflow를
좁은 변경으로 추가합니다. 제공자 전용 테스트 로직을 추가하지 말고 hosted
job도 동일한 스크립트를 호출해야 합니다.

NC-TEST-001, NC-ARCH-001, 검토된 보안 위험이 변경되지 않으면 `check`는
BASELINED를 표시하면서 종료 코드 0을 반환할 수 있습니다. 기준선 증가나
조용한 해소는 실패하며 리뷰가 필요합니다. 엄격한 release gate는 빈 기준선과
`./scripts/harness test` 통과를 추가로 요구해야 합니다.
