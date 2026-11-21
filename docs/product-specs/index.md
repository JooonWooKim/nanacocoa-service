# 제품 명세 인덱스

상태: 활성
갱신 조건: 외부에서 관찰 가능한 API 흐름이나 위험이 변경될 때

기존 동작은 Controller, 정적 페이지 client, 테스트로 표현되며 새 기능은
집중 명세와 실행 가능한 검증을 함께 유지합니다.

- [아키텍처](../../ARCHITECTURE.md)는 경계와 데이터 흐름의 기준입니다.
- [평가 카탈로그](evaluation-catalog.md)는 주요 동작과 위험을 실행 가능한
  테스트에 연결합니다.
- [커머스 출시 PRD](commerce-launch-prd.md)는 구현 우선순위와 기능별 인수 기준,
  출시 승인 조건을 정의합니다.
- [P0 구매·환불·재입고 종단 검증 PRD](p0-commerce-e2e-prd.md)는
  [이슈 #32](https://github.com/JooonWooKim/nanacocoa-service/issues/32)의 합성 정책·모의 PG
  검증 환경, 구매·반품·환불·복구·재입고 시나리오와 화면/API/DB 증거 기준을 정의합니다.
  `feature/32`에서 PRD 12개 기술 시나리오와 Safari 실제 확인창·배송/반품 전체 흐름을 검증했습니다.
  추가 서버 재신청 회귀를 포함한 테스트 결과는 완료 기록에 연결합니다. 로컬 미커밋·main 미반영이며 운영 승인은 별도입니다.
- [상태 확인 계약 정비 PRD](health-check-prd.md)는 이슈 #30의 Actuator 통일, 정상/장애 상태와 공개 응답 보호, health 실패 기준선 해소 인수 기준을 정의합니다.
  `feature/30`에서 구현하고 전체 엄격 191개 실패·skip 0개를 확인했습니다. PR #31로 main에 반영됐습니다.
- [서비스 안내·약관·가입 동의 PRD](service-policy-prd.md)는 이슈 #28의 승인 문서 게시,
  가입 동의 버전·이력, 실패 처리와 운영 활성화 선행 조건을 정의합니다.
  [준비 기록](../exec-plans/completed/issue-28-policy-preparation.md)과 [PR #29](https://github.com/JooonWooKim/nanacocoa-service/pull/29)에서 구현·검증 범위를 확인합니다.
  PR #29로 main 반영을 완료했으며 정책 승인·운영 활성화는 별도입니다.
- [세션 보안 구현 PRD](session-security-prd.md)는 CSRF 발급·검증, 세션 ID 회전과 로그아웃 계약을 정의합니다.
- [결제 환경과 모의 PG PRD](payment-environment-prd.md)는 운영 결제 활성화, 비활성 상태의 API·화면, 모의 PG 검증 기준을 정의합니다.
- [주문 제출 멱등성 구현 PRD](order-submission-idempotency-prd.md)는 checkout
  재제출과 응답 복구, 중복 주문·재고 예약 방지 기준을 정의합니다.
- [미확정 결제·환불 운영 PRD](payment-operations-prd.md)는 관리자 거래 조회와
  개별 복구의 화면·API, 권한·경합 방어 및 인수 기준을 정의합니다.
- [마이페이지 주문 목록·상세 PRD](order-history-prd.md)는 본인 주문 조회,
  주문 당시 정보와 결제 결과 표시, 인수 테스트를 정의합니다.
- [배송관리 구현 PRD](shipping-management-prd.md)는 관리자 출고, 구매자
  배송 조회, 결제 취소와의 경계, 인수 기준을 정의합니다.
- [취소·반품·환불 PRD](cancellation-return-prd.md)은 구매자 신청,
  운영 승인·회수·검수, 기존 환불·복구·재입고 연결을 정의합니다.
  이슈 #26 구현은 PR #27로 main에 반영됐으며 기본 비활성입니다. 운영 정책 승인·출시 검증은 별도입니다.
- [마이페이지 PRD](../design-docs/PRD.md)는 회원 프로필·비밀번호 관리의
  인수 조건과 범위 경계를 정의합니다.
- [기술부채 NC-TEST-001](../exec-plans/tech-debt-tracker.md)은 현재 인증·상태
  확인 계약 충돌을 기록합니다.

사용자에게 보이는 새 기능은 구현 전이나 구현과 함께 인수 조건, 실패 동작,
인가, 데이터 영향, 연결된 테스트를 포함한 집중 명세를 추가해야 합니다.
