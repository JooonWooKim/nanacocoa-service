#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

ensure_result_dirs

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
	status_line SKIPPED "Compose 구성" "Docker Compose를 사용할 수 없음"
	record_status smoke SKIPPED "Docker Compose를 사용할 수 없어 stack을 시작하지 않음"
	exit 2
fi

compose_with_placeholders() {
	env \
		DB_USERNAME=harness_config_only \
		DB_PASSWORD=harness_config_only \
		MYSQL_ROOT_PASSWORD=harness_config_only \
		REDIS_PASSWORD=harness_config_only \
		JWT_SECRET=harness_config_only_32_bytes_minimum \
		docker compose --env-file /dev/null "$@"
}
status_format=$(printf '{{.Service}}\t{{.State}}\t{{.Health}}')

if ! (
	cd "$PROJECT_DIR" &&
	compose_with_placeholders config --quiet
); then
	record_status smoke FAIL "docker compose 구성이 유효하지 않음"
	fail_with_guidance "smoke" "Compose 보간·구성 검증 실패" "./scripts/harness bootstrap을 실행한 뒤 docker compose config를 실행하세요."
fi
status_line PASS "Compose 구성" "compose.yaml이 비프로덕션 placeholder로 정상 보간됨"

if (
	cd "$PROJECT_DIR" &&
	compose_with_placeholders ps --all --format "$status_format"
) > "$RESULT_ROOT/stack-status.tsv" 2> "$LOG_DIR/smoke-compose-ps.log"; then
	if [ -s "$RESULT_ROOT/stack-status.tsv" ]; then
		status_line PASS "Stack 상태" "비식별화한 app·MySQL·Redis 상태를 build/harness/stack-status.tsv에 저장함"
		stack_failures=0
		for service in app mysql redis; do
			if ! awk -F '\t' -v wanted="$service" '
				$1 == wanted {
					found = 1
					if ($2 == "running" && $3 == "healthy") {
						healthy = 1
					}
				}
				END { exit !(found && healthy) }
			' "$RESULT_ROOT/stack-status.tsv"; then
				status_line FAIL "Stack 서비스: $service" "실행 중인 프로젝트에 서비스가 없음" >&2
				stack_failures=$((stack_failures + 1))
			else
				status_line PASS "Stack 서비스: $service" "실행 중이며 정상"
			fi
		done
		if [ "$stack_failures" -gt 0 ]; then
			record_status smoke FAIL "Compose 서비스 ${stack_failures}개가 없거나 비정상"
			fail_with_guidance "Stack 상태" "실행 중인 Compose 프로젝트가 불완전하거나 비정상" "비식별화한 build/harness/stack-status.tsv와 범위가 제한된 Compose 로그를 확인하세요. 하네스는 stack을 변경하지 않았습니다."
		fi
	else
		status_line SKIPPED "Stack 상태" "실행 중인 Nanacocoa Compose 서비스가 없음"
	fi
else
	status_line SKIPPED "Stack 상태" "Docker daemon·프로젝트 상태를 확인할 수 없지만 Compose 구성은 통과"
fi

port=
port_source=
if published=$(cd "$PROJECT_DIR" && compose_with_placeholders port app 8080 2>/dev/null); then
	port=$(printf '%s\n' "$published" | sed -n '1s/.*://p')
	case "$port" in
		''|*[!0-9]*)
			port=
			;;
		*)
			port_source="실행 중인 Compose app"
			;;
	esac
fi

if [ -z "$port" ] && [ -n "${HARNESS_APP_PORT:-}" ]; then
	case "$HARNESS_APP_PORT" in
		*[!0-9]*|'')
			record_status smoke FAIL "HARNESS_APP_PORT는 숫자여야 함"
			fail_with_guidance "smoke" "HARNESS_APP_PORT가 유효하지 않음" "의도적으로 실행한 로컬 app의 port를 HARNESS_APP_PORT에 설정하세요."
			;;
		*)
			port=$HARNESS_APP_PORT
			port_source="명시적 HARNESS_APP_PORT"
			;;
	esac
fi

if [ -z "$port" ]; then
	status_line SKIPPED "Runtime 상태" "실행 중인 Compose app port가 없어 임의의 localhost 서비스에 요청하지 않음"
	record_status smoke SKIPPED "Compose 구성은 통과했으나 실행 중인 Compose app을 찾지 못함"
	exit 2
fi

health_url="http://127.0.0.1:$port/actuator/health"
if ! command -v curl >/dev/null 2>&1; then
	status_line SKIPPED "Runtime 상태" "curl을 사용할 수 없어 app에 요청하지 않음"
	record_status smoke SKIPPED "Compose 구성은 통과했으나 runtime 상태를 확인하지 않음"
	exit 2
fi

if curl --fail --silent --show-error --max-time 5 "$health_url" \
	> "$RESULT_ROOT/smoke-health.json" 2> "$LOG_DIR/smoke-curl.log"; then
	if grep -Eq '^[[:space:]]*\{[[:space:]]*"status"[[:space:]]*:[[:space:]]*"UP"([[:space:]]*,|[[:space:]]*\})' "$RESULT_ROOT/smoke-health.json"; then
		status_line PASS "Runtime 상태" "$health_url ($port_source)"
		record_status smoke PASS "Compose 구성과 실행 중인 app의 Actuator 상태가 UP"
		exit 0
	fi
	record_status smoke FAIL "Actuator가 응답했지만 UP 상태를 보고하지 않음"
	fail_with_guidance "Runtime 상태" "$health_url 응답 본문이 UP 상태가 아님" "build/harness/smoke-health.json과 범위가 제한된 app 로그를 확인하세요."
fi

record_status smoke FAIL "발견한 로컬 app이 ${health_url}의 Actuator 상태 요청에 응답하지 않음"
fail_with_guidance "Runtime 상태" "${health_url}에 연결할 수 없음($port_source)" "build/harness/logs/smoke-curl.log와 범위가 제한된 Compose app 로그를 확인하세요. 하네스는 stack을 변경하지 않았습니다."
