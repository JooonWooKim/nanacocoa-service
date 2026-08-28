# 유지보수 자동화

상태: 제안, 외부 schedule은 활성화하지 않음

권장 저장소 소유 주기:

| 주기 | 명령 | 목적 |
| --- | --- | --- |
| 모든 변경 | `./scripts/harness check` | 회귀와 drift 게이트 |
| 활성 작업 중 매일 | `./scripts/harness test-baseline` | 새 실패와 해소된 실패 탐지 |
| 매주 | `./scripts/harness entropy` | 부채, 예외, 오래된 계획, 작업 표시, 고립 문서 후보, 중복 이름, 반복 실패, 생성·마이그레이션 drift 탐색 |
| 마이그레이션·패키지 변경 후 | `./scripts/harness generate-docs` | 생성 참조 문서 갱신 |
| 인계 전 | `./scripts/harness evidence` | 비식별화된 상태 보존 |

entropy 명령은 진단만 수행합니다. 파일 삭제, 기준선 갱신, 광범위한
refactor를 수행하지 않습니다.

자동 삭제, merge, 배포, 제공자 호출, 기준선 수락을 schedule하지 않습니다.
저장소에 remote, 범위가 제한된 자격 증명, 리뷰 규칙, 명시적 사용자 승인이
생긴 후에만 hosted 자동화가 유지보수 변경안을 열 수 있습니다.

## AI PR Description

상태: 구현 완료, `main` 병합과 repository secret 등록 후 활성화

`.github/workflows/pr-description.yml`은 PR의 변경 파일과 커밋을 읽어 작성자가
입력한 본문 뒤에 다음 관리 구역을 추가합니다.

```text
<!-- pr-auto:start -->
...
<!-- pr-auto:end -->
```

이후 실행은 두 마커 사이만 교체합니다. 한쪽 마커가 없거나 중복되거나 순서가
뒤집혀 있으면 작성자 본문 손실을 막기 위해 갱신을 실패시킵니다.

### 최초 설정

1. GitHub repository의 `Settings` → `Secrets and variables` → `Actions`에서
   repository secret `OPENAI_API_KEY`를 추가합니다.
2. `Settings` → `Actions` 정책에서 `pull_request_target` 실행과 workflow의
   `pull-requests: write` 권한이 차단되지 않았는지 확인합니다. 전체 기본 권한을
   write로 넓힐 필요는 없습니다.
3. 이 workflow를 추가하는 최초 PR을 `main`에 병합합니다. 기본 branch에 없는
   `pull_request_target` workflow는 해당 도입 PR 자체를 자동 갱신하지 않습니다.
4. GitHub의 `Actions` → `PR Description` → `Run workflow`에서 기존 PR 번호를
   입력해 최초 동작을 확인합니다.

`OPENAI_API_KEY`는 환경 변수로만 OpenAI Responses API에 전달하며 코드, 로그,
PR 본문에 기록하지 않습니다. 키가 없거나 OpenAI가 timeout, rate limit, 잘못된
구조화 출력 또는 서버 오류를 반환하면 파일·커밋 통계 기반 폴백 설명을
작성하고 Actions Job Summary에 경고를 남깁니다. GitHub API 권한 오류와 잘못된
관리 마커는 본문을 수정하지 않고 job을 실패시킵니다.

### 외부 전송과 비용 경계

- 모델은 `gpt-5.6-luna`, 추론 수준은 `low`, 응답 저장은 `store: false`입니다.
- 파일명, 상태, 추가·삭제 통계, 커밋 제목과 제한된 텍스트 patch를 전송합니다.
- `.env`, credential·secret·키·인증서 후보 경로와 바이너리는 AI 입력에서
  제외합니다.
- patch에서 대표적인 API key, token, password, JWT, private key를 마스킹합니다.
- patch는 파일당 8 KiB, 전체 60 KiB이고 모델 출력은 700 token으로 제한합니다.
- 실제 비용과 rate limit은 `OPENAI_API_KEY`가 속한 OpenAI project 설정을
  따르므로 project budget과 사용량 알림을 별도로 설정합니다.

변경 patch와 커밋 메시지는 신뢰할 수 없는 입력입니다. workflow는 PR head를
checkout하거나 실행하지 않고, 모델에 도구를 제공하지 않으며, AI 출력에서
HTML 주석, 관리 마커, 이미지 Markdown을 제거합니다. 변경 자원 목록과 통계는
모델 출력이 아니라 GitHub API 응답에서 결정론적으로 생성합니다.
