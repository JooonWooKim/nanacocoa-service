import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
COMMAND = ROOT / "scripts" / "harness"
COMMIT = "12" * 20
IDS = ("QUALITY", "REHEARSAL", "POLICY", "TARGET", "PAYMENT", "ROLLOUT")
MARKER = "sample-sensitive-value-never-print"


def manifest():
    return {
        "schemaVersion": 1,
        "candidateCommit": COMMIT,
        "targetEnvironment": "production",
        "checks": [
            {
                "id": check_id,
                "status": "PASS",
                "candidateCommit": COMMIT,
                "environment": {"QUALITY": "ci", "REHEARSAL": "isolated"}.get(check_id, "production"),
                "role": "release-operator",
                "verifiedAt": "2026-11-26T13:00:00+09:00",
                "evidenceId": f"ev-{number:016x}",
            }
            for number, check_id in enumerate(IDS, 1)
        ],
    }


class ReleasePreflightTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.path = Path(directory.name) / f"{MARKER}.json"

    def invoke(self, *args, code):
        result = subprocess.run([str(COMMAND), "release-preflight", *args],
                                cwd=self.path.parent, capture_output=True, text=True, timeout=5)
        self.assertNotIn(MARKER, result.stdout + result.stderr)
        self.assertNotIn(str(self.path.parent), result.stdout + result.stderr)
        self.assertEqual(result.returncode, code, result.stderr)
        report = json.loads(result.stdout)
        self.assertEqual(report["exitCode"], code)
        self.assertEqual(report["result"], ("REVIEWABLE", "FAIL", "BLOCKED")[code])
        return report, result.stderr

    def check(self, data, code, reason=None):
        self.path.write_text(data if isinstance(data, str) else json.dumps(data), encoding="utf-8")
        report, stderr = self.invoke("--input", self.path.name, code=code)
        if reason:
            self.assertEqual(report["reason"], reason)
        return report, stderr

    def test_pass_uses_fixed_order_and_does_not_echo_attestations(self):
        data = manifest()
        data["checks"].reverse()
        report, stderr = self.check(data, 0, "CHECKS_SATISFIED")
        self.assertIn("검토 가능", stderr)
        self.assertEqual(report["candidateCommit"], COMMIT)
        self.assertEqual([entry["id"] for entry in report["checks"]], list(IDS))
        output = json.dumps(report) + stderr
        for value in ("release-operator", "ev-0000000000000001", "13:00:00", "production"):
            self.assertNotIn(value, output)

    def test_baselined_needs_complete_approval(self):
        data = manifest()
        row = data["checks"][0]
        row["status"] = "BASELINED"
        self.assertEqual(self.check(data, 2)[0]["checks"][0]["reason"], "MISSING_BASELINE_APPROVAL")
        row["baselineApproval"] = {"role": "release-lead", "verifiedAt": "2026-11-26T04:00:00Z"}
        self.assertEqual(self.check(data, 2)[0]["checks"][0]["reason"], "MISSING_BASELINE_APPROVAL")
        row["baselineApproval"]["evidenceId"] = row["evidenceId"]
        self.assertEqual(self.check(data, 1)[0]["checks"][0]["reason"], "BASELINE_APPROVAL_REUSES_EVIDENCE")
        row["baselineApproval"]["evidenceId"] = "ev-1111111111111111"
        self.assertEqual(self.check(data, 0)[0]["checks"][0]["status"], "BASELINED")

    def test_missing_input_or_required_fields_block(self):
        self.assertEqual(self.invoke(code=2)[0]["reason"], "MISSING_INPUT")
        self.assertEqual(self.invoke("--input", self.path.name, code=2)[0]["reason"], "INPUT_NOT_FOUND")
        for field in ("id", "status", "candidateCommit", "environment", "role", "verifiedAt", "evidenceId"):
            with self.subTest(field=field):
                data = manifest()
                del data["checks"][0][field]
                self.check(data, 2)
        data = manifest()
        del data["targetEnvironment"]
        self.check(data, 2, "MISSING_MANIFEST_FIELDS")
        data = manifest()
        data["checks"].pop()
        self.assertEqual(self.check(data, 2)[0]["checks"][-1]["reason"], "MISSING_CHECK")

    def test_failed_check_overrides_blocked_and_skipped(self):
        data = manifest()
        data["checks"][0]["status"] = "SKIPPED"
        data["checks"][1]["status"] = "BLOCKED"
        self.check(data, 2, "CHECK_INCOMPLETE")
        data["checks"][2]["status"] = "FAIL"
        data["checks"].pop()
        report, _ = self.check(data, 1, "CHECK_FAILED")
        self.assertEqual([entry["status"] for entry in report["checks"][:3]], ["SKIPPED", "BLOCKED", "FAIL"])

    def test_candidate_and_environment_mismatches_fail(self):
        data = manifest()
        data["checks"][0]["candidateCommit"] = "34" * 20
        data["checks"][1]["environment"] = "ci"
        data["checks"][2]["environment"] = "isolated"
        report, _ = self.check(data, 1)
        self.assertEqual([entry["reason"] for entry in report["checks"][:3]],
                         ["CANDIDATE_MISMATCH", "ENVIRONMENT_MISMATCH", "ENVIRONMENT_MISMATCH"])

    def test_invalid_fields_and_values_fail_without_reflecting_input(self):
        for field, value in (("status", MARKER), ("role", "buyer@example.test"),
                             ("evidenceId", "https://example.test/evidence"),
                             ("verifiedAt", "2026-02-30T00:00:00Z"),
                             ("verifiedAt", "2026-11-26T12:00:00+00:60"),
                             ("verifiedAt", "2026-11-26T12:00:00"),
                             ("environment", "https://example.test"),
                             ("id", ["QUALITY"]), ("rawLog", MARKER)):
            with self.subTest(field=field, value=value):
                data = manifest()
                data["checks"][0][field] = value
                self.check(data, 1)
        data = manifest()
        data["targetEnvironment"] = "ci"
        self.check(data, 1, "INVALID_FIELD_VALUE")
        data = manifest()
        data["schemaVersion"] = True
        self.check(data, 1, "INVALID_FIELD_VALUE")
        data = manifest()
        data["checks"][0]["baselineApproval"] = {"unexpected": MARKER}
        self.check(data, 1, "UNEXPECTED_BASELINE_APPROVAL")

    def test_invalid_json_and_duplicate_keys_or_ids_fail(self):
        self.check('{"checks":', 1, "INVALID_JSON")
        self.check('{"schemaVersion": NaN}', 1, "INVALID_JSON")
        self.check('{"checks":[{"role":"a","role":"b"}]}', 1, "DUPLICATE_JSON_KEY")
        data = manifest()
        data["checks"].append(data["checks"][0])
        self.check(data, 1, "DUPLICATE_CHECK_ID")

    def test_special_files_and_large_input_fail(self):
        self.path.write_text(" " * (64 * 1024 + 1), encoding="utf-8")
        self.assertEqual(self.invoke("--input", self.path.name, code=1)[0]["reason"], "INPUT_TOO_LARGE")
        fifo = self.path.with_suffix(".fifo")
        os.mkfifo(fifo)
        self.assertEqual(self.invoke("--input", fifo.name, code=1)[0]["reason"], "NOT_REGULAR_FILE")
        symlink = self.path.with_suffix(".link")
        symlink.symlink_to(self.path)
        self.assertEqual(self.invoke("--input", symlink.name, code=1)[0]["reason"], "INPUT_UNREADABLE")
        self.assertEqual(self.invoke(MARKER, code=1)[0]["reason"], "INVALID_ARGUMENTS")


if __name__ == "__main__":
    unittest.main()
