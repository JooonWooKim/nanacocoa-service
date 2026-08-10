#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

violations=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-docs-violations.XXXXXX")
cleanup() {
	rm -f "$violations"
}
trap cleanup EXIT HUP INT TERM

required='
AGENTS.md
ARCHITECTURE.md
docs/README.md
docs/design-docs/index.md
docs/design-docs/core-beliefs.md
docs/exec-plans/README.md
docs/exec-plans/PLANS.md
docs/exec-plans/tech-debt-tracker.md
docs/product-specs/index.md
docs/product-specs/evaluation-catalog.md
docs/runbooks/local-development.md
docs/runbooks/testing.md
docs/runbooks/database-migrations.md
docs/runbooks/incident-debugging.md
docs/runbooks/ci.md
docs/runbooks/automation.md
docs/quality/QUALITY_SCORE.md
docs/quality/RELIABILITY.md
docs/quality/SECURITY.md
docs/quality/TESTING.md
docs/quality/OBSERVABILITY.md
docs/generated/repository-map.md
docs/generated/db-schema.md
'

printf '%s\n' "$required" |
while IFS= read -r relative; do
	[ -n "$relative" ] || continue
	file="$PROJECT_DIR/$relative"
	if [ ! -f "$file" ]; then
		printf '%s: 필수 지식 파일이 없습니다\n' "$relative" >> "$violations"
	elif [ ! -s "$file" ]; then
		printf '%s: 지식 파일이 비어 있습니다\n' "$relative" >> "$violations"
	fi
done

find "$PROJECT_DIR" -type f -name '*.md' \
	-not -path "$PROJECT_DIR/build/*" \
	-not -path "$PROJECT_DIR/.gradle/*" | LC_ALL=C sort |
while IFS= read -r file; do
	relative=${file#"$PROJECT_DIR/"}
	if grep -nE '\{\{[^}]+\}\}|<!-- *TODO|<!-- *TBD' "$file" >/dev/null 2>&1; then
		printf '%s: 해결되지 않은 placeholder 표시가 있습니다\n' "$relative" >> "$violations"
	fi

	grep -Eo '\]\([^)]*\)' "$file" 2>/dev/null |
	sed 's/^](//; s/)$//' |
	while IFS= read -r target; do
		target=$(printf '%s' "$target" | sed 's/^<//; s/>$//')
		case "$target" in
			''|\#*|http://*|https://*|mailto:*|app://*)
				continue
				;;
		esac
		target=${target%%#*}
		target=${target%%\?*}
		case "$target" in
			/*)
				resolved="$PROJECT_DIR$target"
				;;
			*)
				resolved="$(dirname "$file")/$target"
				;;
		esac
		if [ ! -e "$resolved" ]; then
			printf '%s: 링크 대상 `%s`이(가) 없습니다\n' "$relative" "$target" >> "$violations"
		fi
	done
done

find "$PROJECT_DIR/docs/exec-plans/active" -type f -name '*.md' | LC_ALL=C sort |
while IFS= read -r plan; do
	for heading in '## 목표' '## 하지 않는 일' '## 현재 증거' '## 결정' \
		'## 작업 분해' '## 진행 상황' '## 명령과 증거' \
		'## 위험과 롤백' '## 미해결 항목' '## 완료 조건'; do
		if ! grep -Fqx "$heading" "$plan"; then
			printf '%s: 활성 계획에 `%s`이(가) 없습니다\n' "${plan#"$PROJECT_DIR/"}" "$heading" >> "$violations"
		fi
	done
	if ! grep -q '^상태: 활성' "$plan"; then
		printf '%s: 활성 계획은 `상태: 활성`을 선언해야 합니다\n' "${plan#"$PROJECT_DIR/"}" >> "$violations"
	fi
	if ! grep -qE '^- \[[ x]\]' "$plan"; then
		printf '%s: 활성 계획에 실행 가능한 checklist가 필요합니다\n' "${plan#"$PROJECT_DIR/"}" >> "$violations"
	fi
	if ! grep -qE '^- 20[0-9]{2}-[0-9]{2}-[0-9]{2}:' "$plan"; then
		printf '%s: 활성 계획에 날짜가 있는 진행 증거가 필요합니다\n' "${plan#"$PROJECT_DIR/"}" >> "$violations"
	fi
	if ! grep -qE '^\| `[^`]+` \| (PASS|FAIL|BLOCKED|SKIPPED|BASELINED) \|' "$plan"; then
		printf '%s: 활성 계획에 분류된 명령·증거 행이 하나 이상 필요합니다\n' "${plan#"$PROJECT_DIR/"}" >> "$violations"
	fi
done

find "$PROJECT_DIR/docs/exec-plans/completed" -type f -name '*.md' 2>/dev/null | LC_ALL=C sort |
while IFS= read -r plan; do
	if ! grep -q '^상태: 완료' "$plan"; then
		printf '%s: 완료 계획은 `상태: 완료`를 선언해야 합니다\n' "${plan#"$PROJECT_DIR/"}" >> "$violations"
	fi
	for heading in '## 목표' '## 하지 않는 일' '## 현재 증거' '## 결정' \
		'## 작업 분해' '## 진행 상황' '## 명령과 증거' \
		'## 위험과 롤백' '## 미해결 항목' '## 완료 조건'; do
		if ! grep -Fqx "$heading" "$plan"; then
			printf '%s: 완료 계획에 `%s`이(가) 없습니다\n' "${plan#"$PROJECT_DIR/"}" "$heading" >> "$violations"
		fi
	done
	if grep -qE '^- \[ \]' "$plan"; then
		printf '%s: 완료 계획에 미완료 작업 항목이 남아 있습니다\n' "${plan#"$PROJECT_DIR/"}" >> "$violations"
	fi
	if ! grep -qE '^- \[x\]' "$plan"; then
		printf '%s: 완료 계획에 완료된 실행 checklist가 필요합니다\n' "${plan#"$PROJECT_DIR/"}" >> "$violations"
	fi
	if ! grep -qE '^- 20[0-9]{2}-[0-9]{2}-[0-9]{2}:' "$plan"; then
		printf '%s: 완료 계획에 날짜가 있는 진행 증거가 필요합니다\n' "${plan#"$PROJECT_DIR/"}" >> "$violations"
	fi
	if ! grep -qE '^\| `[^`]+` \| (PASS|FAIL|BLOCKED|SKIPPED|BASELINED) \|' "$plan"; then
		printf '%s: 완료 계획에 분류된 명령 증거가 필요합니다\n' "${plan#"$PROJECT_DIR/"}" >> "$violations"
	fi
done

for index_target in \
	'design-docs/index.md' \
	'product-specs/index.md' \
	'exec-plans/README.md' \
	'runbooks/local-development.md' \
	'runbooks/ci.md' \
	'quality/QUALITY_SCORE.md' \
	'quality/SECURITY.md' \
	'generated/repository-map.md' \
	'generated/db-schema.md'; do
	if ! grep -Fq "$index_target" "$PROJECT_DIR/docs/README.md"; then
		printf 'docs/README.md: 필수 인덱스 링크 `%s`이(가) 없습니다\n' "$index_target" >> "$violations"
	fi
done

for root_target in ARCHITECTURE.md docs/README.md docs/runbooks/testing.md docs/quality/QUALITY_SCORE.md; do
	if ! grep -Fq "$root_target" "$PROJECT_DIR/AGENTS.md"; then
		printf 'AGENTS.md: 필수 지도 링크 `%s`이(가) 없습니다\n' "$root_target" >> "$violations"
	fi
done

help_output=$("$PROJECT_DIR/scripts/harness" help)
find "$PROJECT_DIR" -type f -name '*.md' \
	-not -path "$PROJECT_DIR/build/*" \
	-not -path "$PROJECT_DIR/.gradle/*" | LC_ALL=C sort |
while IFS= read -r file; do
	grep -Eo '\./scripts/harness[[:space:]]+[a-z][a-z-]*' "$file" 2>/dev/null |
	awk '{print $2}' |
	while IFS= read -r command; do
		if ! printf '%s\n' "$help_output" | grep -qE "^  $command([[:space:]]|$)"; then
			printf '%s: 지원하지 않는 하네스 명령 `%s`을(를) 참조합니다\n' "${file#"$PROJECT_DIR/"}" "$command" >> "$violations"
		fi
	done
done

awk -F'|' '
	/^\| NC-/ {
		if (NF != 8) {
			print "docs/exec-plans/tech-debt-tracker.md:" NR ": 기술부채 행 형식이 잘못되었습니다"
			next
		}
		for (i = 2; i <= 7; i++) {
			value = $i
			gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
			if (value == "") {
				print "docs/exec-plans/tech-debt-tracker.md:" NR ": 필수 기술부채 필드가 비어 있습니다"
			}
		}
	}
' "$PROJECT_DIR/docs/exec-plans/tech-debt-tracker.md" >> "$violations"

if ! "$HARNESS_COMMAND_DIR/generate-docs.sh" --check; then
	printf 'docs/generated: 생성된 소스 참조 문서가 오래되었습니다\n' >> "$violations"
fi

if [ -s "$violations" ]; then
	cat "$violations" >&2
	record_status docs FAIL "지식 구조, 링크, 계획 또는 생성 문서 검사가 실패함"
	printf '경로·내용을 수정하거나 문서를 재생성한 뒤 ./scripts/harness docs를 다시 실행하세요.\n' >&2
	exit 1
fi

record_status docs PASS "필수 문서, 링크, 계획, 생성 참조 문서가 유효함"
status_line PASS "docs" "지식 graph와 생성 산출물이 내부적으로 일관됨"
