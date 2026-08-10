#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

status_line PASS "저장소 root" "$PROJECT_DIR"

if [ ! -f "$PROJECT_DIR/.env.example" ]; then
	record_status bootstrap FAIL ".env.example이 없음"
	fail_with_guidance "bootstrap" "환경 템플릿이 없습니다" ".env.example을 복구한 뒤 ./scripts/harness bootstrap을 다시 실행하세요."
fi

if [ -f "$PROJECT_DIR/.env" ]; then
	status_line PASS "로컬 비밀값" ".env가 이미 있으며 내용을 읽거나 변경하지 않음"
	record_status bootstrap PASS "로컬 환경 파일이 이미 있음"
	exit 0
fi

status_line BLOCKED "로컬 비밀값" ".env가 초기화되지 않음"
printf '%s\n' \
	"하네스는 비밀값을 자동 생성하거나 출력하지 않습니다." \
	"로컬 Docker 시작이 필요하면 .env.example을 검토하고 다음을 실행하세요:" \
	"  ./scripts/init-env.sh" \
	"그 후 다음을 다시 실행하세요:" \
	"  ./scripts/harness doctor"
record_status bootstrap BLOCKED "사용자가 scripts/init-env.sh로 .env를 명시적으로 초기화해야 함"
exit 2
