#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

if [ "$#" -gt 1 ]; then
	record_status test-baseline FAIL "인자가 너무 많음"
	fail_with_guidance "test-baseline" "인자가 너무 많습니다" "test-baseline 또는 test-baseline --from-results를 사용하세요."
fi

FAILURE_BASELINE="$PROJECT_DIR/harness/baselines/test-failures.txt"
FINGERPRINT_BASELINE="$PROJECT_DIR/harness/baselines/test-failure-fingerprints.txt"
INVENTORY_BASELINE="$PROJECT_DIR/harness/baselines/test-inventory.txt"
RESULT_DIR="$PROJECT_DIR/build/test-results/test"
PARSED_DIR="$RESULT_ROOT/junit"
RESULT_FINGERPRINT="$RESULT_DIR/.harness-source-fingerprint"
mode=${1:-run}
expected_fingerprints=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-test-fingerprints.XXXXXX")
violations=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-test-violations.XXXXXX")
cleanup() {
	rm -f "$expected_fingerprints" "$violations"
}
trap cleanup EXIT HUP INT TERM

for baseline in "$FAILURE_BASELINE" "$FINGERPRINT_BASELINE" "$INVENTORY_BASELINE"; do
	if [ ! -f "$baseline" ]; then
		record_status test-baseline FAIL "필수 테스트 기준선이 없음"
		fail_with_guidance "test-baseline" "$baseline 파일이 없습니다" "검토된 테스트 목록·실패 기준선을 복구하세요."
	fi
done

gradle_status=0
if [ "$mode" = "run" ]; then
	ensure_result_dirs
	test_source_fingerprint=$(workspace_fingerprint)
	if (cd "$PROJECT_DIR" && ./gradlew test) > "$LOG_DIR/gradle-test.log" 2>&1; then
		gradle_status=0
	else
		gradle_status=$?
	fi
	if [ "$gradle_status" -ne 0 ] &&
		! grep -q 'There were failing tests' "$LOG_DIR/gradle-test.log"; then
		record_status test-baseline FAIL "Gradle 인프라·build 실패로 테스트 XML을 신뢰할 수 없음"
		tail -n 40 "$LOG_DIR/gradle-test.log" >&2
		fail_with_guidance "test-baseline" "정상 테스트 실패 결과 전에 Gradle이 ${gradle_status}로 종료됨" "Gradle 오류를 해결한 뒤 ./scripts/harness test-baseline을 다시 실행하세요."
	fi
	if [ "$test_source_fingerprint" != "$(workspace_fingerprint)" ]; then
		record_status test-baseline FAIL "테스트 실행 중 저장소 입력이 변경됨"
		fail_with_guidance "test-baseline" "Gradle 실행 중 소스 fingerprint가 변경됨" "동시 편집을 중지하고 전체 테스트 기준선을 다시 실행하세요."
	fi
	printf '%s\n' "$test_source_fingerprint" > "$RESULT_FINGERPRINT"
elif [ "$mode" != "--from-results" ]; then
	record_status test-baseline FAIL "지원하지 않는 option: $mode"
	fail_with_guidance "test-baseline" "지원하지 않는 option" "test-baseline 또는 test-baseline --from-results를 사용하세요."
fi

if [ ! -f "$RESULT_FINGERPRINT" ] ||
	[ "$(sed -n '1p' "$RESULT_FINGERPRINT" 2>/dev/null)" != "$(workspace_fingerprint)" ]; then
	record_status test-baseline FAIL "JUnit XML이 현재 저장소 fingerprint에 결합되지 않음"
	fail_with_guidance "test-baseline" "테스트 결과가 오래되었거나 결과 fingerprint 도입 전 생성됨" "--from-results 없이 ./scripts/harness test-baseline을 실행하세요."
fi

if [ ! -d "$RESULT_DIR" ]; then
	record_status test-baseline FAIL "Gradle XML 결과를 사용할 수 없음"
	fail_with_guidance "test-baseline" "테스트 결과 디렉터리가 없습니다" "./gradlew test를 실행하고 build/reports/tests/test/를 확인하세요."
fi

if ! JUNIT_RESULT_DIR="$RESULT_DIR" JUNIT_OUTPUT_DIR="$PARSED_DIR" \
	"$HARNESS_COMMAND_DIR/junit-results.sh"; then
	record_status test-baseline FAIL "JUnit XML이 불완전·중복되었거나 내부적으로 불일치함"
	fail_with_guidance "test-baseline" "JUnit 결과 parsing 실패" "build/test-results/test를 확인하고 ./scripts/harness test-baseline을 다시 실행하세요."
fi

if ! cmp -s "$INVENTORY_BASELINE" "$PARSED_DIR/inventory.txt"; then
	status_line FAIL "테스트 목록" "전체 발견 테스트 집합이 변경되었거나 부분 결과가 생성됨" >&2
	diff -u "$INVENTORY_BASELINE" "$PARSED_DIR/inventory.txt" >&2 || true
	printf '추가·삭제·이름 변경된 테스트를 검토하고 전체 실행 후에만 test-inventory.txt를 갱신하세요.\n' >> "$violations"
fi

if [ -s "$PARSED_DIR/skipped.txt" ]; then
	status_line FAIL "테스트 skip" "검토된 테스트 모음에서 비활성·skip 테스트를 허용하지 않음" >&2
	cat "$PARSED_DIR/skipped.txt" >&2
	printf '@Disabled·skip을 제거하거나 별도로 검토된 정책을 문서화하세요. 현재 기준선은 skip 0개만 허용합니다.\n' >> "$violations"
fi

if ! cmp -s "$FAILURE_BASELINE" "$PARSED_DIR/failures.txt"; then
	status_line FAIL "테스트 기준선" "실패 집합이 변경됨, 추가·해결 항목을 모두 검토해야 함" >&2
	diff -u "$FAILURE_BASELINE" "$PARSED_DIR/failures.txt" >&2 || true
	printf '실패 ID가 변경되었습니다. 회귀를 수정하거나 검증된 해결 항목만 제거하세요.\n' >> "$violations"
fi

grep -v '^#' "$FINGERPRINT_BASELINE" | sed '/^[[:space:]]*$/d' > "$expected_fingerprints"
if ! cmp -s "$expected_fingerprints" "$PARSED_DIR/failure-fingerprints.txt"; then
	status_line FAIL "실패 fingerprint" "실패 종류·타입·메시지가 변경됨" >&2
	diff -u "$expected_fingerprints" "$PARSED_DIR/failure-fingerprints.txt" >&2 || true
	printf '알려진 테스트 식별자는 같지만 실패 의미가 변경되었습니다. 수락 전에 조사하세요.\n' >> "$violations"
fi

if [ -s "$violations" ]; then
	cat "$violations" >&2
	if [ -f "$LOG_DIR/gradle-test.log" ]; then
		printf '\n마지막 Gradle 테스트 출력:\n' >&2
		tail -n 30 "$LOG_DIR/gradle-test.log" >&2
	fi
	record_status test-baseline FAIL "목록, skip, 실패 ID 또는 fingerprint 래칫 변경"
	exit 1
fi

baseline_count=$(wc -l < "$FAILURE_BASELINE" | tr -d ' ')
if [ "$baseline_count" -gt 0 ]; then
	test_count=$(wc -l < "$INVENTORY_BASELINE" | tr -d ' ')
	status_line BASELINED "테스트 모음" "테스트 ${test_count}개, skip 0개, 정확한 fingerprint 실패 ${baseline_count}개, 엄격 테스트는 FAIL 유지"
	record_status test-baseline BASELINED "전체 테스트 ${test_count}개, skip 0개, 알려진 실패 fingerprint ${baseline_count}개 변경 없음"
	exit 0
fi

if [ "$gradle_status" -ne 0 ]; then
	record_status test-baseline FAIL "파싱된 테스트 실패 없이 Gradle 실패, $LOG_DIR/gradle-test.log 확인 필요"
	fail_with_guidance "test-baseline" "테스트 실패 없이 Gradle이 ${gradle_status}로 종료됨" "build/harness/logs/gradle-test.log를 확인하세요."
fi

record_status test-baseline PASS "전체 Gradle 테스트 통과"
status_line PASS "테스트 모음" "모든 테스트가 통과하고 기준선이 남아 있지 않음"
