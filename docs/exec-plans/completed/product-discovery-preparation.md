# 상품 검색·카테고리 관리 준비

상태: 완료 · 기능 준비

## 목표

원격 main을 기반으로 상품 검색·카테고리 PRD, 이슈, 추적 브랜치와 준비 검증 증거를 확보한다.

## 하지 않는 일

기능 구현·인수 검증, 커밋·구현 푸시·PR 생성, 실데이터 접근 및 운영 활성화.

## 현재 증거

main 8b9eaae에 PR #39 반영 확인. 기존 상품 목록은 전체 배열이고 카테고리 저장 필드는 없다. 중복 이슈와 열린 PR은 없었다.

## 결정

상품당 분류 한 개, 숨김·복구 시 기존 상품 연결 보존, 기존 상품은 미분류로 이행한다. FAQ·문의와 주문 상태 알림은 후속이다.

## 작업 분해

- [x] 조회·초안·원본 백업
- [x] 이슈·브랜치·전환
- [x] PRD와 제품·출시 링크 갱신
- [x] 검사와 증거 보관

## 진행 상황

- 2026-11-23: [이슈 #40](https://github.com/JooonWooKim/nanacocoa-service/issues/40) 생성, feature/40 → origin/feature/40 전환. 전환 전후 13개 파일 해시 일치. PRD와 제품·출시 문서 연결.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git fetch / git switch / SHA-256` | PASS | main 8b9eaae, 전환 후 13개 동일. 백업과 manifest는 /private/tmp/product-discovery-prep-s9s4k46r 에 보관. |
| `./scripts/harness docs`, `git diff --check` | PASS | 링크·지식 구조와 공백 검사 통과 |
| `./scripts/harness check` | BASELINED | exit 0. 기존 아키텍처 예외 3개, 보안 예외 2개 유지. 새 회귀 없음. |
| `check: test-baseline / migrations / eval / self-check / entropy` | PASS | Java 237개, 실패·오류·skip 0개. 기존 마이그레이션과 기준선 유지. |
| `check: doctor / smoke` | SKIPPED | 비공개 환경 내용 미조회, 실행 중인 Compose 앱 없음. |
| `최종 SHA-256 / 공동 편집 diff` | PASS | 13개 중 11개 바이트 동일, 출시 PRD·인덱스 2개는 이번 추가분만 확인. |
| `DISCOVERY-01~06` | SKIPPED | 기능 구현 후 실행. |

[상품 검색·카테고리 관리 PRD](../../product-specs/product-discovery-prd.md)와 로컬 증거의 check.log, check-summary.txt, java-summary.json, preservation-final.json, preparation.diff를 참조한다.

## 위험과 롤백

공동 편집 문서는 원본과 추가분을 비교한다. 원본 확인을 위해 전체 파일 복원이나 강제 전환을 하지 않는다.

## 미해결 항목

DISCOVERY-01~06 구현·인수 검증 및 운영 반영은 후속이다.

## 완료 조건

실제 이슈·추적 브랜치·PRD 연결, 문서·diff·check 분류와 원본 대조 후 보관한다.
