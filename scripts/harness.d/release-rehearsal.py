"""Build and rehearse a committed candidate on a disposable local Compose stack."""

import http.client
import json
import os
import re
import secrets
import signal
import subprocess
import sys
import tarfile
import tempfile
import uuid
from datetime import datetime, timezone
from http.cookies import CookieError, SimpleCookie
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SHA = re.compile(r"[0-9a-f]{40}")
SOURCE_DB = "rehearsal_source"
RESTORE_DB = "rehearsal_restore"
MYSQL_VERSION = "8.4.10"
SAFE_APP_ENV = {
    "SPRING_PROFILES_ACTIVE": "mysql",
    "SPRING_JPA_HIBERNATE_DDL_AUTO": "validate",
    "SPRING_FLYWAY_ENABLED": "true",
    "SPRING_FLYWAY_CLEAN_DISABLED": "true",
    "POLICY_SIGNUP_ENABLED": "false",
    "TOSS_PAYMENTS_ENABLED": "false",
    "PAYMENT_RECONCILIATION_ENABLED": "false",
    "PRODUCT_IMAGE_STORAGE_MODE": "mock",
}
HOST_ENV = ("PATH", "HOME", "DOCKER_CONFIG", "TMPDIR", "SYSTEMROOT")
NO_MASQUERADE = "com.docker.network.bridge.enable_ip_masquerade"
FIXTURE_EMAIL = "rehearsal@example.invalid"
FIXTURE_PASSWORD = "fixture-rehearsal-only"
# BCrypt hash of the public fixture password above. Used only inside the temporary database.
FIXTURE_HASH = "$2y$10$Fzw6OdNvWZOfq3BfoJ/yBOSyh6GVEshuOn7JoWzcF0pAgHPMY35Vy"
FIXTURE_SQL = f"""
START TRANSACTION;
SET @recorded = '2026-01-01 00:00:00.123456';
INSERT INTO members (id,email,name,password,created_at)
VALUES (1,'{FIXTURE_EMAIL}','Rehearsal Member','{FIXTURE_HASH}',@recorded);
INSERT INTO products (id,name,price,summary,detail_title,description,created_at)
VALUES (1,'Rehearsal Product',42000,'Fixture','Fixture','Fixture',@recorded);
INSERT INTO orders (id,order_id,member_id,total_amount,status,created_at,updated_at)
VALUES (1,'REHEARSAL-ORDER',1,42000,'PAID',@recorded,@recorded);
INSERT INTO order_items (id,order_id,product_id,product_name,unit_price,quantity,line_amount)
VALUES (1,1,1,'Rehearsal Product',42000,1,42000);
INSERT INTO payments (id,order_id,payment_key,approval_idempotency_key,request_fingerprint,
                      compensation_idempotency_key,amount,status,recovery_action,approved_at,created_at,updated_at)
VALUES (1,1,'rehearsal-payment','rehearsal-approval',REPEAT('a',64),'rehearsal-compensation',
        42000,'SUCCESS','NONE',@recorded,@recorded,@recorded);
INSERT INTO member_policy_consents (id,member_id,agreed,recorded_at,document_code,document_version,content_hash)
VALUES (1,1,TRUE,@recorded,'TERMS','rehearsal',REPEAT('b',64));
COMMIT;
"""
FIXTURE_TABLES = ("members", "products", "orders", "order_items", "payments", "member_policy_consents")


class RehearsalError(Exception):
    def __init__(self, reason, status="FAIL"):
        super().__init__(reason)
        self.status = status


def interruption(_signum, _frame):
    raise RehearsalError("INTERRUPTED")


def command(args, reason, *, cwd=ROOT, env=None, timeout=30, input_data=None, status="FAIL"):
    try:
        with subprocess.Popen(
            args, cwd=cwd, env=env, start_new_session=True, stderr=subprocess.DEVNULL,
            stdin=subprocess.PIPE if input_data is not None else subprocess.DEVNULL, stdout=subprocess.PIPE,
        ) as process:
            try:
                stdout, _ = process.communicate(input_data, timeout=timeout)
            except BaseException:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait()
                raise
    except (OSError, subprocess.TimeoutExpired):
        raise RehearsalError(reason, status) from None
    if process.returncode:
        raise RehearsalError(reason, status)
    return stdout


def export_candidate(candidate, workdir, env):
    archive = workdir / "candidate.tar"
    command(["git", "archive", "--format=tar", "-o", archive, candidate], "CANDIDATE_EXPORT_FAILED", env=env)
    snapshot = workdir / "snapshot"
    snapshot.mkdir()
    with tarfile.open(archive, "r:") as bundle:
        for entry in bundle.getmembers():
            path = Path(entry.name)
            if path.is_absolute() or ".." in path.parts or not (entry.isfile() or entry.isdir()):
                raise RehearsalError("CANDIDATE_ARCHIVE_UNSAFE")
        bundle.extractall(snapshot)
    command(["sh", "scripts/setup-release-config.sh"], "CANDIDATE_SETUP_FAILED", cwd=snapshot, env=env)
    return snapshot


class Stack:
    def __init__(self, snapshot, workdir, env):
        self.snapshot = snapshot
        self.env = env
        self.project = "rehearsal-" + uuid.uuid4().hex
        self.image = f"nanacocoa-server:{self.project}"
        self.docker = None
        self.compose = None
        self.owned = False
        self.config_path = workdir / "rehearsal.yaml"
        self.env_file = workdir / "rehearsal.env"

    def prepare(self):
        endpoint = command(
            ["docker", "context", "inspect", "--format", "{{.Endpoints.docker.Host}}"], "DOCKER_UNAVAILABLE",
            env=self.env, timeout=10, status="BLOCKED",
        ).decode().strip()
        if not re.fullmatch(r"unix:///[^\s]+", endpoint):
            raise RehearsalError("LOCAL_DOCKER_REQUIRED", "BLOCKED")
        self.docker = ["docker", "--host", endpoint]
        command([*self.docker, "info", "--format", "{{.ServerVersion}}"], "DOCKER_UNAVAILABLE",
                env=self.env, timeout=10, status="BLOCKED")
        command([*self.docker, "compose", "version"], "COMPOSE_UNAVAILABLE",
                env=self.env, timeout=10, status="BLOCKED")
        values = {
            "DB_NAME": SOURCE_DB,
            "DB_USERNAME": "rehearsal",
            **{name: secrets.token_hex(32) for name in
               ("DB_PASSWORD", "MYSQL_ROOT_PASSWORD", "REDIS_PASSWORD", "JWT_SECRET")},
        }
        self.env_file.write_text("".join(f"{key}={value}\n" for key, value in values.items()), encoding="utf-8")
        app_env = "\n".join(f"      {key}: {json.dumps(value)}" for key, value in SAFE_APP_ENV.items())
        self.config_path.write_text(f"""
services:
  app:
    image: {self.image}
    restart: "no"
    ports: !override
      - "127.0.0.1::8080"
    networks: !override [backend, loopback]
    environment:
{app_env}
  mysql:
    restart: "no"
    ports: !reset []
    volumes: !reset []
    tmpfs: ["/var/lib/mysql"]
    networks: !override [backend]
  redis:
    restart: "no"
    networks: !override [backend]
networks: !override
  backend:
    internal: true
  loopback:
    driver: bridge
    driver_opts:
      {NO_MASQUERADE}: "false"
volumes: !reset {{}}
""", encoding="utf-8")
        self.compose = [*self.docker, "compose", "--project-name", self.project,
                        "--project-directory", self.snapshot, "--env-file", self.env_file,
                        "-f", self.snapshot / "compose.yaml", "-f", self.config_path]
        config = json.loads(self.run("config", "--no-env-resolution", "--format", "json",
                                     reason="COMPOSE_CONFIG_FAILED"))
        self.validate_config(config)

    def validate_config(self, config):
        services = config.get("services", {})
        network = config.get("networks", {})
        if (config.get("name") != self.project or set(services) != {"app", "mysql", "redis"}
                or set(network) != {"backend", "loopback"} or not network["backend"].get("internal")
                or network["loopback"].get("driver") != "bridge"
                or network["loopback"].get("driver_opts") != {NO_MASQUERADE: "false"}
                or any(settings.get("external") or settings.get("name") != f"{self.project}_{name}"
                       for name, settings in network.items()) or config.get("volumes")
                or config.get("configs") or config.get("secrets")):
            raise RehearsalError("COMPOSE_ISOLATION_INVALID")
        for name, service in services.items():
            expected_networks = {"backend", "loopback"} if name == "app" else {"backend"}
            if (set(service.get("networks", {})) != expected_networks or service.get("network_mode")
                    or service.get("volumes") or service.get("volumes_from") or service.get("privileged")
                    or service.get("devices") or service.get("container_name") or service.get("env_file")
                    or service.get("pid") or service.get("ipc") or service.get("cap_add")
                    or service.get("post_start") or service.get("pre_stop")):
                raise RehearsalError("COMPOSE_ISOLATION_INVALID")
        app, mysql, redis = (services[name] for name in ("app", "mysql", "redis"))
        env = app["environment"]
        build = app.get("build", {})
        ports = app.get("ports", [])
        if (app.get("image") != self.image or Path(build.get("context", "")) != self.snapshot
                or build.get("dockerfile") != "Dockerfile" or build.get("additional_contexts")
                or build.get("ssh") or build.get("secrets") or build.get("entitlements")
                or len(ports) != 1 or ports[0].get("host_ip") != "127.0.0.1"
                or ports[0].get("target") != 8080 or ports[0].get("published") not in (None, "0")
                or mysql.get("ports") or redis.get("ports")
                or "/var/lib/mysql" not in mysql.get("tmpfs", [])
                or "/data" not in redis.get("tmpfs", [])
                or mysql.get("image") != f"mysql:{MYSQL_VERSION}"
                or any(env.get(key) != value for key, value in SAFE_APP_ENV.items())
                or not env.get("DB_URL", "").startswith(f"jdbc:mysql://mysql:3306/{SOURCE_DB}?")
                or mysql.get("environment", {}).get("MYSQL_DATABASE") != SOURCE_DB
                or env.get("REDIS_HOST") != "redis"):
            raise RehearsalError("COMPOSE_ISOLATION_INVALID")

    def run(self, *args, reason, timeout=30, input_data=None):
        return command([*self.compose, *args], reason, cwd=self.snapshot, env=self.env,
                       timeout=timeout, input_data=input_data)

    def start(self, checks):
        self.owned = True
        self.run("build", "app", reason="IMAGE_BUILD_FAILED", timeout=600)
        checks.append("candidate-image")
        self.wait("STACK_START_FAILED")

    def wait(self, reason):
        self.run("up", "-d", "--no-build", "--wait", "--wait-timeout", "150", reason=reason, timeout=180)

    def close(self):
        if not self.owned:
            return
        try:
            self.run("down", "--timeout", "10", reason="CLEANUP_FAILED", timeout=60)
        finally:
            images = command([*self.docker, "image", "ls", "-q", "--filter", f"reference={self.image}"],
                             "CLEANUP_FAILED", env=self.env)
            if images.strip():
                command([*self.docker, "image", "rm", self.image], "CLEANUP_FAILED", env=self.env)
        label = f"label=com.docker.compose.project={self.project}"
        for args in (("ps", "-aq"), ("network", "ls", "-q")):
            remaining = command([*self.docker, *args, "--filter", label], "CLEANUP_FAILED", env=self.env)
            if remaining.strip():
                raise RehearsalError("CLEANUP_FAILED")

    def port(self):
        mapping = self.run("port", "app", "8080", reason="APP_PORT_INVALID").decode().strip()
        match = re.fullmatch(r"127\.0\.0\.1:([1-9][0-9]{0,4})", mapping)
        if not match or int(match[1]) > 65535:
            raise RehearsalError("APP_PORT_INVALID")
        return int(match[1])

    def mysql(self, database, sql, reason):
        return self.mysql_tool("mysql", "--batch", "--skip-column-names", database,
                               reason=reason, input_data=sql)

    def mysql_tool(self, tool, *args, reason, input_data=None):
        return self.run("exec", "-T", "mysql", "sh", "-ec",
                        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec "$@"', "sh", tool, "-u", "root", *args,
                        reason=reason, timeout=60, input_data=input_data)


def http_json(port, path, reason, method="GET", payload=None, headers=None):
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
    try:
        connection.request(method, path, body=payload, headers=headers or {})
        response = connection.getresponse()
        data = json.loads(response.read(65537))
        if not isinstance(data, dict):
            raise RehearsalError(reason)
        return response.status, data, response.headers
    except (ValueError, OSError, http.client.HTTPException):
        raise RehearsalError(reason) from None
    finally:
        connection.close()


def session_cookies(headers, cookies):
    try:
        for value in headers.get_all("Set-Cookie", []):
            cookies.load(value)
    except CookieError:
        raise RehearsalError("PAYMENT_GUARD_FAILED") from None
    return "; ".join(f"{name}={cookie.coded_value}" for name, cookie in cookies.items())


def check_app(port):
    reason = "APP_HEALTH_FAILED"
    code, health, _ = http_json(port, "/actuator/health", reason)
    if code != 200 or health.get("status") != "UP":
        raise RehearsalError(reason)
    reason = "SIGNUP_GUARD_FAILED"
    code, catalog, _ = http_json(port, "/api/policies", reason)
    if code != 200 or catalog.get("data", {}).get("signupReady") is not False:
        raise RehearsalError(reason)


def check_payment_guard(port):
    reason = "PAYMENT_GUARD_FAILED"
    cookies = SimpleCookie()
    code, csrf, headers = http_json(port, "/api/auth/csrf", reason)
    if code != 200 or csrf.get("data", {}).get("headerName") != "X-CSRF-TOKEN":
        raise RehearsalError(reason)
    code, _, headers = http_json(
        port, "/api/auth/login", reason, method="POST",
        payload=json.dumps({"email": FIXTURE_EMAIL, "password": FIXTURE_PASSWORD}),
        headers={"Content-Type": "application/json", "X-CSRF-TOKEN": csrf["data"]["token"],
                 "Cookie": session_cookies(headers, cookies)},
    )
    if code != 200:
        raise RehearsalError(reason)
    session = session_cookies(headers, cookies)
    code, payment, _ = http_json(port, "/api/payments/client-config", reason,
                                 headers={"Cookie": session})
    if code != 503 or payment.get("code") != "PAYMENT_11" or payment.get("data") is not None:
        raise RehearsalError(reason)
    return session


def check_schema(stack):
    version = stack.mysql(SOURCE_DB, b"SELECT VERSION();", "MYSQL_VERSION_CHECK_FAILED")
    if version.decode().strip() != MYSQL_VERSION:
        raise RehearsalError("MYSQL_VERSION_MISMATCH")
    migration_dir = stack.snapshot / "src/main/resources/db/migration"
    expected = sorted(path.name for path in migration_dir.glob("V*__*.sql") if path.is_file())
    actual = stack.mysql(
        SOURCE_DB, b"SELECT script FROM flyway_schema_history WHERE version IS NOT NULL OR NOT success;",
        "FLYWAY_CHECK_FAILED",
    )
    if not expected or sorted(actual.decode().splitlines()) != expected:
        raise RehearsalError("FLYWAY_HISTORY_MISMATCH")


def fingerprint(stack, database, reason):
    counts = ", ".join(f"(SELECT COUNT(*) FROM {table})" for table in FIXTURE_TABLES)
    query = f"SELECT {counts};\n" + "".join(f"SELECT * FROM {table} ORDER BY id;\n" for table in FIXTURE_TABLES)
    rows = stack.mysql(database, query.encode(), reason).splitlines()
    if len(rows) != len(FIXTURE_TABLES) + 1 or rows[0].split(b"\t") != [b"1"] * len(FIXTURE_TABLES):
        raise RehearsalError(reason)
    return rows


def rehearse(stack, checks):
    stack.start(checks)
    port = stack.port()
    check_app(port)
    checks.append("stack-health")
    check_schema(stack)
    checks.append("flyway-jpa")
    stack.mysql(SOURCE_DB, FIXTURE_SQL.encode(), "FIXTURE_LOAD_FAILED")
    check_payment_guard(port)
    checks.append("safe-defaults")
    before = fingerprint(stack, SOURCE_DB, "FIXTURE_READ_FAILED")
    stack.run("restart", "app", reason="APP_RESTART_FAILED", timeout=60)
    stack.wait("APP_RESTART_FAILED")
    check_app(stack.port())
    if fingerprint(stack, SOURCE_DB, "RESTART_DATA_MISMATCH") != before:
        raise RehearsalError("RESTART_DATA_MISMATCH")
    checks.append("restart-data")
    dump = stack.mysql_tool("mysqldump", "--single-transaction", "--no-tablespaces",
                            "--set-gtid-purged=OFF", "--skip-comments", SOURCE_DB, reason="DB_DUMP_FAILED")
    stack.mysql(SOURCE_DB, b"CREATE DATABASE rehearsal_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;",
                "RESTORE_SCHEMA_FAILED")
    stack.mysql(RESTORE_DB, dump, "DB_RESTORE_FAILED")
    if fingerprint(stack, RESTORE_DB, "RESTORE_DATA_MISMATCH") != before:
        raise RehearsalError("RESTORE_DATA_MISMATCH")
    checks.append("restore-data")


def run_candidate(candidate, checks):
    env = {key: os.environ[key] for key in HOST_ENV if key in os.environ}
    head = command(["git", "rev-parse", "--verify", "HEAD"], "HEAD_UNAVAILABLE", env=env).decode().strip()
    if head != candidate:
        raise RehearsalError("CANDIDATE_NOT_HEAD")
    checks.append("candidate-commit")
    with tempfile.TemporaryDirectory(prefix="nanacocoa-release-rehearsal-") as workspace:
        workdir = Path(workspace).resolve()
        snapshot = export_candidate(candidate, workdir, env)
        stack = Stack(snapshot, workdir, env)
        try:
            stack.prepare()
            rehearse(stack, checks)
        finally:
            if stack.owned:
                stack.close()
                checks.append("cleanup")


def main(args):
    candidate = args[1] if len(args) == 2 and args[0] == "--candidate" and SHA.fullmatch(args[1]) else None
    checks = []
    status, reason = "PASS", "REHEARSAL_VERIFIED"
    if candidate is None:
        status, reason = "FAIL", "EXPECTED_FULL_CANDIDATE_SHA"
    else:
        try:
            run_candidate(candidate, checks)
        except RehearsalError as error:
            status, reason = error.status, str(error)
        except (OSError, ValueError, KeyError, TypeError, AttributeError, tarfile.TarError):
            status, reason = "FAIL", "REHEARSAL_INPUT_OR_RUNTIME_INVALID"
    exit_code = {"PASS": 0, "FAIL": 1, "BLOCKED": 2}[status]
    report = {"schemaVersion": 1, "result": status, "exitCode": exit_code, "reason": reason, "completed": checks}
    if "candidate-commit" in checks:
        report["check"] = {
            "id": "REHEARSAL",
            "status": status,
            "candidateCommit": candidate,
            "environment": "isolated",
            "role": "release-rehearsal",
            "verifiedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
            "evidenceId": "ev-" + uuid.uuid4().hex,
        }
    print(json.dumps(report))
    return exit_code


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, interruption)
    signal.signal(signal.SIGINT, interruption)
    sys.exit(main(sys.argv[1:]))
