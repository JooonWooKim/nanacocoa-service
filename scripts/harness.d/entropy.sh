#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

report_dir="$RESULT_ROOT/entropy"
mkdir -p "$report_dir"
report="$report_dir/report.txt"
detail_dir="$report_dir/details"
mkdir -p "$detail_dir"

count_matches() {
	pattern=$1
	shift
	count=$(grep -R -E -n "$pattern" "$@" \
		--exclude-dir=build --exclude-dir=.gradle --exclude=entropy.sh --exclude=docs.sh \
		2>/dev/null | wc -l | tr -d ' ')
	printf '%s' "$count"
}

test_baseline_count=$(wc -l < "$PROJECT_DIR/harness/baselines/test-failures.txt" | tr -d ' ')
architecture_baseline_count=$(wc -l < "$PROJECT_DIR/harness/baselines/architecture-exceptions.txt" | tr -d ' ')
security_baseline_count=$(wc -l < "$PROJECT_DIR/harness/baselines/security-exceptions.txt" | tr -d ' ')
active_plans=$(find "$PROJECT_DIR/docs/exec-plans/active" -type f -name '*.md' | wc -l | tr -d ' ')
debt_items=$(grep -c '^| NC-' "$PROJECT_DIR/docs/exec-plans/tech-debt-tracker.md" 2>/dev/null || true)
todo_count=$(count_matches 'TODO|FIXME|HACK' "$PROJECT_DIR/src" "$PROJECT_DIR/scripts" "$PROJECT_DIR/docs")
disabled_tests=$(count_matches '@Disabled|xdescribe|xit\(' "$PROJECT_DIR/src/test")
today=$(date '+%Y-%m-%d')

grep -R -E -n 'TODO|FIXME|HACK' \
	"$PROJECT_DIR/src" "$PROJECT_DIR/scripts" "$PROJECT_DIR/docs" \
	--exclude-dir=build --exclude-dir=.gradle --exclude=entropy.sh --exclude=docs.sh \
	2>/dev/null | sed "s|$PROJECT_DIR/||" > "$detail_dir/todo-fixme-hack.txt" || true

: > "$detail_dir/stale-active-plans.txt"
find "$PROJECT_DIR/docs/exec-plans/active" -type f -name '*.md' | LC_ALL=C sort |
while IFS= read -r plan; do
	if ! grep -q "^- $today:" "$plan"; then
		printf '%s\n' "${plan#"$PROJECT_DIR/"}" >> "$detail_dir/stale-active-plans.txt"
	fi
done

awk -F'|' '
	/^\| NC-/ {
		state = $3
		gsub(/^[[:space:]]+|[[:space:]]+$/, "", state)
		if (state == "차단" || state == "제안") {
			print $0
		}
	}
' "$PROJECT_DIR/docs/exec-plans/tech-debt-tracker.md" > "$detail_dir/debt-needing-decision.md"

find "$PROJECT_DIR/src" -type f -name '*.java' |
awk -F/ '{print $NF "|" $0}' | LC_ALL=C sort |
awk -F'|' '
	{
		count[$1]++
		paths[$1] = paths[$1] "\n  " $2
	}
	END {
		for (name in count) {
			if (count[name] > 1) {
				print name " (" count[name] ")" paths[name]
			}
		}
	}
' | LC_ALL=C sort > "$detail_dir/duplicate-java-filenames.txt"

: > "$detail_dir/orphan-document-candidates.txt"
find "$PROJECT_DIR/docs" -type f -name '*.md' | LC_ALL=C sort |
while IFS= read -r document; do
	name=$(basename "$document")
	references=$(grep -R -F -l "$name" "$PROJECT_DIR" \
		--include='*.md' --exclude-dir=build --exclude-dir=.gradle 2>/dev/null |
		grep -Fvx "$document" | wc -l | tr -d ' ')
	if [ "$references" -eq 0 ]; then
		printf '%s\n' "${document#"$PROJECT_DIR/"}" >> "$detail_dir/orphan-document-candidates.txt"
	fi
done

cut -d'|' -f3- "$PROJECT_DIR/harness/baselines/test-failure-fingerprints.txt" |
LC_ALL=C sort | uniq -d > "$detail_dir/repeated-failure-fingerprints.txt"

if "$HARNESS_COMMAND_DIR/generate-docs.sh" --check >/dev/null 2>&1; then
	generated_docs_status=PASS
else
	generated_docs_status=DRIFT
fi
if "$HARNESS_COMMAND_DIR/migrations.sh" >/dev/null 2>&1; then
	migration_status=PASS
else
	migration_status=DRIFT
fi

stale_plan_count=$(wc -l < "$detail_dir/stale-active-plans.txt" | tr -d ' ')
decision_debt_count=$(wc -l < "$detail_dir/debt-needing-decision.md" | tr -d ' ')
orphan_count=$(wc -l < "$detail_dir/orphan-document-candidates.txt" | tr -d ' ')
duplicate_name_count=$(grep -cE '^[^[:space:]].* \([0-9]+\)$' "$detail_dir/duplicate-java-filenames.txt" 2>/dev/null || true)
repeated_fingerprint_count=$(wc -l < "$detail_dir/repeated-failure-fingerprints.txt" | tr -d ' ')

{
	printf 'Nanacocoa 엔트로피 진단\n'
	printf '수집_UTC=%s\n\n' "$(utc_now)"
	printf '알려진_테스트_실패=%s\n' "$test_baseline_count"
	printf '아키텍처_예외=%s\n' "$architecture_baseline_count"
	printf '보안_운영_예외=%s\n' "$security_baseline_count"
	printf '활성_실행_계획=%s\n' "$active_plans"
	printf '오늘_진행_기록이_없는_활성_계획=%s\n' "$stale_plan_count"
	printf '추적_기술부채=%s\n' "$debt_items"
	printf '차단_또는_제안_부채_행=%s\n' "$decision_debt_count"
	printf 'TODO_FIXME_HACK_일치=%s\n' "$todo_count"
	printf '비활성_테스트_일치=%s\n' "$disabled_tests"
	printf '고립_문서_후보=%s\n' "$orphan_count"
	printf '중복_Java_파일명_그룹=%s\n' "$duplicate_name_count"
	printf '반복_실패_fingerprint=%s\n' "$repeated_fingerprint_count"
	printf '생성_문서_상태=%s\n' "$generated_docs_status"
	printf '마이그레이션_등록부_상태=%s\n\n' "$migration_status"
	printf '상세 위치\n'
	printf '%s\n' \
		'build/harness/entropy/details/todo-fixme-hack.txt' \
		'build/harness/entropy/details/stale-active-plans.txt' \
		'build/harness/entropy/details/debt-needing-decision.md' \
		'build/harness/entropy/details/orphan-document-candidates.txt' \
		'build/harness/entropy/details/duplicate-java-filenames.txt' \
		'build/harness/entropy/details/repeated-failure-fingerprints.txt'
	printf '\n해석\n'
	printf '%s\n' \
		'- 기준선 증가는 자동 수락이 아니라 리뷰가 필요합니다.' \
		'- 오늘 진행 기록이 없는 활성 계획에는 현재 증거나 명시적 종료가 필요합니다.' \
		'- TODO/FIXME/HACK 결과는 진단용이며 부채 생성 전에 검토합니다.' \
		'- 고립 문서와 중복 이름 결과는 자동 삭제 대상이 아니라 사람 리뷰 후보입니다.' \
		'- architecture, docs, migrations, security, test-baseline으로 규칙을 강제합니다.' \
		'- 이 명령은 저장소 파일을 삭제하거나 다시 쓰지 않습니다.'
} > "$report"

record_status entropy PASS "진단 보고서: build/harness/entropy/report.txt"
status_line PASS "entropy" "build/harness/entropy/report.txt"
