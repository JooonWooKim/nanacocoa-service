"""Check a supplied release evidence manifest without changing the target."""

import json
import os
import re
import stat
import sys
from datetime import datetime


CHECK_IDS = ("QUALITY", "REHEARSAL", "POLICY", "TARGET", "PAYMENT", "ROLLOUT")
TECHNICAL_ENVIRONMENTS = {"QUALITY": "ci", "REHEARSAL": "isolated"}
STATUSES = {"PASS", "FAIL", "BLOCKED", "SKIPPED", "BASELINED"}
MANIFEST_FIELDS = {"schemaVersion", "candidateCommit", "targetEnvironment", "checks"}
ATTESTATION_FIELDS = {"role", "verifiedAt", "evidenceId"}
CHECK_FIELDS = ATTESTATION_FIELDS | {"id", "status", "candidateCommit", "environment"}
SHA = re.compile(r"[0-9a-f]{40}")
LABEL = re.compile(r"[a-z][a-z0-9]*(?:-[a-z0-9]+)*")
EVIDENCE_ID = re.compile(r"ev-[0-9a-f]{16,64}")
TIMESTAMP = re.compile(
    r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
    r"(?:\.[0-9]+)?(?:Z|[+-][0-9]{2}:[0-9]{2})",
    re.IGNORECASE,
)
MAX_INPUT_BYTES = 64 * 1024


class InvalidEvidence(ValueError):
    """Carries a fixed reason code, never an input value."""


def require_object(value, fields):
    if not isinstance(value, dict) or value.keys() - fields:
        raise InvalidEvidence("INVALID_FIELDS")


def matches(pattern, value):
    return isinstance(value, str) and pattern.fullmatch(value) is not None


def is_label(value):
    return matches(LABEL, value) and len(value) <= 32


def is_timestamp(value):
    if not matches(TIMESTAMP, value):
        return False
    # Reject offsets that datetime would normalize instead of rejecting.
    if value[-1].upper() == "Z":
        value = value[:-1] + "+00:00"
    if int(value[-5:-3]) > 23 or int(value[-2:]) > 59:
        return False
    try:
        datetime.fromisoformat(value)
    except ValueError:
        return False
    return True


def validate_present(row, field, predicate):
    if field in row and not predicate(row[field]):
        raise InvalidEvidence("INVALID_FIELD_VALUE")


def validate_attestation(row):
    validate_present(row, "role", is_label)
    validate_present(row, "verifiedAt", is_timestamp)
    validate_present(row, "evidenceId", lambda value: matches(EVIDENCE_ID, value))
    return ATTESTATION_FIELDS <= row.keys()


def unique_object(pairs):
    obj = {}
    for key, value in pairs:
        if key in obj:
            raise InvalidEvidence("DUPLICATE_JSON_KEY")
        obj[key] = value
    return obj


def reject_constant(_value):
    raise InvalidEvidence("INVALID_JSON")


def read_manifest(path):
    fd = os.open(path, os.O_RDONLY | os.O_NONBLOCK | os.O_NOFOLLOW)
    with os.fdopen(fd, "rb") as stream:
        if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
            raise InvalidEvidence("NOT_REGULAR_FILE")
        data = stream.read(MAX_INPUT_BYTES + 1)
    if len(data) > MAX_INPUT_BYTES:
        raise InvalidEvidence("INPUT_TOO_LARGE")
    return json.loads(data.decode("utf-8"), object_pairs_hook=unique_object, parse_constant=reject_constant)


def check_result(check_id, status, reason):
    return {"id": check_id, "status": status, "reason": reason}


def validate_check(row, candidate, target):
    require_object(row, CHECK_FIELDS | {"baselineApproval"})
    validate_present(row, "id", lambda value: value in CHECK_IDS)
    validate_present(row, "status", lambda value: isinstance(value, str) and value in STATUSES)
    validate_present(row, "candidateCommit", lambda value: matches(SHA, value))
    validate_present(row, "environment", is_label)
    complete_attestation = validate_attestation(row)
    approval = row.get("baselineApproval")
    approval_complete = False
    status = row.get("status")
    check_id = row.get("id")
    if "baselineApproval" in row:
        if status is not None and status != "BASELINED":
            raise InvalidEvidence("UNEXPECTED_BASELINE_APPROVAL")
        require_object(approval, ATTESTATION_FIELDS)
        approval_complete = validate_attestation(approval)
    if candidate is not None and row.get("candidateCommit", candidate) != candidate:
        return check_result(check_id, "FAIL", "CANDIDATE_MISMATCH")
    expected_environment = TECHNICAL_ENVIRONMENTS.get(check_id, target) if check_id is not None else None
    if expected_environment is not None and row.get("environment", expected_environment) != expected_environment:
        return check_result(check_id, "FAIL", "ENVIRONMENT_MISMATCH")
    if approval_complete and approval["evidenceId"] == row.get("evidenceId"):
        return check_result(check_id, "FAIL", "BASELINE_APPROVAL_REUSES_EVIDENCE")
    if status in {"FAIL", "BLOCKED", "SKIPPED"}:
        return check_result(check_id, status, "EVIDENCE_" + status)
    if not complete_attestation or not CHECK_FIELDS <= row.keys():
        return check_result(check_id, "BLOCKED", "MISSING_CHECK_FIELDS")
    if status == "BASELINED" and not approval_complete:
        return check_result(check_id, "BLOCKED", "MISSING_BASELINE_APPROVAL")
    return check_result(check_id, status, "BASELINE_APPROVED" if status == "BASELINED" else "EVIDENCE_PASS")


def validate_manifest(manifest):
    require_object(manifest, MANIFEST_FIELDS)
    validate_present(manifest, "schemaVersion", lambda value: type(value) is int and value == 1)
    validate_present(manifest, "candidateCommit", lambda value: matches(SHA, value))
    validate_present(manifest, "targetEnvironment",
                     lambda value: is_label(value) and value not in TECHNICAL_ENVIRONMENTS.values())
    validate_present(manifest, "checks", lambda value: isinstance(value, list))
    candidate = manifest.get("candidateCommit")
    target = manifest.get("targetEnvironment")
    checked = {}
    missing_identity = False
    for row in manifest.get("checks", []):
        result = validate_check(row, candidate, target)
        check_id = result["id"]
        if check_id is None:
            if result["status"] == "FAIL":
                raise InvalidEvidence(result["reason"])
            missing_identity = True
            continue
        if check_id in checked:
            raise InvalidEvidence("DUPLICATE_CHECK_ID")
        checked[check_id] = result
    checks = [checked.get(check_id) or check_result(check_id, "BLOCKED", "MISSING_CHECK") for check_id in CHECK_IDS]
    if any(check["status"] == "FAIL" for check in checks):
        return emit(1, "CHECK_FAILED", checks, candidate)
    if not MANIFEST_FIELDS <= manifest.keys() or missing_identity:
        return emit(2, "MISSING_MANIFEST_FIELDS", checks, candidate)
    if any(check["status"] in {"BLOCKED", "SKIPPED"} for check in checks):
        return emit(2, "CHECK_INCOMPLETE", checks, candidate)
    return emit(0, "CHECKS_SATISFIED", checks, candidate)


def emit(exit_code, reason, checks=None, candidate=None):
    result = ("REVIEWABLE", "FAIL", "BLOCKED")[exit_code]
    report = {"schemaVersion": 1, "result": result, "exitCode": exit_code,
              "reason": reason, "checks": checks or []}
    if candidate is not None:
        report["candidateCommit"] = candidate
    print(json.dumps(report, ensure_ascii=True))
    print(f"배포 사전점검 · {'검토 가능' if exit_code == 0 else result} · {reason}", file=sys.stderr)
    for check in report["checks"]:
        print(f"{check['id']} · {check['status']} · {check['reason']}", file=sys.stderr)
    return exit_code


def main(args):
    if not args or (args == ["--input"] or args == ["--input", ""]):
        return emit(2, "MISSING_INPUT")
    if len(args) != 2 or args[0] != "--input":
        return emit(1, "INVALID_ARGUMENTS")
    try:
        return validate_manifest(read_manifest(args[1]))
    except InvalidEvidence as error:
        return emit(1, str(error))
    except FileNotFoundError:
        return emit(2, "INPUT_NOT_FOUND")
    except OSError:
        return emit(1, "INPUT_UNREADABLE")
    except (ValueError, RecursionError):
        return emit(1, "INVALID_JSON")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
