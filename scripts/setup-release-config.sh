#!/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"

config_files() {
    cat <<'MAPPING'
Dockerfile.template|Dockerfile
dockerignore|.dockerignore
compose.template.yml|compose.yaml
application.template.yml|src/main/resources/application.yml
application-test.template.yml|src/test/resources/application-test.yml
env.example|.env.example
MAPPING
}

safe_parent() {
    directory=$(dirname "$1")
    while [ "$directory" != . ]; do
        if [ -L "$directory" ] || { [ -e "$directory" ] && [ ! -d "$directory" ]; }; then
            return 1
        fi
        directory=$(dirname "$directory")
    done
}

template_dir=deploy/templates
config_files | (
    conflicts=0
    while IFS='|' read -r template target; do
        source="$template_dir/$template"
        if ! safe_parent "$source" || [ -L "$source" ] || [ ! -f "$source" ]; then
            printf '공용 템플릿을 사용할 수 없습니다. %s\n' "$source" >&2
            conflicts=1
        elif ! safe_parent "$target" || [ -L "$target" ] ||
            { [ -e "$target" ] && { [ ! -f "$target" ] || ! cmp -s "$source" "$target"; }; }; then
            printf '기존 설정을 보존했습니다. %s\n' "$target" >&2
            conflicts=1
        fi
    done
    exit "$conflicts"
)

config_files | while IFS='|' read -r template target; do
    source="$template_dir/$template"
    if [ ! -L "$target" ] && [ -f "$target" ] && cmp -s "$source" "$target"; then
        continue
    fi
    safe_parent "$target"
    mkdir -p "$(dirname "$target")"
    # Use noclobber so a concurrently created regular file is preserved.
    if [ -L "$target" ] || [ -e "$target" ] || ! (umask 022; set -C; cat "$source" > "$target"); then
        printf '설정 파일을 생성하지 못했습니다. %s\n' "$target" >&2
        exit 1
    fi
    printf '공용 설정 생성 %s\n' "$target"
done
