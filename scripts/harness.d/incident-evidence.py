"""Build read-only order/payment incident evidence from allowlisted events."""

import json
import os
import re
import stat
import subprocess
import sys
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
JAVA = "src/main/java/com/nanacocoa/server/"
TEST_JAVA = "src/test/java/com/nanacocoa/server/order/"
ORDER = "OrderServiceTest"
INVENTORY = "InventoryIntegrationTest"
PAYMENT = "PaymentOperationsIntegrationTest"
RETURNS = "ReturnRequestIntegrationTest"
TEST_PATHS = {
    ORDER: TEST_JAVA + "service/OrderServiceTest.java",
    INVENTORY: TEST_JAVA + "inventory/InventoryIntegrationTest.java",
    PAYMENT: TEST_JAVA + "inventory/PaymentOperationsIntegrationTest.java",
    RETURNS: TEST_JAVA + "inventory/ReturnRequestIntegrationTest.java",
}
ORDER_DOC = "docs/runbooks/inventory.md"
PAYMENT_DOC = "docs/runbooks/payment-environment.md"
HTTP_OPERATIONS = {"ORDER_CREATE", "PAYMENT_CONFIRM", "PAYMENT_CANCEL", "PAYMENT_RECONCILE"}
RECOVERY_OPERATIONS = {"RECOVERY_BATCH", "RECOVERY_APPROVAL", "RECOVERY_COMPENSATION", "RECOVERY_CANCELLATION"}
RESULTS = {"SUCCESS", "PENDING", "DEFERRED", "FAILED", "STOCK_CONFLICT", "UNAUTHENTICATED",
           "FORBIDDEN", "CSRF_REJECTED", "REQUEST_REJECTED", "INTERNAL_ERROR"}
RECOVERY_RESULTS = {"SUCCESS", "PENDING", "DEFERRED", "FAILED", "INTERNAL_ERROR"}
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
SHA = re.compile(r"[0-9a-f]{40}")
STAMP = re.compile(r"([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2})(?:\.([0-9]{1,9}))?Z")
REPO_PATH = re.compile(r"[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*")
MAX_BYTES = 256 * 1024


class InvalidInput(ValueError):
    """Fixed error codes without reflected input."""


def object_with_unique_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise InvalidInput("DUPLICATE_FIELD")
        result[key] = value
    return result


def read_input(path):
    fd = os.open(path, os.O_RDONLY | os.O_NONBLOCK | os.O_NOFOLLOW)
    with os.fdopen(fd, "rb") as source:
        if not stat.S_ISREG(os.fstat(source.fileno()).st_mode):
            raise InvalidInput("INVALID_INPUT_FILE")
        data = source.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise InvalidInput("INPUT_TOO_LARGE")
    return json.loads(data.decode("utf-8"), object_pairs_hook=object_with_unique_keys)


def matches(pattern, value):
    return isinstance(value, str) and pattern.fullmatch(value) is not None


def is_choice(value, choices):
    return isinstance(value, str) and value in choices


def timestamp_key(value):
    if not matches(STAMP, value):
        raise InvalidInput("INVALID_TIMESTAMP")
    match = STAMP.fullmatch(value)
    try:
        datetime.fromisoformat(match[1])
    except ValueError:
        raise InvalidInput("INVALID_TIMESTAMP") from None
    return match[1], (match[2] or "").ljust(9, "0")


def validate_event(event):
    if not isinstance(event, dict) or not is_choice(event.get("kind"), {"HTTP", "RECOVERY"}):
        raise InvalidInput("INVALID_EVENT")
    http = event["kind"] == "HTTP"
    expected = {"kind", "id", "occurredAt", "operation", "result", "durationMs"}
    if http:
        expected.add("httpStatus")
    if event.keys() != expected:
        raise InvalidInput("INVALID_EVENT_FIELDS")
    if (not matches(UUID, event["id"])
            or not is_choice(event["operation"], HTTP_OPERATIONS if http else RECOVERY_OPERATIONS)
            or not is_choice(event["result"], RESULTS if http else RECOVERY_RESULTS)
            or type(event["durationMs"]) is not int or not 0 <= event["durationMs"] < 2**63):
        raise InvalidInput("INVALID_EVENT_VALUE")
    if http and (type(event["httpStatus"]) is not int or not 100 <= event["httpStatus"] <= 599):
        raise InvalidInput("INVALID_HTTP_STATUS")
    timestamp_key(event["occurredAt"])


def parse_manifest(data):
    required = {"candidateCommit", "events"}
    if not isinstance(data, dict) or not required <= data.keys() or data.keys() - required - {"changedPaths"}:
        raise InvalidInput("INVALID_MANIFEST_FIELDS")
    if not matches(SHA, data["candidateCommit"]) or not isinstance(data["events"], list):
        raise InvalidInput("INVALID_MANIFEST_VALUE")
    paths = data.get("changedPaths", [])
    if not isinstance(paths, list) or any(
        not matches(REPO_PATH, path) or any(part in {".", "..", ".git"} for part in path.split("/"))
        for path in paths
    ):
        raise InvalidInput("INVALID_CHANGED_PATH")
    for event in data["events"]:
        validate_event(event)
    return data["candidateCommit"], data["events"], paths


def head_commit():
    result = subprocess.run(["git", "rev-parse", "--verify", "HEAD"], cwd=ROOT, capture_output=True,
                            text=True, timeout=5)
    return result.stdout.strip() if result.returncode == 0 else None


def candidate_has_references(candidate, paths):
    result = subprocess.run(["git", "ls-tree", "-r", "--name-only", candidate, "--", *paths],
                            cwd=ROOT, capture_output=True, text=True, timeout=5)
    return result.returncode == 0 and set(paths) <= set(result.stdout.splitlines())


def guidance(event):
    operation, result = event["operation"], event["result"]
    if result in {"UNAUTHENTICATED", "FORBIDDEN", "CSRF_REJECTED"}:
        tests = (INVENTORY,) if operation == "ORDER_CREATE" else (PAYMENT,)
        return ("ACCESS", "기존 로그인·관리자 권한·CSRF 상태를 확인합니다.",
                (JAVA + "common/security/config/SecurityConfig.java",), tests)
    if operation == "ORDER_CREATE":
        stage = "STOCK_RESERVATION" if result == "STOCK_CONFLICT" else "ORDER_SUBMISSION"
        return (stage, "주문 제출 결과를 조회하고 멱등성과 예약 재고, 롤백 여부를 대조합니다.",
                (JAVA + "order/service/OrderService.java", ORDER_DOC), (ORDER, INVENTORY))
    if operation == "PAYMENT_CONFIRM":
        stage = "APPROVAL_UNCERTAIN" if result in {"PENDING", "DEFERRED", "INTERNAL_ERROR"} else "APPROVAL"
        return (stage, "기존 거래 상태를 조회하고 미확정이면 운영 복구 절차에서 확인합니다.",
                (JAVA + "payment/facade/PaymentFacade.java", PAYMENT_DOC), (INVENTORY, PAYMENT))
    if operation == "PAYMENT_CANCEL":
        return ("CANCELLATION", "저장된 취소와 환불 상태를 조회하고 기존 운영 절차를 확인합니다.",
                (JAVA + "payment/facade/PaymentFacade.java", PAYMENT_DOC), (PAYMENT, RETURNS))
    return (operation, "해당 요청 또는 배치의 결과에서 기존 승인·취소·보상 복구 단계를 확인합니다.",
            (JAVA + "payment/service/PaymentRecoveryService.java", PAYMENT_DOC),
            (PAYMENT, RETURNS) if operation in {"RECOVERY_CANCELLATION", "PAYMENT_RECONCILE"} else (PAYMENT,))


def affected_tests(path):
    if path.startswith(JAVA + "order/"):
        return (ORDER, INVENTORY, RETURNS)
    if path.startswith(JAVA + "payment/"):
        return (INVENTORY, PAYMENT, RETURNS)
    return ()


def report_for(candidate, events, paths, kind, identifier):
    timeline = sorted((event for event in events if event["kind"] == kind and event["id"] == identifier),
                      key=lambda event: timestamp_key(event["occurredAt"]))
    findings = list(dict.fromkeys(guidance(event) for event in timeline if event["result"] != "SUCCESS"))
    report = {"candidateCommit": candidate, "kind": kind, "id": identifier, "timeline": timeline}
    if not findings:
        report.update(status="BLOCKED", reason="MISSING_FAILURE_EVIDENCE",
                      neededEvidence="같은 번호의 실패·보류 또는 후속 처리 이벤트")
        return report
    test_reasons = {}
    sources = {}
    procedures = []
    for stage, procedure, links, tests in findings:
        procedures.append({"stage": stage, "procedure": procedure})
        sources.update(dict.fromkeys(links))
        for test in tests:
            test_reasons.setdefault(test, []).append(stage)
    full_check = False
    for path in paths:
        tests = affected_tests(path)
        if not tests:
            full_check = True
        for test in tests:
            reasons = test_reasons.setdefault(test, [])
            if "CHANGED_DOMAIN" not in reasons:
                reasons.append("CHANGED_DOMAIN")
    commands = ["./scripts/harness test " + " ".join(f"--tests '*{test}'" for test in test_reasons)]
    if paths:
        commands.append("./scripts/harness check")
    if full_check or any(test in test_reasons for test in (INVENTORY, PAYMENT, RETURNS)):
        commands.append("./scripts/harness mysql-quality")
    report.update(status="PASS", reason="EVIDENCE_PREPARED", findings=procedures, sources=list(sources),
                  recommendedTests=[{"class": test, "reasons": reasons} for test, reasons in test_reasons.items()],
                  commands=commands, fullCheck=full_check)
    return report


def evaluate(args):
    if (len(args) != 4 or args[0] != "--input" or args[2] not in {"--request-id", "--run-id"}
            or not matches(UUID, args[3])):
        raise InvalidInput("INVALID_ARGUMENTS")
    candidate, events, paths = parse_manifest(read_input(args[1]))
    if candidate != head_commit():
        return {"status": "BLOCKED", "reason": "CANDIDATE_MISMATCH",
                "neededEvidence": "증거를 수집한 커밋의 로컬 체크아웃"}
    report = report_for(candidate, events, paths, "HTTP" if args[2] == "--request-id" else "RECOVERY", args[3])
    if report["status"] == "PASS":
        references = report["sources"] + [TEST_PATHS[row["class"]] for row in report["recommendedTests"]]
        if not candidate_has_references(candidate, references):
            return {"status": "BLOCKED", "reason": "CANDIDATE_REFERENCES_MISSING",
                    "neededEvidence": "해결 자료의 코드·운영 문서·검증 파일을 포함하는 후보 커밋"}
    return report


def main(args):
    try:
        report = evaluate(args)
    except InvalidInput as error:
        report = {"status": "FAIL", "reason": str(error)}
    except (OSError, subprocess.TimeoutExpired):
        report = {"status": "FAIL", "reason": "INPUT_UNAVAILABLE"}
    except (ValueError, RecursionError):
        report = {"status": "FAIL", "reason": "INVALID_JSON"}
    print(json.dumps(report, ensure_ascii=False))
    return {"PASS": 0, "FAIL": 1, "BLOCKED": 2}[report["status"]]


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
