#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

violations=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-harness-self.XXXXXX")
cleanup() {
	rm -f "$violations"
}
trap cleanup EXIT HUP INT TERM

if [ ! -x "$PROJECT_DIR/scripts/harness" ]; then
	printf 'scripts/harness를 실행할 수 없습니다. chmod +x scripts/harness를 실행하세요\n' >> "$violations"
fi

find "$PROJECT_DIR/scripts" -type f \( -name '*.sh' -o -name 'harness' \) | sort |
while IFS= read -r file; do
	if ! sh -n "$file" 2>/dev/null; then
		printf '%s: shell 문법 검사 실패\n' "${file#"$PROJECT_DIR/"}" >> "$violations"
	fi
	if [ ! -s "$file" ]; then
		printf '%s: script가 비어 있음\n' "${file#"$PROJECT_DIR/"}" >> "$violations"
	fi
done

fixture_output=$(mktemp -d "${TMPDIR:-/tmp}/nanacocoa-junit-fixture.XXXXXX")
if ! JUNIT_RESULT_DIR="$PROJECT_DIR/harness/fixtures/junit" JUNIT_OUTPUT_DIR="$fixture_output" \
	"$PROJECT_DIR/scripts/harness.d/junit-results.sh" >/dev/null 2>&1; then
	printf 'JUnit 결과 parser가 PASS/FAIL/SKIPPED fixture 검증에 실패함\n' >> "$violations"
else
	if [ "$(wc -l < "$fixture_output/inventory.txt" | tr -d ' ')" -ne 3 ]; then
		printf 'JUnit 결과 parser fixture의 inventory 개수가 3이 아님\n' >> "$violations"
	fi
	if ! grep -Fxq 'harness.fixture.ParserFixture#fails' "$fixture_output/failures.txt"; then
		printf 'JUnit 결과 parser가 fixture 실패를 분류하지 못함\n' >> "$violations"
	fi
	if ! grep -Fxq 'harness.fixture.ParserFixture#skips' "$fixture_output/skipped.txt"; then
		printf 'JUnit 결과 parser가 fixture skip을 분류하지 못함\n' >> "$violations"
	fi
fi
find "$fixture_output" -mindepth 1 -delete 2>/dev/null || true
rmdir "$fixture_output" 2>/dev/null || true

duplicate_fixture=$(mktemp -d "${TMPDIR:-/tmp}/nanacocoa-junit-duplicate.XXXXXX")
cp "$PROJECT_DIR/harness/fixtures/junit/TEST-harness-fixture.xml" \
	"$duplicate_fixture/TEST-harness-fixture-a.xml"
cp "$PROJECT_DIR/harness/fixtures/junit/TEST-harness-fixture.xml" \
	"$duplicate_fixture/TEST-harness-fixture-b.xml"
duplicate_output=$(mktemp -d "${TMPDIR:-/tmp}/nanacocoa-junit-duplicate-output.XXXXXX")
if JUNIT_RESULT_DIR="$duplicate_fixture" JUNIT_OUTPUT_DIR="$duplicate_output" \
	"$PROJECT_DIR/scripts/harness.d/junit-results.sh" >/dev/null 2>&1; then
	printf 'JUnit 결과 parser가 중복 testcase 식별자를 허용함\n' >> "$violations"
fi
find "$duplicate_fixture" "$duplicate_output" -mindepth 1 -delete 2>/dev/null || true
rmdir "$duplicate_fixture" "$duplicate_output" 2>/dev/null || true

migration_fixture=$(mktemp -d "${TMPDIR:-/tmp}/nanacocoa-migration-order.XXXXXX")
for migration_name in V10__ten.sql V2__two.sql V1__one.sql; do
	: > "$migration_fixture/$migration_name"
done
ordered_names=$(ordered_migration_files "$migration_fixture" | sed 's|^.*/||' | tr '\n' ' ')
if [ "$ordered_names" != "V1__one.sql V2__two.sql V10__ten.sql " ]; then
	printf '숫자 기준 migration 정렬 실패: %s\n' "$ordered_names" >> "$violations"
fi
find "$migration_fixture" -mindepth 1 -delete 2>/dev/null || true
rmdir "$migration_fixture" 2>/dev/null || true

if ! "$PROJECT_DIR/scripts/harness" help >/dev/null 2>&1; then
	printf 'scripts/harness help 실패\n' >> "$violations"
fi

if "$PROJECT_DIR/scripts/harness" definitely-not-a-command >/dev/null 2>&1; then
	printf 'scripts/harness가 유효하지 않은 명령을 허용함\n' >> "$violations"
fi

if "$PROJECT_DIR/scripts/harness" help unexpected-argument >/dev/null 2>&1; then
	printf 'scripts/harness help가 예상하지 않은 인자를 허용함\n' >> "$violations"
fi

if "$PROJECT_DIR/scripts/harness" docs unexpected-argument >/dev/null 2>&1; then
	printf 'scripts/harness docs가 예상하지 않은 인자를 허용함\n' >> "$violations"
fi

if "$PROJECT_DIR/scripts/harness" generate-docs --check unexpected-argument >/dev/null 2>&1; then
	printf 'scripts/harness generate-docs가 추가 인자를 허용함\n' >> "$violations"
fi

if "$PROJECT_DIR/scripts/harness" migrations --accept-new unexpected-argument >/dev/null 2>&1; then
	printf 'scripts/harness migrations가 추가 인자를 허용함\n' >> "$violations"
fi

if "$PROJECT_DIR/scripts/harness" test-baseline --from-results unexpected-argument >/dev/null 2>&1; then
	printf 'scripts/harness test-baseline이 추가 인자를 허용함\n' >> "$violations"
fi

for command in doctor bootstrap check test test-unit test-integration architecture docs eval smoke evidence clean security migrations generate-docs entropy test-baseline; do
	if ! "$PROJECT_DIR/scripts/harness" help | grep -qE "^  $command([[:space:]]|$)"; then
		printf 'help 출력에 명령이 문서화되어 있지 않음: %s\n' "$command" >> "$violations"
	fi
done

workspace_input_files |
while IFS= read -r file; do
	case "$file" in
		*.jar)
			continue
			;;
	esac
	if grep -nE '[[:blank:]]+$' "$file" >/dev/null 2>&1; then
		printf '%s: 줄 끝 공백 발견\n' "${file#"$PROJECT_DIR/"}" >> "$violations"
	fi
done

if [ -s "$violations" ]; then
	cat "$violations" >&2
	record_status harness-self-check FAIL "하네스 인터페이스 또는 script 품질 위반"
	printf '나열된 파일을 수정한 뒤 ./scripts/harness check를 다시 실행하세요.\n' >&2
	exit 1
fi

record_status harness-self-check PASS "dispatcher, 문법, parser fixture, help, 유효하지 않은 입력, 저장소 텍스트 공백 검사 통과"
status_line PASS "harness-self-check" "명령 인터페이스, parser fixture, 저장소 텍스트가 내부적으로 일관됨"
