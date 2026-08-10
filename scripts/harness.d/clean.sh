#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

expected="$PROJECT_DIR/build/harness"
if [ "$RESULT_ROOT" != "$expected" ]; then
	record_status clean FAIL "기본값이 아닌 결과 root 거부: $RESULT_ROOT"
	fail_with_guidance "clean" "결과 root가 저장소 build/harness 디렉터리가 아닙니다" "HARNESS_RESULT_ROOT를 해제하고 다시 실행하세요."
fi

case "$RESULT_ROOT" in
	"$PROJECT_DIR"/build/harness)
		;;
	*)
		echo "FAIL: 안전하지 않은 clean 대상: $RESULT_ROOT" >&2
		exit 1
		;;
esac

if [ -d "$RESULT_ROOT" ]; then
	find "$RESULT_ROOT" -mindepth 1 -delete
fi
mkdir -p "$RESULT_ROOT"
record_status clean PASS "build/harness 임시 증거만 삭제함"
printf '[PASS] clean - build/harness 임시 증거만 삭제함\n'
