#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

failures=0
warnings=0

check_required() {
	label=$1
	command_name=$2
	if command -v "$command_name" >/dev/null 2>&1; then
		status_line PASS "$label" "$(command -v "$command_name")"
	else
		status_line FAIL "$label" "명령이 없음: $command_name"
		failures=$((failures + 1))
	fi
}

check_optional() {
	label=$1
	command_name=$2
	if command -v "$command_name" >/dev/null 2>&1; then
		status_line PASS "$label" "$(command -v "$command_name")"
	else
		status_line SKIPPED "$label" "선택 명령을 사용할 수 없음: $command_name"
		warnings=$((warnings + 1))
	fi
}

check_required "Java" java
check_required "Gradle wrapper shell" sh
for required_tool in awk basename cat cmp cp cut date diff dirname env find git grep head mkdir mktemp mv rm sed sort tail tr uniq wc; do
	check_required "POSIX 도구: $required_tool" "$required_tool"
done
if command -v shasum >/dev/null 2>&1 || command -v sha256sum >/dev/null 2>&1; then
	status_line PASS "SHA-256 도구" "마이그레이션 불변성 검사 사용 가능"
else
	status_line FAIL "SHA-256 도구" "shasum 또는 sha256sum을 설치하세요"
	failures=$((failures + 1))
fi
check_optional "Docker" docker
check_optional "curl" curl
check_optional "로컬 port 확인" nc

if command -v java >/dev/null 2>&1; then
	java_version=$(java -version 2>&1 | sed -n '1p')
	case "$java_version" in
		*\"17.*|*" 17."*)
			status_line PASS "Java toolchain" "$java_version"
			;;
		*)
			status_line FAIL "Java toolchain" "Java 17이 필요하며 현재 버전은 $java_version"
			failures=$((failures + 1))
			;;
	esac
fi

if [ -x "$PROJECT_DIR/gradlew" ]; then
	status_line PASS "Gradle wrapper" "./gradlew 실행 가능"
else
	status_line FAIL "Gradle wrapper" "chmod +x gradlew를 실행하세요"
	failures=$((failures + 1))
fi

if command -v docker >/dev/null 2>&1; then
	if docker compose version >/dev/null 2>&1; then
		status_line PASS "Docker Compose" "$(docker compose version 2>/dev/null)"
		if docker info >/dev/null 2>&1; then
			status_line PASS "Docker daemon" "읽기 전용 daemon 조회 성공"
		else
			status_line SKIPPED "Docker daemon" "daemon을 사용할 수 없어 Compose 설정만 가능하고 runtime smoke는 제한됨"
			warnings=$((warnings + 1))
		fi
	else
		status_line SKIPPED "Docker Compose" "plugin·daemon을 사용할 수 없어 smoke가 스택을 검사할 수 없음"
		warnings=$((warnings + 1))
	fi
fi

if command -v nc >/dev/null 2>&1; then
	for port in "${HARNESS_APP_PORT:-8080}" "${HARNESS_MYSQL_PORT:-3306}"; do
		if nc -z 127.0.0.1 "$port" >/dev/null 2>&1; then
			status_line SKIPPED "로컬 port $port" "loopback에서 사용 중이며 소유자·준비 상태를 추론하지 않음"
			warnings=$((warnings + 1))
		else
			status_line PASS "로컬 port $port" "연결을 받지 않아 충돌이 감지되지 않음"
		fi
	done
fi

if [ -f "$PROJECT_DIR/.env.example" ]; then
	status_line PASS "환경 템플릿" ".env.example이 있음"
else
	status_line FAIL "환경 템플릿" ".env.example이 필요함"
	failures=$((failures + 1))
fi

if [ -f "$PROJECT_DIR/.env" ]; then
	status_line SKIPPED "로컬 환경" ".env가 있지만 의도적으로 내용을 읽지 않아 시작 준비 상태는 미검증"
	warnings=$((warnings + 1))
else
	status_line BLOCKED "로컬 환경" ".env가 없으며 Docker 시작 전에 ./scripts/init-env.sh를 명시적으로 실행해야 함"
	warnings=$((warnings + 1))
fi

if [ "$failures" -gt 0 ]; then
	record_status doctor FAIL "필수 검사 ${failures}개 실패, 선택 검사 ${warnings}개 사용 불가"
	exit 1
fi

if [ "$warnings" -gt 0 ]; then
	record_status doctor SKIPPED "저장소 전용 도구 통과, Docker·runtime 검사 ${warnings}개 사용 불가"
	exit 2
fi

record_status doctor PASS "모든 필수·선택 검사 사용 가능"
status_line PASS "doctor" "저장소 검사와 선택적 로컬 runtime 확인 사용 가능"
