#!/bin/sh

set -u

HARNESS_COMMAND_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$HARNESS_COMMAND_DIR/../.." && pwd)
RESULT_ROOT=${HARNESS_RESULT_ROOT:-"$PROJECT_DIR/build/harness"}
STATUS_DIR="$RESULT_ROOT/status"
LOG_DIR="$RESULT_ROOT/logs"

ensure_result_dirs() {
	mkdir -p "$STATUS_DIR" "$LOG_DIR"
}

utc_now() {
	date -u '+%Y-%m-%dT%H:%M:%SZ'
}

one_line() {
	printf '%s' "$1" | tr '\n\r|' '   '
}

workspace_input_files() {
	find "$PROJECT_DIR" \
		-type d \( -name .git -o -name .gradle -o -name build \) -prune -o \
		-type f \( \
			-name '*.java' -o -name '*.gradle' -o -name '*.yml' -o \
			-name '*.yaml' -o -name '*.md' -o -name '*.sh' -o \
			-name '*.sql' -o -name '*.properties' -o -name '*.txt' -o \
			-name '*.tsv' -o -name '*.xml' -o -name '*.json' -o \
			-name '*.js' -o -name '*.html' -o -name '*.css' -o \
			-name '*.toml' -o -name '*.conf' -o -name '*.jar' -o \
			-name 'Dockerfile' -o -name 'gradlew' -o -name 'gradlew.bat' -o \
			-name 'harness' -o -name '.env.example' -o \
			-name '.gitignore' -o -name '.dockerignore' \
		\) -print | LC_ALL=C sort
}

checksum_stream() {
	if command -v shasum >/dev/null 2>&1; then
		shasum -a 256 | awk '{print $1}'
	elif command -v sha256sum >/dev/null 2>&1; then
		sha256sum | awk '{print $1}'
	else
		cksum | awk '{print $1 "-" $2}'
	fi
}

checksum_path() {
	path=$1
	if command -v shasum >/dev/null 2>&1; then
		shasum -a 256 "$path" | awk '{print $1}'
	elif command -v sha256sum >/dev/null 2>&1; then
		sha256sum "$path" | awk '{print $1}'
	else
		cksum "$path" | awk '{print $1 "-" $2}'
	fi
}

workspace_fingerprint() {
	fingerprint_files=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-workspace-files.XXXXXX")
	workspace_input_files > "$fingerprint_files"
	{
		sed "s|^$PROJECT_DIR/||" "$fingerprint_files"
		while IFS= read -r input_file; do
			if [ -x "$input_file" ]; then
				executable=x
			else
				executable=-
			fi
			printf '%s %s\n' "$executable" "${input_file#"$PROJECT_DIR/"}"
		done < "$fingerprint_files"
		if command -v git >/dev/null 2>&1; then
			git hash-object --stdin-paths < "$fingerprint_files"
		else
			while IFS= read -r input_file; do
				checksum_path "$input_file"
			done < "$fingerprint_files"
		fi
	} | checksum_stream
	rm -f "$fingerprint_files"
}

ordered_migration_files() {
	migration_dir=$1
	find "$migration_dir" -type f -name 'V*__*.sql' |
	awk '
		{
			path = $0
			name = $0
			sub(/^.*\//, "", name)
			version = name
			sub(/^V/, "", version)
			sub(/__.*/, "", version)
			if (version ~ /^[0-9]+$/) {
				printf "%020.0f|%s\n", version + 0, path
			} else {
				printf "x|%s\n", path
			}
		}
	' | LC_ALL=C sort -t '|' -k1,1 -k2,2 | cut -d '|' -f 2-
}

record_status() {
	name=$1
	status=$2
	detail=${3:-}
	run_id=${HARNESS_RUN_ID:-"manual-$(date -u '+%Y%m%dT%H%M%SZ')-$$"}
	ensure_result_dirs
	printf '%s|%s|%s|%s|%s\n' \
		"$status" "$(utc_now)" "$(workspace_fingerprint)" "$run_id" "$(one_line "$detail")" \
		> "$STATUS_DIR/$name.status"
}

status_line() {
	status=$1
	name=$2
	detail=${3:-}
	if [ -n "$detail" ]; then
		printf '[%s] %s - %s\n' "$status" "$name" "$detail"
	else
		printf '[%s] %s\n' "$status" "$name"
	fi
}

fail_with_guidance() {
	check=$1
	detail=$2
	next=$3
	status_line FAIL "$check" "$detail" >&2
	printf '수정 안내: %s\n' "$next" >&2
	exit 1
}
