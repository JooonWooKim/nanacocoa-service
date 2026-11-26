# 격리 MySQL 8.4 품질 검사 구현

상태: 완료 (로컬 구현 및 검증)

## 목표

[이슈 #54](https://github.com/JooonWooKim/nanacocoa-service/issues/54)의 검사를 로컬과 GitHub에서 같은 명령으로 실행한다.

## 하지 않는 일

운영 DB·외부 제공자 접속, 운영 설정, 브랜치 보호, 속한 작업과 무관한 기준선 변경은 범위 밖이다.

## 현재 증거

원격 main `cd8cb8a`에 [준비 PRD](../../product-specs/mysql-quality-gate-prd.md)가 반영됐다. 현재 GitHub 품질 검사는 기존 H2 경로를 실행한다.

## 결정

독립 Gradle 테스트 작업과 별도 GitHub 작업을 사용한다. MySQL은 실행별 임시 컨테이너와 보고서로 격리한다.

## 작업 분해

- [x] 사용자 파일 백업·대조와 최신 main fast-forward
- [x] 로컬 검사 명령과 GitHub 작업 구현
- [x] MySQL·H2·실패 경로 및 문서 검증

## 진행 상황

- 2026-11-26: 기존 파일 16개 백업 후 `feature/54`를 `cd8cb8a`로 fast-forward했고 해시가 같았다.
- 2026-11-26: 공용 Spring 설정과 독립 Gradle 작업으로 로컬·CI 공통 MySQL 명령을 구현했다. 실제 MySQL과 H2를 분리해 검증했다.

## 명령과 증거

| 명령 | 상태 | 증거 |
| --- | --- | --- |
| `git merge --ff-only origin/main` | PASS | 사용자 파일 16개가 전환 전과 동일하다. |
| `./scripts/harness mysql-quality` | PASS | 깨끗한 스냅샷에서 MySQL 8.4.10에 연결했다. Flyway 이행·JPA 및 주문·재고·결제·반품 67개 통과. 실패·skip·누락 0. |
| `./scripts/harness mysql-quality` 중단·Docker 장애 | PASS | 실제 SIGTERM 이후 실패로 분류하고 생성한 컨테이너를 제거했다. 사용할 수 없는 소켓은 BLOCKED와 종료 코드 2. |
| `./gradlew mysqlQualityTest` 격리 설정 누락 | PASS | 테스트 시작 전에 실패하고 H2를 실행하지 않았다. |
| `python3 -B -m unittest discover -s src/test/python` | PASS | 18개 통과. 새 검증 7개에서 준비 시간 초과, 정리 실패, 미발견·실패·skip, 민감 진단 값 차단과 로컬 Docker 범위를 확인했다. |
| `./scripts/harness check` | BASELINED | 공용 설정만 있는 스냅샷에서 기존 H2 Java 265개와 평가 63개 통과. 기존 구조·보안 예외 유지. |
| `./scripts/harness docs` · `git diff --check` | PASS | 문서, 링크, 공백 검사 통과. |
| GitHub MySQL 작업 | BLOCKED | YAML 구조와 공통 명령을 확인했다. 원격 변경 반영 후 실제 실행과 실패 요약 업로드를 확인한다. |
| Compose runtime smoke | SKIPPED | 전체 하네스에서 Compose 앱을 기동하지 않았다. 실제 MySQL 검증은 별도로 실행했다. |

## 위험과 롤백

실행 범위는 고유한 컨테이너와 비식별 요약이다. 이 작업의 하네스·테스트 작업·workflow 변경만 되돌릴 수 있다.

## 미해결 항목

원격 GitHub의 새 작업은 변경을 반영한 뒤 확인한다.

## 완료 조건

실제 MySQL과 H2를 각각 검증하고 실패·중단 처리와 출시 문서를 기록한다.
