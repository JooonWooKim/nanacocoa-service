# 서비스 정책 게시와 가입 동의 운영

상태: 구현됨 · 원문·운영 조건 승인 및 활성화 대기

## 기본 동작

`policy.signup-enabled` / `POLICY_SIGNUP_ENABLED`는 기본 false다. 정책 목록·원문
조회와 기존 회원 로그인은 유지하고 신규 가입만 `POLICY_01` / HTTP 503으로 보류한다.
true여도 승인된 현재 게시 집합이 없거나 카탈로그 적재가 실패하면 가입을 허용하지 않는다.
게시와 가입 활성화는 실 PG·RETURN 활성화 설정을 변경하지 않는다.

현재 저장소의 `application.yml`, `compose.yaml`, `.env.example`은 Git 제외 대상인
로컬 설정 파일이다. 이번 작업 트리에는 플래그 연결을 추가했지만 원격 코드에 포함됐다고
간주하지 않는다. 별도 배포 환경에서도 앱 프로세스나 Compose app 컨테이너에
`POLICY_SIGNUP_ENABLED`를 전달해야 한다. Java 코드 자체의 기본값도 false이므로
로컬 YAML 유무와 무관하게 미설정 환경에서 신규 가입을 허용하지 않는다.

`GET /api/policies`의 `data.signupReady`가 고객 화면의 가입 가능 여부다.
문서의 `bodyPath`와 `policies.html?document=TERMS&version=문서버전`으로
과거 게시 원문을 확인할 수 있다. 공개 응답에는 내부 승인자·승인 시각·회원 이력을
포함하지 않는다. 미게시·미존재 문서는 동일한 404다.

## 승인 문서 배포

원문은 `src/main/resources/policies/catalog.json`으로 검토·재배포한다.
원격 URL과 외부 파일은 적재하지 않는다. 초기 revision 0·빈 목록은 승인 문서가
아니며 신규 가입을 보류한다. DB에 남은 문서로 자동 대체하지 않는다.
테스트 리소스의 합성 문서를 운영 원문으로 복사하지 않는다.

| 필드 | 계약 |
| --- | --- |
| `revision` | 새로운 배포마다 증가하는 양의 정수. 배포 전 이전 파일과 비교해 역행·동일 revision 변경을 거부함 |
| `archivedDocuments` | 과거 게시 문서의 전체 내용. 생략 시 빈 배열. 현재 집합과 문서 코드/버전이 중복되면 적재 실패 |
| `documents` | TERMS, PRIVACY, SELLER_INFO, SHIPPING_RETURN, EMAIL_RECEIVE를 각각 한 번 포함한 현재 문서 집합 |
| `documentCode`, `version` | 문서 식별자와 고유 버전. version은 영문·숫자·점·밑줄·하이픈 1~64자 |
| `title`, `body` | 제목 1~200자, 일반 텍스트 원문 1~200,000자. HTML을 실행하지 않음 |
| `effectiveAt` | 시차를 포함한 ISO 날짜·시각. 현재보다 미래인 문서는 게시하지 않음 |
| `approvedBy`, `approvedAt` | 실제 승인 기록. 승인자는 1~100자, 승인 시각은 적용일보다 늦거나 현재보다 미래일 수 없음 |
| `consentMode` | NONE은 안내, REQUIRED는 필수 동의, OPTIONAL_EMAIL은 선택 이메일 수신. 필수 항목은 원문 승인에 따라 정하며 최소 하나 필요 |

SELLER_INFO·SHIPPING_RETURN은 NONE, EMAIL_RECEIVE는 OPTIONAL_EMAIL이다.
TERMS·PRIVACY의 동의 필요 여부는 승인 내용에 따른다. 서버는 안내 문서를 무조건
필수 동의로 바꾸지 않는다. 선택 거절도 실제 표시한 EMAIL_RECEIVE 버전과 함께 기록한다.

서버 시작 시 현재/과거 문서를 검증해 불변 메모리 스냅샷으로 읽는다. API 목록·본문
조회와 가입 동의 검증은 이 스냅샷을 사용하며 문서·게시 테이블에 접근하지 않는다.
같은 코드/버전은 덮어쓰지 않고 이전 현재 문서를 archivedDocuments로 이동한다.
파일 반영에는 재시작·배포가 필요하다. 실행 중 API 재조회는 파일을 다시 읽지 않는다.
현재 집합만 유효하지 않으면 독립적으로 검증된 과거 원문을 제공하며 가입은 보류한다.
파일 전체가 파싱 불가·누락되거나 과거 목록도 잘못됐으면 안전하게 읽을 수 있는 자료가
없으므로 원문도 제공하지 않는다. DB 원문으로 폴백하지 않는다.

## DB 약관에서 파일로 전환

V10은 기존 동의의 문서 코드·버전·해시를 채우고 기존 FK·동의 결과·기록 시각을
보존한다. 신규 동의에는 문서 FK 대신 코드·버전·해시를 직접 기록한다. 문서·게시 테이블은
삭제하지 않으며 전환 후 서비스에서 사용하지 않는다. 운영 DB에 이력이 없다 해도
기존 회원의 동의를 추정 생성하지 않는다.

1. 승인된 읽기 전용 DB 연결로 `scripts/policy-archive-export.sql`을 실행해 정책 문서만
   비공개 임시 파일로 내보낸다. 도구는 회원/동의/자격 증명을 출력하지 않는다.
2. 오프라인 도구로 기존 원문을 후보 카탈로그의 과거 목록에 합친다. 아래 경로는
   운영자가 준비한 임시 파일 예시이며 SQL 결과나 감사 파일을 무심코 커밋하지 않는다.

```sh
python3 scripts/policy-archive.py export --input /private/tmp/policy-db-export.json --catalog src/main/resources/policies/catalog.json --output /private/tmp/policy-candidate.json --audit /private/tmp/policy-legacy-audit.json
python3 scripts/policy-archive.py verify --input /private/tmp/policy-db-export.json --catalog /private/tmp/policy-candidate.json --audit /private/tmp/policy-legacy-audit.json --previous /private/tmp/previous-release-catalog.json
```

3. verify는 모든 DB 원문의 코드·버전·본문·승인 메타데이터와 원본 DB 해시 및 내보낸
   문서의 정규화 해시를 비교한다. 기존 해시는 원래 JSON 시차 표현에 의존할 수 있어
   변경하지 않고 감사 파일에 보존한다. 신규 이력 해시는 서버가 파일 Document JSON의
   SHA-256으로 계산한다. 이전 배포의 버전 누락/내용 변경, revision 역행·미증가도 거부한다.
4. 대조에 성공한 후보를 검토 후 정책 파일로 반영하고 감사 자료를 별도 보관한다.
   원문이 누락되거나 불일치하면 전환 배포를 보류한다. DB 내보내기와 후보 생성 사이에
   정책을 변경하지 않는다. 이후 모든 정책 배포에도 이전 배포 카탈로그 대조를 수행한다.
5. 신규 가입을 중지하고 진행 중인 가입을 종료한 뒤 백업·V10 적용·모든 인스턴스 교체를
   완료한다. 서로 다른 파일 버전의 서버가 동시에 가입을 받는 rolling 배포는 지원하지 않는다.
   전 인스턴스의 revision·문서 버전·가입 가능 상태를 확인한 후 명시적으로 활성화한다.
6. V10 이후 생성된 이력은 document_id가 없으므로 구버전 앱으로 단순 롤백하지 않는다.
   복구도 파일 기반 앱에서 진행하며 기존 데이터를 삭제하지 않는다.

## 운영 활성화 전 대조

1. 원문 승인자·적용일·필수/선택 항목, 판매자 정보와 실제 문의 경로를 확인한다.
2. 배송 범위·출고 기한·요금·택배사 안내를 실제 배송 설정과 서버 주문 합계에 대조한다.
   배송비 계산은 이 기능이 추가하지 않으므로 실제와 다른 요금을 공지하지 않는다.
3. SHIPPING_RETURN의 기간·회수·검수·환불 조건과 안내를 `returns.policy`의
   `version`, `guidance`, `windowDays`, `allowInTransit`, `allowReapply`,
   `refundAfterFailedInspection` 및 승인 기록과 대조한다.
   비용·부담 주체도 운영 원문과 실제 처리 절차가 같아야 한다.
4. 위 내용이 누락되거나 불일치하면 게시·가입·RETURN 신규 운영 활성화를 보류한다.
   이 대조는 운영 인수 절차이며 서버가 자연어 원문의 의미를 자동 판정하지 않는다.
5. 가상 회원으로 필수 누락·선택 거절 가입, 현재/이전 문서 조회·동의 이력 저장을
   테스트 환경에서 확인한 뒤에만 운영 신규 가입 플래그를 true로 반영한다.
   기존 회원 추가 동의 여부·시점·안내는 별도 승인하며 과거 이력을 생성하지 않는다.

## 장애와 중단

- 적재 실패는 원문·경로·승인 정보 없이 `POLICY_01` 로그로 남는다. 검증된 과거 파일과
  기존 회원 로그인은 유지하고 해당 인스턴스의 가입은 보류한다. 모든 인스턴스의
  게시 revision과 `signupReady`를 확인한다.
- 이전 버전 제출은 `POLICY_02` / 409다. 새 문서를 조회하고 필수·선택 동의를 다시
  받는다. 자동 가입 재전송은 하지 않는다. CSRF 오류도 사용자가 다시 제출한다.
- 가입 응답을 잃으면 로그인으로 가입 여부를 확인한다. 이메일 고유 제약이 추가 회원을
  막고 회원·동의 저장 실패는 함께 롤백된다. 비밀번호를 브라우저 저장소에 보관하지 않는다.
- 신규 가입 중단은 `POLICY_SIGNUP_ENABLED=false`로 재시작한다. 원문·동의 레코드와
  V9를 삭제하지 않는다. 원문 복구는 더 높은 revision에 이전 승인 버전들을 지정하고 기존 버전 파일을 모두 보존한다.
- 마이페이지 수신 설정 변경은 현재 회원 선택만 바꾸며 과거 가입 동의 기록은 유지한다.

검증 기록: [이슈 #28 구현 기록](../exec-plans/completed/issue-28-policy-implementation.md).

파일 전환 검증: [실행 기록](../exec-plans/completed/policy-file-storage.md).
