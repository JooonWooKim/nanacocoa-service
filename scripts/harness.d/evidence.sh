#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

ensure_result_dirs
bundle_id="$(date -u '+%Y%m%dT%H%M%SZ')-$$"
bundle="$RESULT_ROOT/evidence/$bundle_id"
mkdir -p "$bundle/status" "$bundle/artifacts"

revision=$(cd "$PROJECT_DIR" && git rev-parse --verify HEAD 2>/dev/null || printf 'UNBORN')
branch=$(cd "$PROJECT_DIR" && git branch --show-current 2>/dev/null || printf 'UNKNOWN')
fingerprint=$(workspace_fingerprint)
target_run_id=${HARNESS_RUN_ID:-}
if [ -z "$target_run_id" ] && [ -f "$STATUS_DIR/check.status" ]; then
	check_fingerprint=$(awk -F'|' 'NR == 1 {print $3}' "$STATUS_DIR/check.status")
	if [ "$check_fingerprint" = "$fingerprint" ]; then
		target_run_id=$(awk -F'|' 'NR == 1 {print $4}' "$STATUS_DIR/check.status")
	fi
fi
target_run_id=${target_run_id:-"no-current-coordinated-run"}

json_escape() {
	printf '%s' "$1" |
	awk '{
		gsub(/\\/, "\\\\")
		gsub(/"/, "\\\"")
		printf "%s", $0
	}'
}

fresh_statuses=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-evidence-fresh.XXXXXX")
stale_statuses=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-evidence-stale.XXXXXX")
cleanup() {
	rm -f "$fresh_statuses" "$stale_statuses"
}
trap cleanup EXIT HUP INT TERM

find "$STATUS_DIR" -type f -name '*.status' | LC_ALL=C sort |
while IFS= read -r status_file; do
	status_fingerprint=$(awk -F'|' 'NR == 1 {print $3}' "$status_file")
	status_run_id=$(awk -F'|' 'NR == 1 {print $4}' "$status_file")
	if [ "$status_fingerprint" = "$fingerprint" ] && [ "$status_run_id" = "$target_run_id" ]; then
		printf '%s\n' "$status_file" >> "$fresh_statuses"
	else
		printf '%s\n' "$status_file" >> "$stale_statuses"
	fi
done

{
	printf 'Nanacocoa 하네스 증거\n'
	printf '수집_UTC=%s\n' "$(utc_now)"
	printf '저장소=%s\n' "$PROJECT_DIR"
	printf '브랜치=%s\n' "$branch"
	printf '리비전=%s\n' "$revision"
	printf '작업공간_fingerprint=%s\n' "$fingerprint"
	printf '조정_실행_ID=%s\n' "$target_run_id"
	printf '엄격한_테스트_보고서=%s\n' "$PROJECT_DIR/build/reports/tests/test/index.html"
	printf '\n이 소스 상태와 조정 실행에 대해 입증된 상태\n'
	if [ -s "$fresh_statuses" ]; then
		while IFS= read -r status_file; do
			printf '%s=' "$(basename "$status_file" .status)"
			sed -n '1p' "$status_file"
			cp "$status_file" "$bundle/status/"
		done < "$fresh_statuses"
	else
		printf '없음=이 소스 fingerprint와 실행 ID에 일치하는 하네스 명령 상태가 없습니다.\n'
	fi
	printf '\n입증에서 제외한 오래된 상태\n'
	if [ -s "$stale_statuses" ]; then
		while IFS= read -r status_file; do
			printf '%s\n' "$(basename "$status_file")"
		done < "$stale_statuses"
	else
		printf '없음\n'
	fi
	printf '\n작업 트리 경로(내용은 수집하지 않음)\n'
	(cd "$PROJECT_DIR" && git status --short 2>/dev/null) || true
} > "$bundle/summary.txt"

{
	printf '{\n'
	printf '  "collected_utc": "%s",\n' "$(utc_now)"
	printf '  "branch": "%s",\n' "$(json_escape "$branch")"
	printf '  "revision": "%s",\n' "$(json_escape "$revision")"
	printf '  "workspace_fingerprint": "%s",\n' "$fingerprint"
	printf '  "coordinated_run_id": "%s",\n' "$(json_escape "$target_run_id")"
	printf '  "summary": "build/harness/evidence/%s/summary.txt"\n' "$bundle_id"
	printf '}\n'
} > "$bundle/summary.json"

if grep -Fxq "$STATUS_DIR/check.status" "$fresh_statuses" &&
	[ -f "$RESULT_ROOT/check-summary.txt" ]; then
	cp "$RESULT_ROOT/check-summary.txt" "$bundle/artifacts/check-summary.txt"
fi
if grep -Fxq "$STATUS_DIR/eval.status" "$fresh_statuses"; then
	[ ! -f "$RESULT_ROOT/eval/results.tsv" ] ||
		cp "$RESULT_ROOT/eval/results.tsv" "$bundle/artifacts/eval-results.tsv"
	[ ! -f "$RESULT_ROOT/eval/summary.txt" ] ||
		cp "$RESULT_ROOT/eval/summary.txt" "$bundle/artifacts/eval-summary.txt"
fi
if grep -Fxq "$STATUS_DIR/test-baseline.status" "$fresh_statuses" &&
	[ -f "$RESULT_ROOT/junit/summary.txt" ]; then
	cp "$RESULT_ROOT/junit/summary.txt" "$bundle/artifacts/junit-summary.txt"
fi

record_status evidence PASS "비식별화한 증거 묶음: build/harness/evidence/$bundle_id"
status_line PASS "evidence" "build/harness/evidence/$bundle_id"
