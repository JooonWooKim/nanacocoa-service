"""Rehearse a release upgrade and recovery from a pre-deploy synthetic backup."""

import argparse
from contextlib import ExitStack
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path
import signal
import sys
import tarfile
import tempfile
import uuid


spec = importlib.util.spec_from_file_location("release_rehearsal", Path(__file__).with_name("release-rehearsal.py"))
rehearsal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rehearsal)
RehearsalError = rehearsal.RehearsalError
SOURCE_DB = rehearsal.SOURCE_DB

# Stable business columns for the upgrade comparison. The restoration also compares complete rows.
TRANSACTION_COLUMNS = {
    "members": "id,email,name,password,created_at",
    "products": "id,name,price,stock_quantity,reserved_quantity",
    "orders": "id,order_id,member_id,total_amount,status,created_at,updated_at",
    "order_items": "id,order_id,product_id,unit_price,quantity,line_amount",
    "payments": "id,order_id,amount,status,approved_at,created_at,updated_at",
    "member_policy_consents": "id,member_id,document_code,document_version,agreed,content_hash,recorded_at",
}


class Parser(argparse.ArgumentParser):
    def error(self, _message):
        raise RehearsalError("INVALID_ARGUMENTS")


def verify_candidates(previous, candidate, env):
    if not rehearsal.SHA.fullmatch(previous) or not rehearsal.SHA.fullmatch(candidate):
        raise RehearsalError("EXPECTED_FULL_COMMIT_SHAS")
    if previous == candidate:
        raise RehearsalError("COMMITS_MUST_DIFFER")
    for commit in (previous, candidate):
        resolved = rehearsal.command(["git", "rev-parse", "--verify", f"{commit}^{{commit}}"],
                                     "COMMIT_UNAVAILABLE", env=env).decode().strip()
        if resolved != commit:
            raise RehearsalError("COMMIT_UNAVAILABLE")
    head = rehearsal.command(["git", "rev-parse", "--verify", "HEAD"], "HEAD_UNAVAILABLE", env=env)
    if head.decode().strip() != candidate:
        raise RehearsalError("CANDIDATE_NOT_HEAD")


def new_stack(snapshot, workspace, env, stacks, resources):
    workspace.mkdir()
    stack = rehearsal.Stack(snapshot, workspace, env)
    stacks.append(stack)
    resources.callback(stack.close)
    stack.prepare()
    if stack.docker != stacks[0].docker:
        raise RehearsalError("DOCKER_CONTEXT_CHANGED", "BLOCKED")
    stack.owned = True
    return stack


def build(stack, reason):
    stack.run("build", "app", reason=reason, timeout=600)


def application_ready(stack, reason):
    stack.wait(reason)
    port = stack.port()
    rehearsal.check_app(port)
    rehearsal.check_schema(stack)
    session = rehearsal.check_payment_guard(port)
    response, body, _ = rehearsal.http_json(
        port, "/api/orders/REHEARSAL-ORDER", reason, headers={"Cookie": session},
    )
    order = body.get("data")
    if (response != 200 or not isinstance(order, dict)
            or (order.get("orderId"), order.get("totalAmount"), order.get("status"), order.get("paymentResult"))
            != ("REHEARSAL-ORDER", 42000, "PAID", "PAID")):
        raise RehearsalError(reason)


def transaction_state(stack, reason):
    query = "".join(f"SELECT {columns} FROM {table} ORDER BY id;\n"
                    for table, columns in TRANSACTION_COLUMNS.items()).encode()
    rows = stack.mysql(SOURCE_DB, query, reason).splitlines()
    if len(rows) != len(TRANSACTION_COLUMNS):
        raise RehearsalError(reason)
    return rows


def import_backup(stack, dump, expected, reason):
    # The app first starts after import, so Flyway sees the previous release's existing schema.
    stack.run("up", "-d", "--no-build", "--wait", "--wait-timeout", "90", "mysql", reason=reason, timeout=110)
    count = stack.mysql(SOURCE_DB, b"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();",
                        reason)
    if count.strip() != b"0":
        raise RehearsalError("IMPORT_DATABASE_NOT_EMPTY")
    stack.mysql(SOURCE_DB, dump, reason)
    if rehearsal.fingerprint(stack, SOURCE_DB, reason) != expected:
        raise RehearsalError(reason)


def rehearse(previous_snapshot, candidate_snapshot, workspace, env, stacks, resources, completed):
    previous = new_stack(previous_snapshot, workspace / "previous", env, stacks, resources)
    build(previous, "PREVIOUS_BUILD_FAILED")
    previous.wait("PREVIOUS_START_FAILED")
    previous.mysql(SOURCE_DB, rehearsal.FIXTURE_SQL.encode(), "FIXTURE_LOAD_FAILED")
    application_ready(previous, "PREVIOUS_ORDER_READ_FAILED")
    full_rows = rehearsal.fingerprint(previous, SOURCE_DB, "BACKUP_SOURCE_READ_FAILED")
    transactions = transaction_state(previous, "BACKUP_SOURCE_READ_FAILED")
    completed.append("previous-application")

    dump = previous.mysql_tool("mysqldump", "--single-transaction", "--no-tablespaces", "--set-gtid-purged=OFF",
                               "--skip-comments", SOURCE_DB, reason="BACKUP_CREATE_FAILED")
    if not dump.strip():
        raise RehearsalError("BACKUP_EMPTY")
    completed.append("pre-deploy-backup")
    previous.run("stop", "app", reason="PREVIOUS_STOP_FAILED", timeout=45)

    candidate_failure = None
    try:
        candidate = new_stack(candidate_snapshot, workspace / "candidate", env, stacks, resources)
        build(candidate, "CANDIDATE_BUILD_FAILED")
        import_backup(candidate, dump, full_rows, "UPGRADE_BACKUP_IMPORT_FAILED")
        completed.append("candidate-baseline")
        try:
            application_ready(candidate, "CANDIDATE_START_OR_ORDER_READ_FAILED")
            if transaction_state(candidate, "UPGRADE_DATA_MISMATCH") != transactions:
                raise RehearsalError("UPGRADE_DATA_MISMATCH")
        finally:
            candidate.run("stop", "app", reason="CANDIDATE_STOP_FAILED", timeout=45)
        completed.append("candidate-upgrade")
    except RehearsalError as error:
        if str(error) == "INTERRUPTED" or error.status == "BLOCKED":
            raise
        candidate_failure = error

    restored = new_stack(previous_snapshot, workspace / "restored", env, stacks, resources)
    rehearsal.command([*restored.docker, "image", "tag", previous.image, restored.image],
                      "PREVIOUS_IMAGE_REUSE_FAILED", env=env)
    import_backup(restored, dump, full_rows, "ROLLBACK_RESTORE_FAILED")
    completed.append("rollback-restore")
    application_ready(restored, "ROLLBACK_START_OR_ORDER_READ_FAILED")
    if rehearsal.fingerprint(restored, SOURCE_DB, "ROLLBACK_DATA_MISMATCH") != full_rows:
        raise RehearsalError("ROLLBACK_DATA_MISMATCH")
    completed.append("rollback-application")
    if candidate_failure:
        raise candidate_failure


def run(previous, candidate, completed):
    env = {key: os.environ[key] for key in rehearsal.HOST_ENV if key in os.environ}
    verify_candidates(previous, candidate, env)
    completed.append("commits")
    with tempfile.TemporaryDirectory(prefix="nanacocoa-release-rollback-") as directory:
        workspace = Path(directory).resolve()
        previous_dir = workspace / "previous-source"
        candidate_dir = workspace / "candidate-source"
        for path in (previous_dir, candidate_dir):
            path.mkdir()
        previous_snapshot = rehearsal.export_candidate(previous, previous_dir, env)
        candidate_snapshot = rehearsal.export_candidate(candidate, candidate_dir, env)
        stacks = []
        try:
            with ExitStack() as resources:
                rehearse(previous_snapshot, candidate_snapshot, workspace, env, stacks, resources, completed)
        except RehearsalError as error:
            if str(error) != "CLEANUP_FAILED" and any(stack.owned for stack in stacks):
                completed.append("cleanup")
            raise
        else:
            completed.append("cleanup")


def main(args):
    completed = []
    result, reason = "PASS", "ROLLBACK_VERIFIED"
    try:
        parser = Parser(allow_abbrev=False)
        parser.add_argument("--previous", required=True)
        parser.add_argument("--candidate", required=True)
        options = parser.parse_args(args)
        run(options.previous, options.candidate, completed)
    except RehearsalError as error:
        result, reason = error.status, str(error)
    except (OSError, ValueError, KeyError, TypeError, AttributeError, tarfile.TarError):
        result, reason = "FAIL", "ROLLBACK_RUNTIME_INVALID"
    code = {"PASS": 0, "FAIL": 1, "BLOCKED": 2}[result]
    report = {"schemaVersion": 1, "kind": "rollback-rehearsal", "environment": "isolated",
              "result": result, "exitCode": code, "reason": reason, "completed": completed}
    if "commits" in completed:
        report.update({"previousCommit": options.previous, "candidateCommit": options.candidate,
                       "verifiedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
                       "evidenceId": "ev-" + uuid.uuid4().hex})
    print(json.dumps(report))
    return code


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, rehearsal.interruption)
    signal.signal(signal.SIGINT, rehearsal.interruption)
    sys.exit(main(sys.argv[1:]))
