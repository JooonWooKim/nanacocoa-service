# 마이페이지 주소검색 적용

상태: 완료

## 목표

로그인 회원의 저장 주소를 마이페이지에 표시하고 checkout과 동일한
Kakao 우편번호 검색 UI로 수정할 수 있게 한다.

## 하지 않는 일

- 회원 API, DTO, Entity, DB 스키마를 변경하지 않는다.
- checkout의 주소 저장 형식과 검색 UX를 변경하지 않는다.
- Kakao API 키나 mock 데이터를 추가하지 않는다.

## 현재 증거

- `GET /api/members/me`는 인증 회원의 `address`를 단일 문자열로 반환한다.
- 마이페이지는 현재 textarea에 응답 주소를 주입한다.
- checkout은 `[12345] 기본주소 상세주소` 형식과 Kakao 로더, 수동 입력
  fallback을 이미 제공한다.

## 결정

- 주소 파싱·조합과 Kakao 검색 초기화를 checkout과 마이페이지가 공유한다.
- 우편번호 없는 기존 주소는 기본주소에 표시하고, 주소를 건드리지 않으면
  다른 프로필 저장 시 원문을 유지한다.
- 주소가 변경된 경우에만 5자리 우편번호와 기본주소를 필수 검증한다.
- 외부 스크립트 실패는 검색만 비활성화하고 입력·저장 흐름은 유지한다.

## 작업 분해

- [x] 조사
- [x] 구현
- [x] 검증
- [x] 리뷰와 문서화

## 진행 상황

- 2026-09-15: `codex/mypage-address-search` 전용 worktree를 생성하고 기존
  회원 API, 마이페이지, checkout 주소 흐름을 확인했다.
- 2026-09-15: 마이페이지 주소 UI, 저장 주소 파싱·보존, 공용 Kakao
  검색 초기화와 정적 페이지 회귀 계약을 구현했다.
- 2026-09-15: 표준·기존 주소, 수정 저장, 390px 레이아웃, 외부
  스크립트 차단 fallback을 임시 로컬 mock으로 확인했다.
- 2026-09-15: 전체 기준선 테스트 83개에서 신규 실패 없이 기존 실패
  5개만 유지되었고 평가 카탈로그도 통과했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `./gradlew test --tests com.nanacocoa.server.member.MemberApiIntegrationTest` | PASS | 변경 전 회원 조회·수정 계약 정상 |
| `node --check src/main/resources/static/script.js` | PASS | JavaScript 구문 정상 |
| `./gradlew test --tests com.nanacocoa.server.member.MemberApiIntegrationTest` | PASS | 마이페이지 주소 DOM과 회원 API 계약 통과 |
| 로컬 mock 브라우저 검증 | PASS | 표준·기존 주소, 저장, 390px, CDN 실패 fallback 확인 |
| `./scripts/harness docs` | PASS | 지식 graph와 생성 산출물 일관성 유지 |
| `./scripts/harness test-baseline` | BASELINED | 83개, skip 0개, 기존 fingerprint 실패 5개 유지 |
| `./scripts/harness eval` | BASELINED | 위험 카탈로그 신규 회귀 없음 |
| `./scripts/harness check` | FAIL/BLOCKED | 현재 브랜치에 없는 `.env.example`·`compose.yaml`, Docker 미가동, sandbox Gradle cache 권한으로 실패; 주소 변경과 무관 |

## 위험과 롤백

- 공용화 중 checkout 이벤트 흐름이 변경될 수 있으므로 기존 정적 계약과
  브라우저 흐름을 함께 검증한다.
- 롤백은 이 브랜치의 정적 페이지, 스크립트, 테스트 변경만 되돌린다.

## 미해결 항목

- `./scripts/harness check`의 환경·Compose·보안 게이트는 현재 브랜치에 필수
  구성 파일이 없어 실패한다. 이 작업에서 관련 없는 운영 구성은 추가하지
  않았다.

## 완료 조건

- 표준·기존 주소 표시, 검색 선택, 검증, 스크립트 실패 흐름을 확인한다.
- JavaScript 구문, 관련 통합 테스트, `./scripts/harness check`를 실행하고 분류한다.
- 회귀, 보안, 데이터 손실 위험을 검토한 뒤 계획을 `completed/`로 이동한다.
