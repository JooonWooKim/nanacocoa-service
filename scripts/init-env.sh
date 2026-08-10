#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(dirname -- "$SCRIPT_DIR")
ENV_FILE="$PROJECT_DIR/.env"
EXAMPLE_FILE="$PROJECT_DIR/.env.example"
TEMP_FILE="$ENV_FILE.tmp.$$"

cleanup() {
    rm -f "$TEMP_FILE"
}

trap cleanup EXIT HUP INT TERM

if ! command -v openssl >/dev/null 2>&1; then
    echo "오류: 필수 비밀값 생성에 openssl이 필요합니다." >&2
    exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
    if [ ! -f "$EXAMPLE_FILE" ]; then
        echo "오류: $EXAMPLE_FILE 파일을 찾을 수 없습니다." >&2
        exit 1
    fi
    cp "$EXAMPLE_FILE" "$ENV_FILE"
fi

get_value() {
    key=$1
    sed -n "s/^${key}=//p" "$ENV_FILE" | tail -n 1
}

set_value() {
    key=$1
    value=$2

    awk -v key="$key" -v value="$value" '
        BEGIN { updated = 0 }
        index($0, key "=") == 1 {
            if (!updated) {
                print key "=" value
                updated = 1
            }
            next
        }
        { print }
        END {
            if (!updated) {
                print key "=" value
            }
        }
    ' "$ENV_FILE" > "$TEMP_FILE"

    chmod 600 "$TEMP_FILE"
    mv "$TEMP_FILE" "$ENV_FILE"
}

ensure_value() {
    key=$1
    value=$2

    if [ -z "$(get_value "$key")" ]; then
        set_value "$key" "$value"
    fi
}

ensure_value DB_USERNAME nanacocoa_app
ensure_value DB_PASSWORD "$(openssl rand -hex 32)"
ensure_value MYSQL_ROOT_PASSWORD "$(openssl rand -hex 32)"
ensure_value REDIS_PASSWORD "$(openssl rand -hex 32)"
ensure_value JWT_SECRET "$(openssl rand -hex 32)"

chmod 600 "$ENV_FILE"

echo ".env 초기화가 완료되었습니다. 기존 값은 변경하지 않았습니다."
