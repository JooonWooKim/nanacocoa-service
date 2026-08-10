#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

if [ "$#" -gt 1 ]; then
	record_status generate-docs FAIL "인자가 너무 많음"
	fail_with_guidance "generate-docs" "인자가 너무 많습니다" "generate-docs 또는 generate-docs --check를 사용하세요."
fi

export LC_ALL=C
mode=${1:-write}
temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/nanacocoa-generated-docs.XXXXXX")
cleanup() {
	find "$temp_dir" -mindepth 1 -delete 2>/dev/null || true
	rmdir "$temp_dir" 2>/dev/null || true
}
trap cleanup EXIT HUP INT TERM

repo_output="$temp_dir/repository-map.md"
schema_output="$temp_dir/db-schema.md"

count_files() {
	directory=$1
	pattern=$2
	if [ ! -d "$directory" ]; then
		printf '0'
		return
	fi
	find "$directory" -type f -name "$pattern" | wc -l | tr -d ' '
}

generate_repository_map() {
	output=$1
	{
		printf '%s\n\n' '# 자동 생성 저장소 지도'
		printf '%s\n' '상태: 자동 생성'
		printf '%s\n' '원본: Java 소스·테스트 경로, resource, script, root build·배포 파일'
		printf '%s\n\n' '재생성: `./scripts/harness generate-docs`'
		printf '%s\n\n' '이 파일을 직접 수정하지 않습니다. docs 검사가 현재 소스 입력과 비교합니다.'
		printf '%s\n\n' '## 목록'
		printf '%s\n' '| 영역 | Java 파일 | 주요 역할 |'
		printf '%s\n' '| --- | ---: | --- |'
		domain_root="$PROJECT_DIR/src/main/java/com/nanacocoa/server"
		for domain_dir in "$domain_root"/*; do
			[ -d "$domain_dir" ] || continue
			domain=$(basename "$domain_dir")
			count=$(count_files "$domain_dir" '*.java')
			case "$domain" in
				common) role='공통 응답, 오류, 보안, 세션, 사용자 상세' ;;
				member) role='식별, 회원가입, 로그인, 세션' ;;
				products) role='상품 목록과 이미지 저장' ;;
				order) role='주문, 배송, 서버 가격 계산' ;;
				payment) role='승인, 취소, 락, 복구' ;;
				*) role='분류되지 않은 최상위 패키지, ARCHITECTURE.md에 기록 필요' ;;
			esac
			printf '| `%s` | %s | %s |\n' "$domain" "$count" "$role"
		done
		printf '\n- 주요 Java 파일: %s\n' "$(count_files "$PROJECT_DIR/src/main/java" '*.java')"
		printf '%s\n' "- 테스트 Java 파일: $(count_files "$PROJECT_DIR/src/test/java" '*.java')"
		printf '%s\n' "- Flyway 마이그레이션: $(count_files "$PROJECT_DIR/src/main/resources/db/migration" 'V*.sql')"
		printf '%s\n\n' "- 정적 자원·페이지: $(count_files "$PROJECT_DIR/src/main/resources/static" '*')"
		printf '%s\n\n' '## 주요 패키지 디렉터리'
		printf '%s\n' '```text'
		find "$PROJECT_DIR/src/main/java/com/nanacocoa/server" -type d |
			sed "s|$PROJECT_DIR/||" | LC_ALL=C sort
		printf '%s\n\n' '```'
		printf '%s\n\n' '## 테스트 클래스'
		printf '%s\n' '```text'
		find "$PROJECT_DIR/src/test/java" -type f -name '*.java' |
			sed "s|$PROJECT_DIR/||" | LC_ALL=C sort
		printf '%s\n\n' '```'
		printf '%s\n\n' '## 운영 진입점'
		[ -f "$PROJECT_DIR/gradlew" ] && [ -f "$PROJECT_DIR/build.gradle" ] &&
			printf '%s\n' '- 빌드: `./gradlew`, `build.gradle`'
		[ -x "$PROJECT_DIR/scripts/harness" ] &&
			printf '%s\n' '- 하네스: `./scripts/harness`'
		[ -f "$PROJECT_DIR/compose.yaml" ] && [ -f "$PROJECT_DIR/Dockerfile" ] &&
			printf '%s\n' '- 로컬 스택: `compose.yaml`, `Dockerfile`'
		[ -f "$PROJECT_DIR/scripts/init-env.sh" ] &&
			printf '%s\n' '- 환경 초기화: `scripts/init-env.sh`'
		[ -f "$PROJECT_DIR/src/main/resources/application.yml" ] &&
			printf '%s\n' '- 실행 환경 설정: `src/main/resources/application.yml`'
		[ -f "$PROJECT_DIR/AGENTS.md" ] && [ -f "$PROJECT_DIR/ARCHITECTURE.md" ] && [ -d "$PROJECT_DIR/docs" ] &&
			printf '%s\n' '- 지식 root: `AGENTS.md`, `ARCHITECTURE.md`, `docs/`'
	} > "$output"
}

generate_db_schema() {
	output=$1
	{
		printf '%s\n\n' '# 자동 생성 데이터베이스 스키마 이력'
		printf '%s\n' '상태: 자동 생성'
		printf '%s\n' '원본: `src/main/resources/db/migration` 아래 순서가 지정된 SQL 파일'
		printf '%s\n\n' '재생성: `./scripts/harness generate-docs`'
		printf '%s\n\n' '이 파일을 직접 수정하지 않습니다. 새 Flyway 마이그레이션을 추가하고 재생성합니다.'
		printf '%s\n\n' '아래 SQL은 버전 관리되는 운영 스키마 이력이며 실행 중인 데이터베이스 dump가 아닙니다.'
		ordered_migration_files "$PROJECT_DIR/src/main/resources/db/migration" |
		while IFS= read -r migration; do
			relative=${migration#"$PROJECT_DIR/"}
			printf '\n## `%s`\n\n' "$relative"
			printf '%s\n' '```sql'
			sed -n '1,$p' "$migration"
			printf '%s\n' '```'
		done
	} > "$output"
}

generate_repository_map "$repo_output"
generate_db_schema "$schema_output"

target_dir="$PROJECT_DIR/docs/generated"
case "$mode" in
	write)
		mkdir -p "$target_dir"
		mv "$repo_output" "$target_dir/repository-map.md"
		mv "$schema_output" "$target_dir/db-schema.md"
		record_status generate-docs PASS "repository-map.md와 db-schema.md를 생성함"
		status_line PASS "generate-docs" "결정적 생성 참조 문서를 갱신함"
		;;
	--check)
		failures=0
		for name in repository-map.md db-schema.md; do
			if [ ! -f "$target_dir/$name" ]; then
				status_line FAIL "생성 문서" "docs/generated/$name 파일이 없음" >&2
				failures=$((failures + 1))
			elif ! cmp -s "$temp_dir/$name" "$target_dir/$name"; then
				status_line FAIL "생성 문서" "docs/generated/$name 파일이 오래됨" >&2
				diff -u "$target_dir/$name" "$temp_dir/$name" >&2 || true
				failures=$((failures + 1))
			fi
		done
		if [ "$failures" -gt 0 ]; then
			record_status generate-docs FAIL "생성 참조 문서 ${failures}개가 없거나 오래됨"
			printf './scripts/harness generate-docs를 실행하고 결과를 검토한 뒤 ./scripts/harness docs를 다시 실행하세요.\n' >&2
			exit 1
		fi
		record_status generate-docs PASS "생성 참조 문서가 현재 소스 입력과 일치함"
		status_line PASS "생성 문서" "저장소·스키마 참조 문서가 최신임"
		;;
	*)
		record_status generate-docs FAIL "지원하지 않는 option: $mode"
		fail_with_guidance "generate-docs" "지원하지 않는 option: $mode" "generate-docs 또는 generate-docs --check를 사용하세요."
		;;
esac
