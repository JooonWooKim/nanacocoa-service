"""Collect technical rows for the existing release-preflight manifest."""

import argparse
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile


spec = importlib.util.spec_from_file_location("release_preflight", Path(__file__).with_name("release-preflight.py"))
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)

REPO = "JooonWooKim/nanacocoa-service"
API = f"repos/{REPO}/actions"
SUMMARY = "quality-summary.json"
GATES = {
    "doctor", "architecture", "security", "migrations", "docs", "harness-self-check",
    "entropy", "smoke", "test-baseline", "eval", "git-diff-check", "workspace-stability",
}
OPTIONAL = {"doctor", "smoke"}
JOB_NAMES = {"Harness check", "MySQL 8.4 quality"}
FAILED = {"failure", "timed_out", "action_required", "startup_failure"}
REHEARSAL_STAGES = [
    "candidate-commit", "candidate-image", "stack-health", "flyway-jpa", "safe-defaults",
    "restart-data", "restore-data", "cleanup",
]


class CollectionError(Exception):
    def __init__(self, reason, exit_code=1):
        super().__init__(reason)
        self.exit_code = exit_code


class Parser(argparse.ArgumentParser):
    def error(self, _message):
        raise CollectionError("INVALID_ARGUMENTS")


def positive_id(value):
    if not isinstance(value, str) or not re.fullmatch(r"[1-9][0-9]{0,18}", value):
        raise CollectionError("INVALID_RUN_ID")
    return int(value)


def valid_candidate(candidate):
    if not preflight.matches(preflight.SHA, candidate):
        raise CollectionError("INVALID_CANDIDATE")


def load_json(data):
    return json.loads(data.decode("utf-8"), object_pairs_hook=preflight.unique_object,
                      parse_constant=preflight.reject_constant)


def github(endpoint):
    env = {**os.environ, "GH_PROMPT_DISABLED": "1", "GH_DEBUG": "", "GH_FORCE_TTY": ""}
    try:
        result = subprocess.run(
            ["gh", "api", "--hostname", "github.com", endpoint],
            env=env, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            timeout=35, check=True,
        )
    except (OSError, subprocess.SubprocessError):
        raise CollectionError("GITHUB_UNAVAILABLE", 2) from None
    return result.stdout


def github_json(endpoint):
    data = load_json(github(endpoint))
    if not isinstance(data, dict):
        raise CollectionError("GITHUB_RESPONSE_INVALID")
    return data


def parse_gate_summary(text):
    gates = {}
    for line in text.splitlines():
        fields = line.split("|", 2)
        if len(fields) != 3:
            raise CollectionError("HARNESS_SUMMARY_INVALID")
        name, status, _detail = fields
        if (name not in GATES or name in gates
                or status not in ({"PASS", "BASELINED", "SKIPPED"} if name in OPTIONAL else {"PASS", "BASELINED"})):
            raise CollectionError("HARNESS_SUMMARY_INVALID")
        gates[name] = status
    if gates.keys() != GATES:
        raise CollectionError("HARNESS_SUMMARY_INCOMPLETE")
    return "BASELINED" if "BASELINED" in gates.values() else "PASS"


def publish(path):
    candidate = os.environ.get("GITHUB_SHA")
    valid_candidate(candidate)
    identity = {
        "schemaVersion": 1, "candidateCommit": candidate,
        "runId": positive_id(os.environ.get("GITHUB_RUN_ID")),
        "runAttempt": positive_id(os.environ.get("GITHUB_RUN_ATTEMPT")),
    }
    with open(path, encoding="utf-8") as source:
        summary = source.read(preflight.MAX_INPUT_BYTES + 1)
    if len(summary) > preflight.MAX_INPUT_BYTES:
        raise CollectionError("HARNESS_SUMMARY_INVALID")
    return {**identity, "status": parse_gate_summary(summary)}


def read_rehearsal(path, candidate, target):
    try:
        report = preflight.read_manifest(path)
    except FileNotFoundError:
        raise CollectionError("REHEARSAL_NOT_FOUND", 2) from None
    fields = {"schemaVersion", "result", "exitCode", "reason", "completed", "check"}
    preflight.require_object(report, fields)
    if (report.keys() != fields or type(report["schemaVersion"]) is not int
            or report["schemaVersion"] != 1 or type(report["exitCode"]) is not int):
        raise CollectionError("REHEARSAL_REPORT_INVALID")
    status = {0: "PASS", 1: "FAIL", 2: "BLOCKED"}.get(report["exitCode"])
    row = report["check"]
    preflight.require_object(row, preflight.CHECK_FIELDS)
    if (not status or report["result"] != status or row.keys() != preflight.CHECK_FIELDS
            or row["id"] != "REHEARSAL" or row["status"] != status
            or row["role"] != "release-rehearsal"
            or not re.fullmatch(r"[A-Z][A-Z0-9_]{1,64}", report["reason"])):
        raise CollectionError("REHEARSAL_REPORT_INVALID")
    stages = report["completed"]
    if (not isinstance(stages, list) or any(stage not in REHEARSAL_STAGES for stage in stages)
            or len(set(stages)) != len(stages) or "candidate-commit" not in stages):
        raise CollectionError("REHEARSAL_REPORT_INVALID")
    if status == "PASS" and (stages != REHEARSAL_STAGES or report["reason"] != "REHEARSAL_VERIFIED"):
        raise CollectionError("REHEARSAL_REPORT_INVALID")
    result = preflight.validate_check(row, candidate, target)
    if result["reason"] != "EVIDENCE_" + status:
        raise CollectionError(result["reason"])
    return row


def quality_artifact(run_path, identity):
    artifacts = github_json(f"{run_path}/artifacts?name=release-quality-{identity['runAttempt']}")
    rows = artifacts["artifacts"]
    if artifacts["total_count"] == 0 and rows == []:
        return None, None
    if artifacts["total_count"] != 1 or len(rows) != 1:
        raise CollectionError("QUALITY_ARTIFACT_INVALID")
    row = rows[0]
    if row["name"] != f"release-quality-{identity['runAttempt']}":
        raise CollectionError("QUALITY_ARTIFACT_INVALID")
    if row["expired"] is True:
        return None, None
    artifact_id = positive_id(str(row["id"]))
    if (row["expired"] is not False or type(row["size_in_bytes"]) is not int
            or not 0 < row["size_in_bytes"] <= preflight.MAX_INPUT_BYTES):
        raise CollectionError("QUALITY_ARTIFACT_INVALID")
    archive = github(f"{API}/artifacts/{artifact_id}/zip")
    if len(archive) > preflight.MAX_INPUT_BYTES:
        raise CollectionError("QUALITY_ARTIFACT_INVALID")
    with zipfile.ZipFile(io.BytesIO(archive)) as bundle:
        entries = bundle.infolist()
        if (len(entries) != 1 or entries[0].filename != SUMMARY
                or entries[0].file_size > preflight.MAX_INPUT_BYTES):
            raise CollectionError("QUALITY_ARTIFACT_INVALID")
        summary = load_json(bundle.read(entries[0]))
    preflight.require_object(summary, identity.keys() | {"status"})
    if (any(type(summary.get(key)) is not type(value) or summary[key] != value for key, value in identity.items())
            or summary.get("status") not in ("PASS", "BASELINED")):
        raise CollectionError("QUALITY_ARTIFACT_MISMATCH")
    return summary["status"], artifact_id


def job_result(run_path, candidate, run_id, attempt):
    result = github_json(f"{run_path}/attempts/{attempt}/jobs?per_page=100")
    jobs = result["jobs"]
    if result["total_count"] != len(jobs):
        raise CollectionError("QUALITY_JOBS_INVALID")
    by_name = {}
    for job in jobs:
        name = job["name"]
        if name not in JOB_NAMES:
            continue
        if (name in by_name or job["head_sha"] != candidate or job["run_id"] != run_id
                or job["run_attempt"] != attempt):
            raise CollectionError("QUALITY_JOB_MISMATCH")
        by_name[name] = job
    if any(job["status"] == "completed" and job["conclusion"] in FAILED for job in by_name.values()):
        return "FAIL"
    if by_name.keys() == JOB_NAMES and all(job["status"] == "completed" and job["conclusion"] == "success"
                                          for job in by_name.values()):
        return "PASS"
    return "BLOCKED"


def read_quality(run_id, candidate):
    run_path = f"{API}/runs/{run_id}"
    run = github_json(run_path)
    if (run["id"] != run_id or run["head_sha"] != candidate or run["head_branch"] != "main"
            or run["event"] != "push" or run["name"] != "Quality"
            or run["path"] != ".github/workflows/quality.yml"
            or run["repository"]["full_name"].casefold() != REPO.casefold()
            or run["head_repository"]["full_name"].casefold() != REPO.casefold()):
        raise CollectionError("QUALITY_RUN_MISMATCH")
    attempt = positive_id(str(run["run_attempt"]))
    checked_at = run["updated_at"]
    if not preflight.is_timestamp(checked_at):
        raise CollectionError("QUALITY_TIMESTAMP_INVALID")
    artifact_id = None
    status = "BLOCKED"
    if run["status"] == "completed":
        if run["conclusion"] in FAILED:
            status = "FAIL"
        elif run["conclusion"] == "success":
            status = job_result(run_path, candidate, run_id, attempt)
            if status == "PASS":
                identity = {"schemaVersion": 1, "candidateCommit": candidate,
                            "runId": run_id, "runAttempt": attempt}
                published, artifact_id = quality_artifact(run_path, identity)
                status = published or "BLOCKED"
                latest = github_json(run_path)
                if any(latest.get(key) != run[key] for key in ("run_attempt", "status", "conclusion", "head_sha")):
                    status, artifact_id = "BLOCKED", None
    evidence_key = f"{REPO}:{candidate}:{run_id}:{attempt}:{artifact_id}:{status}"
    return {
        "id": "QUALITY", "status": status, "candidateCommit": candidate, "environment": "ci",
        "role": "github-quality", "verifiedAt": checked_at,
        "evidenceId": "ev-" + hashlib.sha256(evidence_key.encode()).hexdigest(),
    }


def main(args):
    try:
        if args[:1] == ["--publish-quality"]:
            if len(args) != 2:
                raise CollectionError("INVALID_ARGUMENTS")
            output = publish(args[1])
        else:
            parser = Parser(allow_abbrev=False)
            parser.add_argument("--candidate", required=True)
            parser.add_argument("--quality-run", required=True)
            parser.add_argument("--rehearsal", required=True)
            parser.add_argument("--target", required=True)
            options = parser.parse_args(args)
            valid_candidate(options.candidate)
            if not preflight.is_label(options.target) or options.target in preflight.TECHNICAL_ENVIRONMENTS.values():
                raise CollectionError("INVALID_TARGET")
            run_id = positive_id(options.quality_run)
            rehearsal = read_rehearsal(options.rehearsal, options.candidate, options.target)
            quality = read_quality(run_id, options.candidate)
            output = {"schemaVersion": 1, "candidateCommit": options.candidate,
                      "targetEnvironment": options.target, "checks": [quality, rehearsal]}
            print(f"증거 수집 · QUALITY {quality['status']} · REHEARSAL {rehearsal['status']}", file=sys.stderr)
        print(json.dumps(output))
        return 0
    except CollectionError as error:
        print(f"증거 수집 · {error}", file=sys.stderr)
        return error.exit_code
    except (ValueError, OSError, KeyError, TypeError, AttributeError, RuntimeError, OverflowError, zipfile.BadZipFile):
        print("증거 수집 · INVALID_EVIDENCE", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
