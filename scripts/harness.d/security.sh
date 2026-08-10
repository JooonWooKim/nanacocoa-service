#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

violations=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-security-violations.XXXXXX")
risks=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-security-risks.XXXXXX")
baseline="$PROJECT_DIR/harness/baselines/security-exceptions.txt"
cleanup() {
	rm -f "$violations" "$risks"
}
trap cleanup EXIT HUP INT TERM

scan_file() {
	file=$1
	case "$file" in
		*/scripts/harness.d/security.sh)
			return
			;;
	esac
	if grep -nE 'AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|live_sk_[A-Za-z0-9_-]+|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----' "$file" >/dev/null 2>&1; then
		printf '%s: 커밋된 자격 증명·개인 키로 의심되는 패턴\n' "${file#"$PROJECT_DIR/"}" >> "$violations"
	fi
}

for root in src scripts docs harness .github AGENTS.md ARCHITECTURE.md README.md \
	compose.yaml build.gradle settings.gradle Dockerfile .env.example; do
	path="$PROJECT_DIR/$root"
	if [ -f "$path" ]; then
		scan_file "$path"
	elif [ -d "$path" ]; then
		find "$path" -type f \( \
			-name '*.java' -o -name '*.gradle' -o -name '*.sh' -o \
			-name '*.md' -o -name '*.yml' -o -name '*.yaml' -o \
			-name '*.properties' -o -name '*.txt' -o -name '*.tsv' -o \
			-name '*.js' -o -name '*.html' -o -name '*.css' -o \
			-name '*.sql' -o -name '*.json' -o -name '*.xml' -o \
			-name '*.toml' -o -name '*.conf' \
		\) | sort |
		while IFS= read -r file; do
			scan_file "$file"
		done
	fi
done

if [ -f "$PROJECT_DIR/.env.example" ]; then
	awk -F= '
		/^(DB_PASSWORD|MYSQL_ROOT_PASSWORD|REDIS_PASSWORD|JWT_SECRET|AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY|AWS_SESSION_TOKEN|TOSS_PAYMENTS_SECRET_KEY)=/ {
			value = substr($0, index($0, "=") + 1)
			if (value != "" && value !~ /^\$\{/ && value !~ /^<.*>$/) {
				print NR ":" $1
			}
		}
	' "$PROJECT_DIR/.env.example" |
	while IFS= read -r finding; do
		printf '.env.example:%s에 placeholder가 아닌 비밀값이 있습니다\n' "$finding" >> "$violations"
	done
fi

find "$PROJECT_DIR/scripts" -type f \( -name '*.sh' -o -name 'harness' \) | sort |
while IFS= read -r file; do
	case "$file" in
		*/scripts/harness.d/security.sh)
			continue
			;;
	esac
	if grep -nE 'docker[[:space:]]+compose[[:space:]]+down.*(--volumes|-v)([[:space:]]|$)|docker[[:space:]]+volume[[:space:]]+(rm|prune)|rm[[:space:]]+-rf[[:space:]]+(/|~|\.|\.\.|\$HOME|\$\{HOME\})([[:space:]]|$)|git[[:space:]]+(reset[[:space:]]+--hard|clean[[:space:]]+-[^[:space:]]*f|checkout[[:space:]]+--)' "$file" >/dev/null 2>&1; then
		printf '%s: 자동화에 파괴적 명령 패턴이 있습니다\n' "${file#"$PROJECT_DIR/"}" >> "$violations"
	fi
done

if grep -R -E -n -i '(logger|log)\.(trace|debug|info|warn|error)\([^;]*(password|secret|token|paymentKey|idempotencyKey)' \
	"$PROJECT_DIR/src/main/java" >/dev/null 2>&1; then
	printf 'src/main/java: logger 문장에 민감한 자격 증명·결제 정보가 포함될 수 있습니다\n' >> "$violations"
fi

if grep -nE '^[[:space:]]*-[[:space:]]+\$\{[A-Za-z0-9_]*(PASSWORD|SECRET|TOKEN|KEY)' \
	"$PROJECT_DIR/compose.yaml" >/dev/null 2>&1; then
	printf 'compose.yaml: 비밀 변수가 container command 인자에 직접 보간됩니다\n' >> "$violations"
fi
if grep -Fq 'exec redis-server /tmp/redis.conf' "$PROJECT_DIR/compose.yaml" &&
	! grep -Eq '^[[:space:]]+user:[[:space:]]+redis[[:space:]]*$' "$PROJECT_DIR/compose.yaml"; then
	printf 'compose.yaml: Redis 설정 wrapper는 비root redis 사용자로 실행해야 합니다\n' >> "$violations"
fi

security_config="$PROJECT_DIR/src/main/java/com/nanacocoa/server/common/security/config/SecurityConfig.java"
auth_controller="$PROJECT_DIR/src/main/java/com/nanacocoa/server/member/controller/AuthController.java"
session_service="$PROJECT_DIR/src/main/java/com/nanacocoa/server/common/session/SessionLoginService.java"
application_config="$PROJECT_DIR/src/main/resources/application.yml"

if grep -qE '\.csrf\(.*disable\(\)' "$security_config" 2>/dev/null; then
	printf '%s\n' 'NC-SEC-001|src/main/java/com/nanacocoa/server/common/security/config/SecurityConfig.java|CSRF 보호가 비활성화되어 있음' >> "$risks"
fi
if grep -qE 'RequestMethod\.GET.*RequestMethod\.POST|RequestMethod\.POST.*RequestMethod\.GET' "$auth_controller" 2>/dev/null; then
	printf '%s\n' 'NC-SEC-002|src/main/java/com/nanacocoa/server/member/controller/AuthController.java|로그아웃이 상태 변경 요청으로 GET을 허용함' >> "$risks"
fi
if grep -q 'SPRING_SECURITY_CONTEXT' "$session_service" 2>/dev/null &&
	! grep -q 'changeSessionId' "$session_service" 2>/dev/null; then
	printf '%s\n' 'NC-SEC-003|src/main/java/com/nanacocoa/server/common/session/SessionLoginService.java|로그인 성공 시 명시적 세션 ID 회전이 없음' >> "$risks"
fi
if grep -qE 'baseline-on-migrate:[[:space:]]*true' "$application_config" 2>/dev/null; then
	printf '%s\n' 'NC-DATA-001|src/main/resources/application.yml|Flyway baseline-on-migrate가 활성화되어 있음' >> "$risks"
fi
if grep -q 'TOSS_PAYMENTS_BASE_URL:https://api.tosspayments.com' "$application_config" 2>/dev/null &&
	grep -q 'PAYMENT_RECONCILIATION_ENABLED:true' "$application_config" 2>/dev/null; then
	printf '%s\n' 'NC-OPS-001|src/main/resources/application.yml|실제 Toss URL과 reconciliation이 모두 기본값으로 활성화되어 있음' >> "$risks"
fi
if grep -q 'TOSS_PAYMENTS_BASE_URL:-https://api.tosspayments.com' "$PROJECT_DIR/compose.yaml" 2>/dev/null &&
	grep -q 'PAYMENT_RECONCILIATION_ENABLED:-true' "$PROJECT_DIR/compose.yaml" 2>/dev/null; then
	printf '%s\n' 'NC-OPS-002|compose.yaml|Compose 기본값이 실제 Toss URL과 활성화된 reconciliation을 결합함' >> "$risks"
fi
if grep -Fq '127.0.0.1:${MYSQL_PORT:-3306}:3306' "$PROJECT_DIR/compose.yaml" 2>/dev/null; then
	printf '%s\n' 'NC-OPS-003|compose.yaml|MySQL이 기본적으로 host loopback에 공개됨' >> "$risks"
fi
LC_ALL=C sort -o "$risks" "$risks"

if [ ! -f "$baseline" ]; then
	printf 'harness/baselines/security-exceptions.txt: 검토된 보안 위험 기준선이 없습니다\n' >> "$violations"
elif ! cmp -s "$baseline" "$risks"; then
	printf '보안 위험 기준선이 변경되었습니다. 새 항목과 해결된 항목을 모두 검토하세요.\n' >> "$violations"
	diff -u "$baseline" "$risks" >> "$violations" 2>&1 || true
fi

if [ -s "$violations" ]; then
	cat "$violations" >&2
	record_status security FAIL "비밀값 또는 파괴적 자동화로 의심되는 패턴"
	printf '지적을 제거하거나 비식별화한 뒤 ./scripts/harness security를 다시 실행하세요.\n' >&2
	exit 1
fi

risk_count=$(wc -l < "$baseline" | tr -d ' ')
if [ "$risk_count" -gt 0 ]; then
	status_line BASELINED "보안 위험" "검토된 위험 ${risk_count}개, 부채 추적기와 security-exceptions.txt 참고"
	record_status security BASELINED "저장소 검사 통과, 검토된 보안·운영 위험 ${risk_count}개 변경 없음"
else
	record_status security PASS "선택된 자격 증명·민감 로그·파괴 스크립트·기준선 위험이 없음"
fi
status_line PASS "security" "저장소 검사 통과, .env 내용은 읽지 않음"
