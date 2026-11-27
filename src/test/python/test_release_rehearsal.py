"""Regression tests for candidate ownership, restore evidence, and failure isolation."""

import contextlib
import importlib.util
import io
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[3]
CANDIDATE = "a" * 40


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts/harness.d" / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


rehearsal = load("release_rehearsal", "release-rehearsal.py")
preflight = load("release_preflight", "release-preflight.py")


class ReleaseRehearsalTest(unittest.TestCase):
    def run_cli(self, *args):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = rehearsal.main(args)
        return code, json.loads(output.getvalue())

    def test_rejects_abbreviated_candidate_without_running_any_commands(self):
        with mock.patch.object(rehearsal, "command") as command:
            code, report = self.run_cli("--candidate", "aaaaaaa")
        self.assertEqual((code, report["reason"]), (1, "EXPECTED_FULL_CANDIDATE_SHA"))
        self.assertNotIn("check", report)
        command.assert_not_called()

    def test_head_mismatch_cannot_issue_attestation(self):
        with mock.patch.object(rehearsal, "command", return_value=b"b" * 40) as command:
            code, report = self.run_cli("--candidate", CANDIDATE)
        self.assertEqual((code, report["reason"]), (1, "CANDIDATE_NOT_HEAD"))
        self.assertNotIn("check", report)
        self.assertEqual(command.call_args.args[0], ["git", "rev-parse", "--verify", "HEAD"])

    def test_candidate_snapshot_excludes_local_edits_and_untracked_files(self):
        with tempfile.TemporaryDirectory() as workspace:
            root = Path(workspace)
            checkout, target = root / "checkout", root / "archive"
            (checkout / "scripts").mkdir(parents=True)
            target.mkdir()
            (checkout / "scripts/setup-release-config.sh").write_text("cp template generated\n")
            (checkout / "template").write_text("committed\n")
            git = ["git", "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgSign=false",
                   "-c", "user.name=Fixture", "-c", "user.email=git@example.invalid"]
            subprocess.run([*git, "init", "--quiet"], cwd=checkout, check=True)
            subprocess.run([*git, "add", "scripts", "template"], cwd=checkout, check=True)
            subprocess.run([*git, "commit", "--quiet", "-m", "fixture"], cwd=checkout, check=True)
            candidate = subprocess.check_output([*git, "rev-parse", "HEAD"], cwd=checkout).decode().strip()
            (checkout / "template").write_text("dirty\n")
            (checkout / ".env").write_text("FIXTURE_UNTRACKED=1\n")
            command = rehearsal.command

            def from_fixture(args, reason, *, cwd=checkout, **kwargs):
                return command(args, reason, cwd=cwd, **kwargs)

            with mock.patch.object(rehearsal, "command", side_effect=from_fixture):
                snapshot = rehearsal.export_candidate(candidate, target, None)
            self.assertEqual((snapshot / "generated").read_text(), "committed\n")
            self.assertFalse((snapshot / ".env").exists())

    def test_missing_docker_is_blocked_and_remote_daemon_is_not_used(self):
        stack = rehearsal.Stack(Path("/tmp/snapshot"), Path("/tmp"), {})
        with mock.patch.object(rehearsal, "command", side_effect=rehearsal.RehearsalError(
                "DOCKER_UNAVAILABLE", "BLOCKED")):
            with self.assertRaisesRegex(rehearsal.RehearsalError, "DOCKER_UNAVAILABLE") as blocked:
                stack.prepare()
        self.assertEqual(blocked.exception.status, "BLOCKED")
        with mock.patch.object(rehearsal, "command", return_value=b"ssh://remote-docker") as command:
            with self.assertRaisesRegex(rehearsal.RehearsalError, "LOCAL_DOCKER_REQUIRED"):
                stack.prepare()
        command.assert_called_once()
        self.assertFalse(stack.owned)

    def test_rejects_host_binds_external_networks_and_database_ports(self):
        for option, risky_value in (
            ("volumes", [{"type": "bind", "source": "/unrelated"}]),
            ("network_mode", "host"),
            ("ports", [{"target": 3306, "host_ip": "127.0.0.1"}]),
        ):
            with self.subTest(option=option):
                stack = rehearsal.Stack(Path("/tmp/snapshot"), Path("/tmp"), {})
                config = {
                    "name": stack.project,
                    "networks": {
                        "backend": {"internal": True, "name": f"{stack.project}_backend"},
                        "loopback": {"driver": "bridge", "name": f"{stack.project}_loopback",
                                     "driver_opts": {rehearsal.NO_MASQUERADE: "false"}},
                    },
                    "services": {
                        "app": {"image": stack.image, "networks": {"backend": None, "loopback": None},
                                "environment": {**rehearsal.SAFE_APP_ENV, "REDIS_HOST": "redis",
                                                "DB_URL": "jdbc:mysql://mysql:3306/rehearsal_source?fixture"},
                                "build": {"context": str(stack.snapshot), "dockerfile": "Dockerfile"},
                                "ports": [{"target": 8080, "host_ip": "127.0.0.1"}]},
                        "mysql": {"networks": {"backend": None}, "tmpfs": ["/var/lib/mysql"],
                                  "environment": {"MYSQL_DATABASE": rehearsal.SOURCE_DB},
                                  "image": f"mysql:{rehearsal.MYSQL_VERSION}"},
                        "redis": {"networks": {"backend": None}, "tmpfs": ["/data"]},
                    },
                }
                stack.validate_config(config)
                config["services"]["mysql"][option] = risky_value
                with self.assertRaisesRegex(rehearsal.RehearsalError, "COMPOSE_ISOLATION_INVALID"):
                    stack.validate_config(config)
        config["services"]["mysql"].pop("ports")
        config["networks"]["backend"]["external"] = True
        with self.assertRaisesRegex(rehearsal.RehearsalError, "COMPOSE_ISOLATION_INVALID"):
            stack.validate_config(config)

    def test_restore_mismatch_fails_and_cleanup_still_runs(self):
        with mock.patch.object(rehearsal, "command", return_value=CANDIDATE.encode()), \
                mock.patch.object(rehearsal, "export_candidate"), \
                mock.patch.object(rehearsal, "Stack") as stack_factory, \
                mock.patch.object(rehearsal, "check_app"), mock.patch.object(rehearsal, "check_schema"), \
                mock.patch.object(rehearsal, "check_payment_guard"), \
                mock.patch.object(rehearsal, "fingerprint", side_effect=[b"original", b"original", b"different"]):
            stack = stack_factory.return_value
            stack.owned = True
            stack.mysql_tool.return_value = b"synthetic-dump-hidden"
            code, report = self.run_cli("--candidate", CANDIDATE)
        self.assertEqual((code, report["reason"]), (1, "RESTORE_DATA_MISMATCH"))
        self.assertEqual(report["check"]["status"], "FAIL")
        self.assertIn("restart-data", report["completed"])
        self.assertIn("cleanup", report["completed"])
        self.assertNotIn("restore-data", report["completed"])
        self.assertNotIn("synthetic-dump-hidden", json.dumps(report))
        self.assertEqual(stack.mysql.call_args.args[:2], (rehearsal.RESTORE_DB, b"synthetic-dump-hidden"))
        stack.close.assert_called_once()

    def test_cleanup_addresses_only_this_projects_resources(self):
        stack = rehearsal.Stack(Path("/tmp/snapshot"), Path("/tmp"), {})
        stack.docker = ["docker", "--host", "unix:///docker.sock"]
        stack.owned = True
        with mock.patch.object(stack, "run") as compose, \
                mock.patch.object(rehearsal, "command", side_effect=[b"image-id", b"", b"", b""]) as docker:
            stack.close()
        compose.assert_called_once_with("down", "--timeout", "10", reason="CLEANUP_FAILED", timeout=60)
        calls = [call.args[0][3:] for call in docker.call_args_list]
        owned_label = f"label=com.docker.compose.project={stack.project}"
        self.assertEqual(calls, [
            ["image", "ls", "-q", "--filter", f"reference={stack.image}"],
            ["image", "rm", stack.image], ["ps", "-aq", "--filter", owned_label],
            ["network", "ls", "-q", "--filter", owned_label],
        ])

    def test_cleanup_failure_cannot_issue_pass_evidence(self):
        with mock.patch.object(rehearsal, "command", return_value=CANDIDATE.encode()), \
                mock.patch.object(rehearsal, "export_candidate"), mock.patch.object(rehearsal, "rehearse"), \
                mock.patch.object(rehearsal, "Stack") as stack_factory:
            stack_factory.return_value.close.side_effect = rehearsal.RehearsalError("CLEANUP_FAILED")
            code, report = self.run_cli("--candidate", CANDIDATE)
        self.assertEqual((code, report["reason"]), (1, "CLEANUP_FAILED"))
        self.assertEqual(report["check"]["status"], "FAIL")
        self.assertNotIn("cleanup", report["completed"])

    def test_success_evidence_is_accepted_by_release_preflight(self):
        with mock.patch.object(rehearsal, "run_candidate", side_effect=lambda _, checks: checks.append(
                "candidate-commit")):
            code, report = self.run_cli("--candidate", CANDIDATE)
        self.assertEqual(code, 0)
        manifest = {"schemaVersion": 1, "candidateCommit": CANDIDATE,
                    "targetEnvironment": "production", "checks": [report["check"]]}
        read_stdout, read_stderr = io.StringIO(), io.StringIO()
        with mock.patch.object(preflight, "read_manifest", return_value=manifest), \
                contextlib.redirect_stdout(read_stdout), contextlib.redirect_stderr(read_stderr):
            result = preflight.main(["--input", "manifest.json"])
        self.assertEqual(result, 2)
        checks = json.loads(read_stdout.getvalue())["checks"]
        self.assertEqual({row["id"]: row["status"] for row in checks}["REHEARSAL"], "PASS")


if __name__ == "__main__":
    unittest.main()
