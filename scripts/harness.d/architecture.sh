#!/bin/sh

set -u
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/lib.sh"

SOURCE_ROOT="$PROJECT_DIR/src/main/java"
BASELINE="$PROJECT_DIR/harness/baselines/architecture-exceptions.txt"
violations=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-architecture-violations.XXXXXX")
common_actual=$(mktemp "${TMPDIR:-/tmp}/nanacocoa-common-imports.XXXXXX")
cleanup() {
	rm -f "$violations" "$common_actual"
}
trap cleanup EXIT HUP INT TERM

if [ ! -d "$SOURCE_ROOT" ]; then
	record_status architecture FAIL "소스 root가 없음"
	fail_with_guidance "architecture" "$SOURCE_ROOT 경로가 없습니다" "Java 소스 트리를 복구하세요."
fi

find "$SOURCE_ROOT" -path '*/repository/*.java' -type f | sort |
while IFS= read -r file; do
	relative=${file#"$PROJECT_DIR/"}
	if ! grep -qE 'extends[[:space:]]+JpaRepository<[^,>]+,[[:space:]]*Long>' "$file"; then
		printf '%s: repository 패키지 타입은 JpaRepository<Entity, Long>을 확장해야 합니다\n' "$relative" >> "$violations"
	fi
done

find "$SOURCE_ROOT" -name '*.java' -type f | sort |
while IFS= read -r file; do
	relative=${file#"$PROJECT_DIR/"}
	if ! grep -qE '^[[:space:]]*@RestController([[:space:](]|$)' "$file"; then
		continue
	fi
	case "$file" in
		*/controller/*.java)
			;;
		*)
			printf '%s: @RestController는 controller 패키지 아래에 있어야 합니다\n' "$relative" >> "$violations"
			;;
	esac
	if grep -nE 'com\.nanacocoa\.server\..*\.repository\.' "$file" >/dev/null 2>&1; then
		printf '%s: Controller가 Repository를 import합니다. Service·Facade를 거쳐야 합니다\n' "$relative" >> "$violations"
	fi
	if grep -nE 'com\.nanacocoa\.server\..*\.entity\.' "$file" >/dev/null 2>&1; then
		printf '%s: Controller가 Entity를 import합니다. 응답 DTO를 사용하세요\n' "$relative" >> "$violations"
	fi
	if grep -n '@Transactional' "$file" >/dev/null 2>&1; then
		printf '%s: Controller가 트랜잭션을 소유합니다. 조정을 Service·Facade로 이동하세요\n' "$relative" >> "$violations"
	fi
	boundary_count=$(grep -Ec '^import com\.nanacocoa\.server\..*\.(service|facade)\.' "$file" 2>/dev/null || true)
	if [ "$boundary_count" -ne 1 ]; then
		printf '%s: Controller는 정확히 하나의 Service·Facade 경계를 import해야 합니다. 발견: %s\n' "$relative" "$boundary_count" >> "$violations"
	fi
done

find "$SOURCE_ROOT" -name '*.java' -type f | sort |
while IFS= read -r file; do
	relative=${file#"$PROJECT_DIR/"}
	if ! grep -q 'extends JpaRepository<' "$file"; then
		continue
	fi
	case "$file" in
		*/repository/*.java)
			;;
		*)
			printf '%s: JpaRepository 선언은 repository 패키지 아래에 있어야 합니다\n' "$relative" >> "$violations"
			;;
	esac
	if grep -nE '^import com\.nanacocoa\.server\..*\.(controller|service|facade)\.' "$file" >/dev/null 2>&1; then
		printf '%s: Repository가 상위 계층을 import합니다\n' "$relative" >> "$violations"
	fi
	if ! grep -qE '(^|[[:space:]])interface[[:space:]]+[A-Za-z0-9_]+' "$file"; then
		printf '%s: Spring Data Repository는 interface를 유지해야 합니다\n' "$relative" >> "$violations"
	fi
	if grep -nE '(^|[[:space:]])default[[:space:]].*\(' "$file" >/dev/null 2>&1; then
		printf '%s: Repository default method에 비즈니스 로직이 있을 수 있습니다. Service로 이동하세요\n' "$relative" >> "$violations"
	fi
done

find "$SOURCE_ROOT" -name '*.java' -type f | sort |
while IFS= read -r file; do
	case "$file" in
		*/controller/*.java)
			continue
			;;
	esac
	if grep -nE '^import com\.nanacocoa\.server\..*\.controller\.' "$file" >/dev/null 2>&1; then
		printf '%s: 하위·비웹 계층이 Controller를 import합니다\n' "${file#"$PROJECT_DIR/"}" >> "$violations"
	fi
done

DOMAIN_ROOT="$SOURCE_ROOT/com/nanacocoa/server"
if [ -d "$DOMAIN_ROOT/common" ]; then
	find "$DOMAIN_ROOT/common" -name '*.java' -type f | sort |
	while IFS= read -r file; do
		relative=${file#"$PROJECT_DIR/"}
		grep -E '^import com\.nanacocoa\.server\.[A-Za-z0-9_]+\.' "$file" 2>/dev/null |
		while IFS= read -r import_line; do
			imported=${import_line#import com.nanacocoa.server.}
			top_package=${imported%%.*}
			if [ "$top_package" != "common" ] && [ -d "$DOMAIN_ROOT/$top_package" ]; then
				printf '%s|%s\n' "$relative" "$import_line"
			fi
		done
	done | LC_ALL=C sort > "$common_actual"
fi

if [ ! -f "$BASELINE" ]; then
	printf '아키텍처 기준선이 없습니다: %s\n' "$BASELINE" >> "$violations"
elif ! cmp -s "$BASELINE" "$common_actual"; then
	printf 'common -> domain 의존성 기준선이 변경되었습니다.\n' >> "$violations"
	printf '아래 diff를 검토하세요. 해결된 항목을 제거하거나 새 의존성을 명시적으로 계획하세요.\n' >> "$violations"
	diff -u "$BASELINE" "$common_actual" >> "$violations" 2>&1 || true
fi

if find "$SOURCE_ROOT" -type d -path '*/dto/request' | grep -q .; then
	printf '기존 dto/reqeust 계약 옆에 dto/request 디렉터리가 추가되었습니다. 철자를 섞지 말고 하나의 원자적 마이그레이션을 계획하세요.\n' >> "$violations"
fi

if [ -s "$violations" ]; then
	cat "$violations" >&2
	record_status architecture FAIL "계층·의존성 불변식 위반, 명령 출력 확인 필요"
	printf '재실행: ./scripts/harness architecture\n참고: ARCHITECTURE.md\n' >&2
	exit 1
fi

baseline_count=$(wc -l < "$BASELINE" | tr -d ' ')
if [ "$baseline_count" -gt 0 ]; then
	status_line BASELINED "common 의존 방향" "검토된 예외 ${baseline_count}개, NC-ARCH-001"
	record_status architecture BASELINED "강제 불변식 통과, common->domain 예외 ${baseline_count}개 변경 없음"
else
	record_status architecture PASS "강제되는 모든 계층 불변식 통과"
fi
status_line PASS "architecture" "Controller 발견·경계, Repository 형태, 하위 계층 방향, DTO, 트랜잭션, 의존성 래칫 유지"
