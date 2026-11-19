# 약관 파일 조회와 DB 동의 이력 분리

상태: 완료

## 목표

정책 API와 가입 검증은 시작 시 읽은 불변 파일 스냅샷을 사용하고 DB에는 동의 이력을 저장한다.

## 하지 않는 일

기존 사용자/UI 변경 폐기, 운영 DB 접속·게시·활성화·배포·커밋·push는 하지 않는다.

## 현재 증거

조사 시 feature/28의 V9는 문서 FK 동의 이력을 저장하고 조회·게시에서 DB 잠금을 사용했다.
183개 Java 테스트에 기존 health 실패 1개가 있으며 Compose 앱은 H2 UI 테스트를 위해 중지돼 있다.

## 결정

기존 API/가입 payload를 유지한다. current documents와 archivedDocuments를 파일로 보존한다.
런타임 재적재를 제공하지 않고 서버 재시작 시에만 적용한다. V10으로 기존 이력을 이행하고 레거시 테이블은 보존한다.

## 작업 분해

- [x] 조사
- [x] 구현
- [x] 검증
- [x] 리뷰와 문서화

## 진행 상황

- 2026-11-20: 기존 변경을 별도 임시 디렉터리에 백업하고 파일/동의 분리 구현을 시작했다.
- 2026-11-20: 정책 DB Repository/게시 서비스를 제거하고 시작 시 1회 설치하는 불변 파일 스냅샷으로 API·가입 검증을 전환했다. V9 테이블/엔티티는 레거시 스키마 호환 용도로만 남긴다.
- 2026-11-20: V10을 등록했다. 기존 동의 코드/버전/해시·FK·시각을 보존하고 신규 이력에는 문서 FK를 요구하지 않는다.
- 2026-11-20: 초기 발견 183개 중 게시 잠금 관련 3개를 파일 후보 충돌·스냅샷 고정·검증 버전 기록으로 교체하고 정책 3개·이행 1개를 추가해 187개로 목록을 갱신했다. health 실패 fingerprint는 변경하지 않았다.
- 2026-11-20: MySQL 8.4.10 tmpfs 컨테이너의 분리 DB에서 검증했고 이 작업에서 생성한 컨테이너만 정리했다. 운영 DB/기존 MySQL·Redis/볼륨에는 접속·변경하지 않았다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git status --short --branch` | PASS | feature/28 및 기존 UI/사용자 문서 변경 확인 |
| 집중 Gradle 테스트 | PASS | H2 정책 16개·V10 이행 1개·전체 이행 1개·JPA 검증 1개, 총 19개 |
| 격리 MySQL 집중 테스트 | PASS | MySQL 8.4.10 정책 16개·V10 이행 1개·전체 이행 1개, 총 18개 |
| `python3 -B -m unittest discover -s src/test/python -v` | PASS | 오프라인 내보내기·원문/해시 대조·이전 배포 버전 보존 3개 |
| 내보내기 SQL + export/verify CLI | PASS | 격리 MySQL의 레거시 fixture를 실제 SQL로 내보내고 파일과 원본 해시 대조 성공 |
| `node --test src/test/js/*.test.cjs` | PASS | 기존 정책/가입·반품·결제 운영 JS 4개 파일 |
| 정책 API SQL 실행 수 | PASS | 목록·본문 GET 전후 Hibernate prepareStatementCount 증가 0 |
| 아키텍처/보안 | BASELINED | 기존 구조 예외 3개와 보안 위험 2개 유지, 신규 회귀 없음 |
| `./scripts/harness migrations --accept-new` | PASS | V10만 등록, 기존 마이그레이션 checksum 유지 |
| `./scripts/harness check` | FAIL | 최종 전체 187개·skip 0개, 기존 health 실패 1개만 BASELINED. 중지된 Compose app의 smoke 게이트 1개만 FAIL |
| 엄격 `./gradlew test` (check 내부) | FAIL | 기존 healthApiReturnsOk HTTP 404 1개, 새 실패 없음 |
| docs·migrations·harness-self-check·entropy·diff·workspace-stability | PASS | check-summary.txt에서 확인 |
| test-baseline·eval | BASELINED | 기존 health/OPS 부채 유지, POLICY 평가 6개 PASS |
| 8080 실제 HTTP 확인 | PASS | 새 코드로 H2 서버 재시작. 목록·5개 본문, 필수 누락 400·이전 버전 409·선택 수신 거절 합성 가입 201 |

- 2026-11-20: 출시 PRD·서비스 PRD·아키텍처·운영/테스트 절차와 생성 문서를 갱신했다.
- 2026-11-20: 기존 UI/사용자 문서 변경을 백업과 비교해 보존을 확인했다. 이번 전환에 필요한 출시/서비스 PRD와 생성 지도는 기존 내용을 유지하며 추가 갱신했다. 커밋·push·운영 배포는 하지 않았다.

## 위험과 롤백

과거 원문 누락·동의 이력 손실·버전 불일치 가입·동시 배포를 검증한다.
전환 전 신규 가입을 중지하고 전체 인스턴스를 같은 파일로 교체한다. V9/기존 테이블을 삭제하지 않는다.
V10 이후 신규 동의는 document_id가 없으므로 구버전 앱으로 단순 롤백하지 않는다.

## 미해결 항목

운영 원문 승인·기존 운영 DB 문서 파일 내보내기와 배포 전 대조는 운영자 작업이다.

## 완료 조건

집중 테스트·전체 check 분류, 새 마이그레이션 등록, 문서/출시 PRD 갱신과 기존 변경 보존 확인을 완료했다.
코드/검증 완료와 운영 이행/승인을 구분한다. 운영 DB 내보내기·파일 대조·배포는 미수행이다.
증거는 `build/harness/check-summary.txt`, JUnit XML, eval 결과에 남겼다.
기존 183개 발견 목록에서 3개를 교체하고 4개를 추가한 변경만 허용했으며 실패 기준선은 유지했다.
