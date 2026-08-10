#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

HARNESS_RUN_ID=${HARNESS_RUN_ID:-"check-$(date -u '+%Y%m%dT%H%M%SZ')-$$"}
export HARNESS_RUN_ID
ensure_result_dirs
check_source_fingerprint=$(workspace_fingerprint)
failures=0
baselined=0
summary="$RESULT_ROOT/check-summary.txt"
: > "$summary"

run_gate() {
	label=$1
	shift
	log="$LOG_DIR/$label.log"
	if "$@" > "$log" 2>&1; then
		cat "$log"
		if grep -q '\[BASELINED\]' "$log"; then
			result=BASELINED
			baselined=$((baselined + 1))
		else
			result=PASS
		fi
	else
		code=$?
		cat "$log" >&2
		result=FAIL
		failures=$((failures + 1))
		printf '게이트 %s가 종료 코드 %s로 실패했습니다. 재실행: ./scripts/harness %s\n' "$label" "$code" "$label" >&2
	fi
	printf '%s|%s|%s\n' "$label" "$result" "$log" >> "$summary"
}

run_optional_gate() {
	label=$1
	shift
	log="$LOG_DIR/$label.log"
	if "$@" > "$log" 2>&1; then
		cat "$log"
		result=PASS
	else
		code=$?
		cat "$log"
		if [ "$code" -eq 2 ]; then
			result=SKIPPED
		else
			result=FAIL
			failures=$((failures + 1))
		fi
	fi
	printf '%s|%s|%s\n' "$label" "$result" "$log" >> "$summary"
}

run_optional_gate doctor "$HARNESS_COMMAND_DIR/doctor.sh"
run_gate architecture "$HARNESS_COMMAND_DIR/architecture.sh"
run_gate security "$HARNESS_COMMAND_DIR/security.sh"
run_gate migrations "$HARNESS_COMMAND_DIR/migrations.sh"
run_gate docs "$HARNESS_COMMAND_DIR/docs.sh"
run_gate harness-self-check "$HARNESS_COMMAND_DIR/harness-self-check.sh"
run_gate entropy "$HARNESS_COMMAND_DIR/entropy.sh"
run_optional_gate smoke "$HARNESS_COMMAND_DIR/smoke.sh"
run_gate test-baseline "$HARNESS_COMMAND_DIR/test-baseline.sh"
run_gate eval env HARNESS_REUSE_TEST_RESULTS=1 "$HARNESS_COMMAND_DIR/eval.sh"

if (cd "$PROJECT_DIR" && git diff --check) > "$LOG_DIR/git-diff-check.log" 2>&1; then
	printf 'git-diff-check|PASS|%s\n' "$LOG_DIR/git-diff-check.log" >> "$summary"
else
	cat "$LOG_DIR/git-diff-check.log" >&2
	printf 'git-diff-check|FAIL|%s\n' "$LOG_DIR/git-diff-check.log" >> "$summary"
	failures=$((failures + 1))
fi

if [ "$check_source_fingerprint" = "$(workspace_fingerprint)" ]; then
	printf 'workspace-stability|PASS|검사 중 소스·실행 파일 fingerprint 변경 없음\n' >> "$summary"
else
	printf 'workspace-stability|FAIL|검사 중 저장소 입력 변경됨\n' >> "$summary"
	printf '검사 실행 중 저장소 입력이 변경되었습니다. 안정된 작업 트리에서 다시 실행하세요.\n' >&2
	failures=$((failures + 1))
fi

if [ "$failures" -gt 0 ]; then
	record_status check FAIL "게이트 ${failures}개 실패; 요약: build/harness/check-summary.txt"
	"$HARNESS_COMMAND_DIR/evidence.sh" > "$LOG_DIR/evidence.log" 2>&1 || true
	status_line FAIL "check" "게이트 ${failures}개 실패; build/harness/check-summary.txt를 확인하세요" >&2
	exit 1
fi

if [ "$baselined" -gt 0 ]; then
	record_status check BASELINED "모든 게이트 유지; 게이트 ${baselined}개에 검토된 기준선 부채가 있음"
	"$HARNESS_COMMAND_DIR/evidence.sh" > "$LOG_DIR/evidence.log" 2>&1 || true
	status_line BASELINED "check" "새 회귀 없음; 엄격한 제품 테스트 모음에는 문서화된 부채가 남아 있음"
	exit 0
fi

record_status check PASS "모든 로컬 CI 게이트가 기준선 없이 통과"
"$HARNESS_COMMAND_DIR/evidence.sh" > "$LOG_DIR/evidence.log" 2>&1 || true
status_line PASS "check" "모든 로컬 CI 게이트 통과"
