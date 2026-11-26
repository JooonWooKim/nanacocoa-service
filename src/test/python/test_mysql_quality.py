import importlib.util
import json
import os
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("mysql_quality", ROOT / "scripts/harness.d/mysql-quality.py")
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)
CONTAINER = "a" * 64


class MysqlQualityTest(unittest.TestCase):
    def setUp(self):
        environment = patch.dict(os.environ)
        environment.start()
        self.addCleanup(environment.stop)
        os.environ.pop("DOCKER_HOST", None)

    def fake_docker(self, calls, unavailable=False, remote=False):
        def run(*args, **_):
            calls.append(args)
            return {
                "info": (1 if unavailable else 0, ""),
                "context": (0, "ssh://example.test" if remote else "unix:///fixture.sock"),
                "create": (0, CONTAINER),
                "start": (0, ""),
                "port": (0, "127.0.0.1:39294"),
                "exec": (0, ""),
                "rm": (0, ""),
            }[args[0]]
        return run

    def test_lifecycle_builds_only_fixture_urls_and_removes_owned_container(self):
        calls = []

        def run_gradle(args, *, env, timeout):
            self.assertEqual(args[-1], "mysqlQualityTest")
            self.assertIn("--no-daemon", args)
            db_urls = [env[key] for key, _ in gate.SUITES.values()]
            self.assertEqual(len(set(db_urls)), 5)
            self.assertTrue(all(url.startswith("jdbc:mysql://127.0.0.1:39294/") for url in db_urls))
            self.assertNotIn("SPRING_APPLICATION_JSON", env)
            self.assertNotIn("TOSS_PAYMENTS_SECRET_KEY", env)
            self.assertEqual(env["PRODUCT_IMAGE_STORAGE_MODE"], "mock")
            self.assertEqual(env["TOSS_PAYMENTS_ENABLED"], "false")
            self.assertTrue((Path(env["MYSQL_QUALITY_XML_DIR"]).parent / "application-test.yml").is_file())
            return 0, ""

        with patch.object(gate.shutil, "which", return_value="/fixture/bin/docker"), \
                patch.object(gate, "docker", self.fake_docker(calls)), \
                patch.object(gate, "wait_for_mysql") as ready, \
                patch.object(gate, "command", side_effect=run_gradle), \
                patch.object(gate, "read_results"), \
                patch.dict(os.environ, {
                    "INVENTORY_TEST_DB_URL": "jdbc:mysql://production.example.test/inventory",
                    "SPRING_APPLICATION_JSON": "synthetic-unsafe-config",
                    "TOSS_PAYMENTS_SECRET_KEY": "synthetic-secret",
                }):
            gate.execute_gate({})
        ready.assert_called_once_with(CONTAINER, 39294)
        self.assertIn("127.0.0.1::3306", calls[2])
        self.assertEqual(calls[2][-1], f"mysql:{gate.MYSQL_VERSION}")
        create_schemas = next(args[-1] for args in calls if args[0] == "exec")
        self.assertEqual(create_schemas.count("CREATE DATABASE "), 5)
        self.assertEqual(calls[-1], ("rm", "--force", CONTAINER))

    def test_unavailable_or_remote_docker_blocks_before_create(self):
        for unavailable, remote, reason in [(True, False, "DOCKER_UNAVAILABLE"),
                                             (False, True, "DOCKER_LOCAL_REQUIRED")]:
            with self.subTest(reason=reason):
                calls = []
                with patch.object(gate.shutil, "which", return_value="/fixture/bin/docker"), \
                        patch.object(gate, "docker", self.fake_docker(calls, unavailable, remote)):
                    with self.assertRaisesRegex(gate.GateError, reason) as error:
                        gate.execute_gate({})
                self.assertEqual(error.exception.status, "BLOCKED")
                self.assertNotIn("create", [args[0] for args in calls])

    def test_timeout_and_interruption_clean_up_only_owned_container(self):
        for error in (gate.GateError("MYSQL_READY_TIMEOUT"), gate.Interrupted()):
            with self.subTest(error=type(error).__name__):
                calls = []
                with patch.object(gate.shutil, "which", return_value="/fixture/bin/docker"), \
                        patch.object(gate, "docker", self.fake_docker(calls)), \
                        patch.object(gate, "wait_for_mysql", side_effect=error):
                    with self.assertRaises(type(error)):
                        gate.execute_gate({})
                self.assertEqual(calls[-1], ("rm", "--force", CONTAINER))

    def test_ready_deadline_has_fixed_failure_reason(self):
        with patch.object(gate.time, "monotonic", side_effect=[0, gate.READY_SECONDS]):
            with self.assertRaisesRegex(gate.GateError, "MYSQL_READY_TIMEOUT"):
                gate.wait_for_mysql(CONTAINER, 39294)

    def test_cleanup_can_find_container_by_only_this_runs_label(self):
        with patch.object(gate, "docker", side_effect=[(0, CONTAINER), (0, "")]) as docker:
            self.assertTrue(gate.cleanup_container("", "fixture-run"))
        self.assertIn(f"label={gate.LABEL}=fixture-run", docker.call_args_list[0].args)
        self.assertEqual(docker.call_args.args, ("rm", "--force", CONTAINER))

    def test_cleanup_failure_cannot_report_success(self):
        calls = []
        fake = self.fake_docker(calls)
        with patch.object(gate.shutil, "which", return_value="/fixture/bin/docker"), \
                patch.object(gate, "docker", side_effect=lambda *a, **kw: (
                    (1, "") if a[0] in {"rm", "ps"} else fake(*a, **kw)
                )), patch.object(gate, "wait_for_mysql"), \
                patch.object(gate, "command", return_value=(0, "")), patch.object(gate, "read_results"):
            with self.assertRaisesRegex(gate.GateError, "MYSQL_CLEANUP_FAILED"):
                gate.execute_gate({})

    def test_results_reject_missing_and_sanitize_failed_and_skipped_ids(self):
        inventory = (ROOT / "harness/baselines/test-inventory.txt").read_text().splitlines()
        expected = [entry for entry in inventory if entry.partition("#")[0] in gate.SUITES]
        with tempfile.TemporaryDirectory() as tmp:
            stage = Path(tmp)
            with self.assertRaisesRegex(gate.GateError, "TEST_RESULTS_INVALID"):
                gate.read_results(stage, {})
            (stage / "xml").mkdir()
            skipped, omitted, failed = expected[:3]
            for omit in (False, True):
                ids = [entry for entry in expected if not (omit and entry == omitted)]
                suite = ET.Element("testsuite", name="fixture", tests=str(len(ids)), failures="1", errors="0", skipped="1")
                for entry in ids:
                    cls, name = entry.split("#")
                    test = ET.SubElement(suite, "testcase", name=name, classname=cls)
                    if entry == failed:
                        ET.SubElement(test, "failure", message="jdbc:mysql://synthetic-value", type="FixtureError")
                    if entry == skipped:
                        ET.SubElement(test, "skipped")
                ET.indent(suite)
                (stage / "xml/TEST-fixture.xml").write_text(ET.tostring(suite, encoding="unicode"))
                report = {}
                reason = "TEST_INVENTORY_MISMATCH" if omit else "TEST_FAILURE_OR_SKIP"
                with self.assertRaisesRegex(gate.GateError, reason):
                    gate.read_results(stage, report)
                self.assertEqual(report["failedTestIds"], [failed])
                self.assertEqual(report["skippedTestIds"], [skipped])
                self.assertEqual(report["missingTestIds"], [omitted] if omit else [])
                self.assertNotIn("synthetic-value", json.dumps(report))


if __name__ == "__main__":
    unittest.main()
