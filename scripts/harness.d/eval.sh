#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

CATALOG="$PROJECT_DIR/harness/evals/catalog.tsv"
BASELINE="$PROJECT_DIR/harness/baselines/test-failures.txt"
PARSED_RESULTS="$RESULT_ROOT/junit"
EVAL_DIR="$RESULT_ROOT/eval"
mkdir -p "$EVAL_DIR"

if [ "${HARNESS_REUSE_TEST_RESULTS:-0}" != "1" ]; then
	if ! "$HARNESS_COMMAND_DIR/test-baseline.sh"; then
		record_status eval FAIL "테스트 기준선 검증 실패"
		exit 1
	fi
else
	if ! "$HARNESS_COMMAND_DIR/test-baseline.sh" --from-results; then
		record_status eval FAIL "재사용한 테스트 결과가 기준선과 일치하지 않음"
		exit 1
	fi
fi

if [ ! -f "$CATALOG" ]; then
	record_status eval FAIL "평가 카탈로그 없음"
	fail_with_guidance "eval" "$CATALOG 파일이 없습니다" "평가 카탈로그를 복구하세요."
fi

results="$EVAL_DIR/results.tsv"
printf 'id\tstatus\trisk\ttest_id\tsuccess_criterion\tdocumentation\n' > "$results"
pass_count=0
baseline_count=0
blocked_count=0
catalog_seen=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-eval-catalog.XXXXXX")
test_seen=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-eval-tests.XXXXXX")
cleanup() {
	rm -f "$catalog_seen" "$test_seen"
}
trap cleanup EXIT HUP INT TERM

while IFS='|' read -r id risk test_id success docs; do
	case "$id" in
		''|\#*)
			continue
			;;
	esac
	if [ -z "$risk" ] || [ -z "$test_id" ] || [ -z "$success" ] || [ -z "$docs" ]; then
		status=BLOCKED
		blocked_count=$((blocked_count + 1))
	elif grep -Fxq "$id" "$catalog_seen"; then
		status=BLOCKED
		blocked_count=$((blocked_count + 1))
	elif grep -Fxq "$test_id" "$test_seen"; then
		status=BLOCKED
		blocked_count=$((blocked_count + 1))
	elif [ ! -f "$PROJECT_DIR/$docs" ]; then
		status=BLOCKED
		blocked_count=$((blocked_count + 1))
	elif ! grep -Fxq "$test_id" "$PARSED_RESULTS/inventory.txt"; then
		status=BLOCKED
		blocked_count=$((blocked_count + 1))
	elif grep -Fxq "$test_id" "$PARSED_RESULTS/skipped.txt"; then
		status=BLOCKED
		blocked_count=$((blocked_count + 1))
	elif grep -Fxq "$test_id" "$BASELINE"; then
		status=BASELINED
		baseline_count=$((baseline_count + 1))
	elif grep -Fxq "$test_id" "$PARSED_RESULTS/failures.txt"; then
		status=FAIL
		blocked_count=$((blocked_count + 1))
	else
		status=PASS
		pass_count=$((pass_count + 1))
	fi
	printf '%s\n' "$id" >> "$catalog_seen"
	printf '%s\n' "$test_id" >> "$test_seen"
	printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$id" "$status" "$risk" "$test_id" "$success" "$docs" >> "$results"
	status_line "$status" "$id" "$risk"
done < "$CATALOG"

printf 'PASS=%s BASELINED=%s BLOCKED=%s\n' "$pass_count" "$baseline_count" "$blocked_count" \
	> "$EVAL_DIR/summary.txt"

if [ "$blocked_count" -gt 0 ]; then
	record_status eval FAIL "카탈로그 테스트 ${blocked_count}개에 현재 결과가 없음; build/harness/eval/results.tsv를 확인하세요"
	exit 1
fi

if [ "$baseline_count" -gt 0 ]; then
	record_status eval BASELINED "${pass_count}개 통과; 알려진 실패 위험 평가 ${baseline_count}개"
	status_line BASELINED "eval" "기계 판독 가능 결과: build/harness/eval/results.tsv"
	exit 0
fi

record_status eval PASS "위험 평가 ${pass_count}개 통과"
status_line PASS "eval" "기계 판독 가능 결과: build/harness/eval/results.tsv"
