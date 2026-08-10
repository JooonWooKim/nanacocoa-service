#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

input_dir=${JUNIT_RESULT_DIR:-"$PROJECT_DIR/build/test-results/test"}
output_dir=${JUNIT_OUTPUT_DIR:-"$RESULT_ROOT/junit"}
raw_inventory=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-junit-inventory.XXXXXX")
raw_failures=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-junit-failures.XXXXXX")
raw_fingerprints=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-junit-fingerprints.XXXXXX")
raw_skipped=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-junit-skipped.XXXXXX")
violations=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-junit-violations.XXXXXX")
cleanup() {
	rm -f "$raw_inventory" "$raw_failures" "$raw_fingerprints" "$raw_skipped" "$violations"
}
trap cleanup EXIT HUP INT TERM

if [ ! -d "$input_dir" ]; then
	echo "JUnit 결과 디렉터리가 없습니다: $input_dir" >&2
	exit 1
fi

xml_count=$(find "$input_dir" -type f -name 'TEST-*.xml' | wc -l | tr -d ' ')
if [ "$xml_count" -eq 0 ]; then
	echo "$input_dir 아래에서 Gradle TEST-*.xml 파일을 찾지 못했습니다" >&2
	exit 1
fi

declared_tests=0
declared_failures=0
declared_errors=0
declared_skipped=0

find "$input_dir" -type f -name 'TEST-*.xml' | LC_ALL=C sort |
while IFS= read -r xml; do
	if ! grep -q '</testsuite>' "$xml"; then
		printf '%s: 불완전한 XML입니다. 닫는 testsuite가 없습니다\n' "$xml" >> "$violations"
	fi

	suite_line=$(grep '<testsuite ' "$xml" | head -n 1)
	tests=$(printf '%s\n' "$suite_line" | sed -n 's/.* tests="\([0-9][0-9]*\)".*/\1/p')
	skipped=$(printf '%s\n' "$suite_line" | sed -n 's/.* skipped="\([0-9][0-9]*\)".*/\1/p')
	failures=$(printf '%s\n' "$suite_line" | sed -n 's/.* failures="\([0-9][0-9]*\)".*/\1/p')
	errors=$(printf '%s\n' "$suite_line" | sed -n 's/.* errors="\([0-9][0-9]*\)".*/\1/p')
	if [ -z "$tests" ] || [ -z "$skipped" ] || [ -z "$failures" ] || [ -z "$errors" ]; then
		printf '%s: testsuite 개수 속성이 없거나 잘못되었습니다\n' "$xml" >> "$violations"
	fi

	awk \
		-v xml="$xml" \
		-v inventory="$raw_inventory" \
		-v failures_file="$raw_failures" \
		-v fingerprints="$raw_fingerprints" \
		-v skipped_file="$raw_skipped" \
		-v violation_file="$violations" '
		function attribute(line, key, value) {
			value = line
			sub(".* " key "=\"", "", value)
			if (value == line) {
				return ""
			}
			sub("\".*", "", value)
			return value
		}
		/<testcase name="/ {
			name = attribute($0, "name")
			classname = attribute($0, "classname")
			sub(/\(\)$/, "", name)
			current = classname "#" name
			if (name == "" || classname == "") {
				print xml ": testcase 식별자를 해석할 수 없습니다" >> violation_file
			} else {
				print current >> inventory
			}
		}
		/<failure / {
			type = attribute($0, "type")
			message = attribute($0, "message")
			print current >> failures_file
			print current "|FAILURE|" type "|" message >> fingerprints
		}
		/<error / {
			type = attribute($0, "type")
			message = attribute($0, "message")
			print current >> failures_file
			print current "|ERROR|" type "|" message >> fingerprints
		}
		/<skipped/ {
			print current >> skipped_file
		}
	' "$xml"
done

if [ -s "$violations" ]; then
	cat "$violations" >&2
	exit 1
fi

mkdir -p "$output_dir"
LC_ALL=C sort "$raw_inventory" > "$output_dir/inventory.raw.txt"
LC_ALL=C sort -u "$raw_inventory" > "$output_dir/inventory.txt"
LC_ALL=C sort -u "$raw_failures" > "$output_dir/failures.txt"
LC_ALL=C sort -u "$raw_fingerprints" > "$output_dir/failure-fingerprints.txt"
LC_ALL=C sort -u "$raw_skipped" > "$output_dir/skipped.txt"
LC_ALL=C sort "$raw_inventory" | uniq -d > "$output_dir/duplicates.txt"

if [ -s "$output_dir/duplicates.txt" ]; then
	echo "중복 testcase 식별자를 발견했습니다:" >&2
	cat "$output_dir/duplicates.txt" >&2
	exit 1
fi

declared_tests=$(find "$input_dir" -type f -name 'TEST-*.xml' -exec grep '<testsuite ' {} \; |
	sed -n 's/.* tests="\([0-9][0-9]*\)".*/\1/p' |
	awk '{sum += $1} END {print sum + 0}')
declared_skipped=$(find "$input_dir" -type f -name 'TEST-*.xml' -exec grep '<testsuite ' {} \; |
	sed -n 's/.* skipped="\([0-9][0-9]*\)".*/\1/p' |
	awk '{sum += $1} END {print sum + 0}')
declared_failures=$(find "$input_dir" -type f -name 'TEST-*.xml' -exec grep '<testsuite ' {} \; |
	sed -n 's/.* failures="\([0-9][0-9]*\)".*/\1/p' |
	awk '{sum += $1} END {print sum + 0}')
declared_errors=$(find "$input_dir" -type f -name 'TEST-*.xml' -exec grep '<testsuite ' {} \; |
	sed -n 's/.* errors="\([0-9][0-9]*\)".*/\1/p' |
	awk '{sum += $1} END {print sum + 0}')

actual_tests=$(wc -l < "$output_dir/inventory.raw.txt" | tr -d ' ')
actual_skipped=$(wc -l < "$output_dir/skipped.txt" | tr -d ' ')
actual_failures=$(wc -l < "$output_dir/failures.txt" | tr -d ' ')
expected_failed=$((declared_failures + declared_errors))

if [ "$declared_tests" -ne "$actual_tests" ] ||
	[ "$declared_skipped" -ne "$actual_skipped" ] ||
	[ "$expected_failed" -ne "$actual_failures" ]; then
	printf 'JUnit 개수 불일치: 선언 tests=%s skipped=%s failed=%s; 해석 tests=%s skipped=%s failed=%s\n' \
		"$declared_tests" "$declared_skipped" "$expected_failed" \
		"$actual_tests" "$actual_skipped" "$actual_failures" >&2
	exit 1
fi

printf 'tests=%s failures=%s skipped=%s xml_files=%s\n' \
	"$actual_tests" "$actual_failures" "$actual_skipped" "$xml_count" \
	> "$output_dir/summary.txt"
