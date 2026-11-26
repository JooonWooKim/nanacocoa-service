"""Run the MySQL quality gate and publish only allowlisted evidence."""

import json
import os
import re
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MYSQL_VERSION = "8.4.10"
READY_SECONDS = 90
LABEL = "com.nanacocoa.mysql-quality"
SUMMARY = ROOT / "build/harness/mysql-quality/summary.json"
SUITES = {
    "com.nanacocoa.server.migration.FlywayMigrationTest":
        ("SUBMISSION_MIGRATION_TEST_DB_URL", "submission_migration"),
    "com.nanacocoa.server.migration.FilePolicyConsentMigrationTest":
        ("POLICY_FILE_MIGRATION_DB_URL", "policy_migration"),
    "com.nanacocoa.server.order.inventory.InventoryIntegrationTest":
        ("INVENTORY_TEST_DB_URL", "inventory"),
    "com.nanacocoa.server.order.inventory.PaymentOperationsIntegrationTest":
        ("PAYMENT_OPERATIONS_TEST_DB_URL", "payment_operations"),
    "com.nanacocoa.server.order.inventory.ReturnRequestIntegrationTest":
        ("RETURNS_TEST_DB_URL", "returns"),
}
TEST_ID = re.compile(r"com\.nanacocoa\.server\.[A-Za-z0-9_.$]+#[A-Za-z0-9_$]+")
CONTAINER_ID = re.compile(r"[a-f0-9]{64}")


class GateError(Exception):
    def __init__(self, reason, status="FAIL"):
        super().__init__(reason)
        self.status = status


class Interrupted(Exception):
    pass


def interrupt(_signum, _frame):
    raise Interrupted()


def kill_group(process):
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait()


def command(args, *, timeout, env=None, capture=False):
    with subprocess.Popen(
        args, cwd=ROOT, env=env, stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
        stderr=subprocess.DEVNULL, stdin=subprocess.DEVNULL, text=True, start_new_session=True,
        errors="replace",
    ) as process:
        try:
            stdout, _ = process.communicate(timeout=timeout)
            return process.returncode, (stdout or "").strip()
        except subprocess.TimeoutExpired:
            kill_group(process)
            return 124, ""
        except BaseException:
            kill_group(process)
            raise


def docker(*args, timeout=10):
    return command(["docker", *args], timeout=timeout, capture=True)


def cleanup_container(container_id, run_label):
    # Also covers interruption after docker create succeeds but before its ID is read.
    if not container_id:
        code, container_id = docker("ps", "--all", "--quiet", "--no-trunc",
                                    "--filter", f"label={LABEL}={run_label}")
        if code or (container_id and not CONTAINER_ID.fullmatch(container_id)):
            return False
    if not container_id:
        return True
    if docker("rm", "--force", container_id)[0] == 0:
        return True
    # --rm can already have removed a container that exited during startup.
    code, remaining = docker("ps", "--all", "--quiet", "--no-trunc",
                             "--filter", f"label={LABEL}={run_label}")
    return code == 0 and not remaining


def wait_for_mysql(container_id, port):
    deadline = time.monotonic() + READY_SECONDS
    while time.monotonic() < deadline:
        code, state = docker("inspect", "--format", "{{.State.Running}}", container_id, timeout=2)
        if code or state != "true":
            raise GateError("MYSQL_STOPPED")
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=1) as connection:
                # Docker may open its forwarding port before MySQL sends its greeting.
                if not connection.recv(1):
                    time.sleep(1)
                    continue
        except OSError:
            time.sleep(1)
            continue
        code, version = docker("exec", container_id, "mysql", "--protocol=TCP", "-h127.0.0.1",
                               "-uroot", "--batch", "--skip-column-names", "-e", "SELECT VERSION()", timeout=2)
        if code == 0:
            if version != MYSQL_VERSION:
                raise GateError("MYSQL_VERSION_MISMATCH")
            return
        time.sleep(1)
    raise GateError("MYSQL_READY_TIMEOUT")


def gradle_environment(stage, port):
    # Use only the public config files and synthetic provider settings for this run.
    for source, dest in (("application.template.yml", "application.yml"),
                         ("application-test.template.yml", "application-test.yml")):
        shutil.copyfile(ROOT / "deploy/templates" / source, stage / dest)
    env = {key: value for key, value in os.environ.items()
           if key in {"PATH", "HOME", "JAVA_HOME", "GRADLE_USER_HOME", "TMPDIR", "LANG", "CI", "TERM"}
           or key.startswith("LC_")}
    env.update({
        "SPRING_CONFIG_LOCATION": stage.as_uri() + "/",
        "TOSS_PAYMENTS_ENABLED": "false",
        "PRODUCT_IMAGE_STORAGE_MODE": "mock",
        "MYSQL_QUALITY_XML_DIR": str(stage / "xml"),
    })
    for variable, database in SUITES.values():
        env[variable] = f"jdbc:mysql://127.0.0.1:{port}/{database}?sslMode=DISABLED&allowPublicKeyRetrieval=true"
    return env


def read_results(stage, report):
    env = {**os.environ, "JUNIT_RESULT_DIR": str(stage / "xml"), "JUNIT_OUTPUT_DIR": str(stage / "parsed")}
    if command([str(ROOT / "scripts/harness.d/junit-results.sh")], timeout=30, env=env)[0]:
        raise GateError("TEST_RESULTS_INVALID")
    baseline = (ROOT / "harness/baselines/test-inventory.txt").read_text()
    expected = {line for line in baseline.splitlines() if line.partition("#")[0] in SUITES}
    if not all(TEST_ID.fullmatch(entry) for entry in expected) or {item.partition("#")[0] for item in expected} != SUITES.keys():
        raise GateError("TEST_INVENTORY_INVALID")

    parsed = stage / "parsed"
    actual = set((parsed / "inventory.txt").read_text().splitlines())
    failed = set((parsed / "failures.txt").read_text().splitlines())
    skipped = set((parsed / "skipped.txt").read_text().splitlines())
    report.update(tests=len(actual), failures=len(failed), skipped=len(skipped),
                  unexpectedTests=len(actual - expected), missingTestIds=sorted(expected - actual),
                  failedTestIds=sorted(failed & expected), skippedTestIds=sorted(skipped & expected))
    if actual != expected:
        raise GateError("TEST_INVENTORY_MISMATCH")
    if failed or skipped:
        raise GateError("TEST_FAILURE_OR_SKIP")


def execute_gate(report):
    if not shutil.which("docker"):
        raise GateError("DOCKER_UNAVAILABLE", "BLOCKED")
    code, endpoint = docker("context", "inspect", "--format", "{{.Endpoints.docker.Host}}")
    configured_host = os.environ.get("DOCKER_HOST") or endpoint
    if code or not endpoint.startswith("unix://") or not configured_host.startswith("unix://"):
        raise GateError("DOCKER_LOCAL_REQUIRED", "BLOCKED")
    if docker("info", "--format", "{{.ServerVersion}}")[0]:
        raise GateError("DOCKER_UNAVAILABLE", "BLOCKED")
    run_label = uuid.uuid4().hex
    container_id = ""
    try:
        code, created = docker(
            "create", "--rm", "--name", f"nanacocoa-mysql-quality-{run_label}", "--label", f"{LABEL}={run_label}",
            "--publish", "127.0.0.1::3306", "--tmpfs", "/var/lib/mysql:rw,noexec,nosuid,size=1g",
            "--env", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", f"mysql:{MYSQL_VERSION}", timeout=180,
        )
        if code or not CONTAINER_ID.fullmatch(created):
            raise GateError("MYSQL_CREATE_FAILED")
        container_id = created
        if docker("start", container_id, timeout=30)[0]:
            raise GateError("MYSQL_START_FAILED")
        code, binding = docker("port", container_id, "3306/tcp")
        bound_port = re.fullmatch(r"127\.0\.0\.1:([1-9][0-9]{0,4})", binding)
        if code or not bound_port or int(bound_port[1]) > 65535:
            raise GateError("MYSQL_PORT_INVALID")
        port = int(bound_port[1])
        wait_for_mysql(container_id, port)
        sql = " ".join(f"CREATE DATABASE {database} CHARACTER SET utf8mb4;" for _, database in SUITES.values())
        if docker("exec", container_id, "mysql", "--protocol=TCP", "-h127.0.0.1", "-uroot", "-e", sql)[0]:
            raise GateError("MYSQL_SCHEMA_SETUP_FAILED")
        with tempfile.TemporaryDirectory(prefix="nanacocoa-mysql-test-") as folder:
            stage = Path(folder).resolve()
            env = gradle_environment(stage, port)
            gradle_code, _ = command([str(ROOT / "gradlew"), "--no-daemon", "mysqlQualityTest"], timeout=600, env=env)
            if gradle_code == 124:
                raise GateError("GRADLE_TIMEOUT")
            read_results(stage, report)
            if gradle_code:
                raise GateError("GRADLE_FAILED")
    finally:
        if not cleanup_container(container_id, run_label):
            raise GateError("MYSQL_CLEANUP_FAILED")


def main():
    for signum in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
        signal.signal(signum, interrupt)
    report = {"status": "PASS", "reason": "TESTS_PASSED"}
    SUMMARY.parent.mkdir(parents=True, exist_ok=True)
    SUMMARY.unlink(missing_ok=True)
    try:
        execute_gate(report)
    except GateError as error:
        report.update(status=error.status, reason=str(error))
    except Interrupted:
        report.update(status="FAIL", reason="INTERRUPTED")
    except OSError:
        report.update(status="FAIL", reason="LOCAL_IO_ERROR")
    with tempfile.NamedTemporaryFile(mode="w", dir=SUMMARY.parent, prefix=".summary-", delete=False) as out:
        json.dump(report, out)
        out.write("\n")
    os.replace(out.name, SUMMARY)
    print(f"[{report['status']}] mysql-quality · {report['reason']}")
    return {"PASS": 0, "FAIL": 1, "BLOCKED": 2}[report["status"]]


if __name__ == "__main__":
    raise SystemExit(main())
