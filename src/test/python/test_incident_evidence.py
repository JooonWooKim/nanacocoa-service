"""Regression tests for the offline incident evidence input and output contract."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("incident_evidence", ROOT / "scripts/harness.d/incident-evidence.py")
CLI = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CLI)
CANDIDATE = "a" * 40
REQUEST = "155dd4fe-3483-4fde-8930-fcb520b705f1"
OTHER = "04346762-897e-4755-9146-96b0884d67a2"
RUN = "ed58be6b-ccb1-4fde-b01f-1cf4efd2516c"


def event(operation, result, *, identifier=REQUEST, fraction="2", http_status=409):
    data = {"kind": "HTTP" if http_status is not None else "RECOVERY", "id": identifier,
            "occurredAt": f"2026-11-26T05:00:00.{fraction}Z", "operation": operation,
            "result": result, "durationMs": 5}
    if http_status is not None:
        data["httpStatus"] = http_status
    return data


class IncidentEvidenceTest(unittest.TestCase):
    def run_manifest(self, events, *, paths=(), run=False, head=CANDIDATE, references=True):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "events.json"
            source.write_text(json.dumps({"candidateCommit": CANDIDATE,
                                          "events": events, "changedPaths": list(paths)}), encoding="utf-8")
            output = io.StringIO()
            with (patch.object(CLI, "head_commit", return_value=head),
                  patch.object(CLI, "candidate_has_references", return_value=references),
                  contextlib.redirect_stdout(output)):
                code = CLI.main(["--input", str(source), "--run-id" if run else "--request-id",
                                 RUN if run else REQUEST])
        return code, json.loads(output.getvalue())

    def test_stock_reservation_guidance_excludes_other_requests_and_adds_changed_domain(self):
        code, report = self.run_manifest([
            event("ORDER_CREATE", "STOCK_CONFLICT"),
            event("PAYMENT_CONFIRM", "PENDING", identifier=OTHER, http_status=202),
        ], paths=["src/main/java/com/nanacocoa/server/payment/facade/PaymentFacade.java"])

        self.assertEqual(0, code)
        self.assertEqual("STOCK_RESERVATION", report["findings"][0]["stage"])
        self.assertEqual([REQUEST], [row["id"] for row in report["timeline"]])
        tests = {row["class"]: row["reasons"] for row in report["recommendedTests"]}
        self.assertIn("STOCK_RESERVATION", tests["OrderServiceTest"])
        self.assertIn("CHANGED_DOMAIN", tests["PaymentOperationsIntegrationTest"])
        self.assertIn("./scripts/harness check", report["commands"])
        self.assertIn("./scripts/harness mysql-quality", report["commands"])
        self.assertFalse(report["fullCheck"])

    def test_uncertain_payment_with_unknown_change_preserves_lookup_advice(self):
        code, report = self.run_manifest(
            [event("PAYMENT_CONFIRM", "PENDING", http_status=202)], paths=["unmapped.file"])

        self.assertEqual(0, code)
        self.assertEqual("APPROVAL_UNCERTAIN", report["findings"][0]["stage"])
        self.assertIn("조회", report["findings"][0]["procedure"])
        self.assertTrue(report["fullCheck"])
        self.assertNotIn("unmapped.file", json.dumps(report))

    def test_automatic_recovery_orders_attempts_to_nanosecond_precision(self):
        code, report = self.run_manifest([
            event("RECOVERY_BATCH", "SUCCESS", identifier=RUN, fraction="123456782", http_status=None),
            event("RECOVERY_CANCELLATION", "FAILED", identifier=RUN, fraction="123456781", http_status=None),
        ], run=True)

        self.assertEqual(0, code)
        self.assertEqual(["RECOVERY_CANCELLATION", "RECOVERY_BATCH"],
                         [row["operation"] for row in report["timeline"]])
        self.assertEqual("RECOVERY_CANCELLATION", report["findings"][0]["stage"])
        self.assertIn("ReturnRequestIntegrationTest", [row["class"] for row in report["recommendedTests"]])

    def test_missing_failure_and_candidate_mismatch_are_blocked(self):
        success = event("ORDER_CREATE", "SUCCESS", http_status=201)
        self.assertEqual((2, "MISSING_FAILURE_EVIDENCE"),
                         self.reason(self.run_manifest([success])))
        self.assertEqual((2, "CANDIDATE_MISMATCH"),
                         self.reason(self.run_manifest([success], head="b" * 40)))
        self.assertEqual((2, "CANDIDATE_REFERENCES_MISSING"),
                         self.reason(self.run_manifest([event("ORDER_CREATE", "STOCK_CONFLICT")], references=False)))

    def test_disallowed_input_is_rejected_without_reflecting_it(self):
        unrelated = event("PAYMENT_CONFIRM", "PENDING", identifier=OTHER, http_status=202)
        unrelated["untrustedSensitiveMarker"] = "synthetic-secret"
        code, report = self.run_manifest([event("ORDER_CREATE", "STOCK_CONFLICT"), unrelated])
        self.assertEqual(1, code)
        self.assertEqual({"status": "FAIL", "reason": "INVALID_EVENT_FIELDS"}, report)
        self.assertEqual((1, "INVALID_CHANGED_PATH"),
                         self.reason(self.run_manifest([], paths=["../outside"])))
        self.assertEqual((1, "INVALID_EVENT_VALUE"),
                         self.reason(self.run_manifest([event("PRIVATE_OPERATION", "FAILED")])))

    @staticmethod
    def reason(result):
        code, report = result
        return code, report["reason"]


if __name__ == "__main__":
    unittest.main()
