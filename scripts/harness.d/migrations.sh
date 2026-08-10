#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

if [ "$#" -gt 1 ]; then
	record_status migrations FAIL "인자가 너무 많음"
	fail_with_guidance "migrations" "인자가 너무 많습니다" "migrations 또는 migrations --accept-new를 사용하세요."
fi

MIGRATION_DIR="$PROJECT_DIR/src/main/resources/db/migration"
BASELINE="$PROJECT_DIR/harness/baselines/migration-checksums.sha256"
mode=${1:-verify}
current=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-migrations-current.XXXXXX")
new_entries=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-migrations-new.XXXXXX")
violations=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-migrations-violations.XXXXXX")
cleanup() {
	rm -f "$current" "$new_entries" "$violations"
}
trap cleanup EXIT HUP INT TERM

checksum_file() {
	file=$1
	if command -v shasum >/dev/null 2>&1; then
		shasum -a 256 "$file" | awk '{print $1}'
	elif command -v sha256sum >/dev/null 2>&1; then
		sha256sum "$file" | awk '{print $1}'
	else
		return 1
	fi
}

if [ ! -d "$MIGRATION_DIR" ] || [ ! -f "$BASELINE" ]; then
	record_status migrations FAIL "마이그레이션 디렉터리 또는 checksum 기준선이 없음"
	fail_with_guidance "migrations" "필수 마이그레이션 입력이 없습니다" "마이그레이션과 harness/baselines/migration-checksums.sha256을 복구하세요."
fi

ordered_migration_files "$MIGRATION_DIR" |
while IFS= read -r file; do
	hash=$(checksum_file "$file") || {
		printf 'SHA-256 도구를 사용할 수 없습니다(shasum 또는 sha256sum 필요).\n' >> "$violations"
		continue
	}
	printf '%s  %s\n' "$hash" "${file#"$PROJECT_DIR/"}" >> "$current"
done

expected=1
ordered_migration_files "$MIGRATION_DIR" |
while IFS= read -r file; do
	name=$(basename "$file")
	version=${name%%__*}
	version=${version#V}
	case "$version" in
		*[!0-9]*|'')
			printf '%s: 마이그레이션 버전은 숫자여야 합니다\n' "${file#"$PROJECT_DIR/"}" >> "$violations"
			;;
		*)
			if [ "$version" -ne "$expected" ]; then
				printf '%s: 마이그레이션 V%s가 필요하지만 V%s를 발견했습니다\n' "${file#"$PROJECT_DIR/"}" "$expected" "$version" >> "$violations"
			fi
			expected=$((expected + 1))
			;;
	esac
done

case "$mode" in
	verify)
		if ! cmp -s "$BASELINE" "$current"; then
			printf '마이그레이션 checksum 등록부가 현재 파일과 다릅니다.\n' >> "$violations"
			diff -u "$BASELINE" "$current" >> "$violations" 2>&1 || true
		fi
		;;
	--accept-new)
		while read -r hash path; do
			[ -n "$path" ] || continue
			old_hash=$(awk -v wanted="$path" '$2 == wanted {print $1}' "$BASELINE")
			if [ -n "$old_hash" ] && [ "$old_hash" != "$hash" ]; then
				printf '%s: 기존 마이그레이션이 변경되었습니다. 복구하고 새 버전을 추가하세요\n' "$path" >> "$violations"
			elif [ -z "$old_hash" ]; then
				printf '%s  %s\n' "$hash" "$path" >> "$new_entries"
			fi
		done < "$current"

		while read -r hash path; do
			[ -n "$path" ] || continue
			if ! awk -v wanted="$path" '$2 == wanted {found=1} END {exit !found}' "$current"; then
				printf '%s: 등록된 마이그레이션이 삭제되었습니다\n' "$path" >> "$violations"
			fi
		done < "$BASELINE"

		if [ ! -s "$violations" ] && [ -s "$new_entries" ]; then
			cp "$current" "$BASELINE"
			status_line PASS "migrations" "새 마이그레이션을 등록했고 기존 checksum은 변경 없음"
		elif [ ! -s "$violations" ]; then
			status_line PASS "migrations" "등록할 새 마이그레이션이 없음"
		fi
		;;
	*)
		record_status migrations FAIL "지원하지 않는 option: $mode"
		fail_with_guidance "migrations" "지원하지 않는 option: $mode" "./scripts/harness migrations 또는 migrations --accept-new를 사용하세요."
		;;
esac

if [ -s "$violations" ]; then
	cat "$violations" >&2
	record_status migrations FAIL "마이그레이션 불변성·버전 검사 실패"
	printf '기존 마이그레이션을 복구하거나 다음 버전을 추가한 뒤 ./scripts/harness migrations를 다시 실행하세요.\n' >&2
	exit 1
fi

record_status migrations PASS "마이그레이션 버전과 등록된 checksum이 일치함"
status_line PASS "migrations" "Flyway 이력이 순차적이며 변경되지 않음"
