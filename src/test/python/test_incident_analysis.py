"""Regression tests for the bounded, read-only incident analyzer."""

import importlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts/harness.d"))
ANALYSIS = importlib.import_module("incident-analyze")
REQUEST = "155dd4fe-3483-4fde-8930-fcb520b705f1"
OTHER = "04346762-897e-4755-9146-96b0884d67a2"
RUN = "ed58be6b-ccb1-4fde-b01f-1cf4efd2516c"


def event(operation, result, *, identifier=REQUEST, status=409):
    entry = {"kind": "HTTP" if status is not None else "RECOVERY", "id": identifier,
             "occurredAt": "2026-11-27T01:00:00Z", "operation": operation, "result": result, "durationMs": 3}
    if status is not None:
        entry["httpStatus"] = status
    return entry


class IncidentAnalysisTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.candidate = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.source = Path(directory.name) / "events.json"

    def write(self, events, candidate=None):
        self.source.write_bytes(ANALYSIS.as_bytes({"candidateCommit": candidate or self.candidate, "events": events}))

    def args(self, *, run=False):
        return ["--input", str(self.source), "--run-id" if run else "--request-id", RUN if run else REQUEST]

    def cli(self, *, run=False):
        command = ROOT / "scripts/harness"
        result = subprocess.run([str(command), "incident-analyze", *self.args(run=run)], cwd=ROOT,
                                capture_output=True, text=True, timeout=10)
        self.assertEqual("", result.stderr)
        return result.returncode, json.loads(result.stdout)

    def test_stock_cli_reports_evidence_sources_and_existing_test_recommendation(self):
        stock = event("ORDER_CREATE", "STOCK_CONFLICT")
        self.write([stock])
        code, report = self.cli()
        self.assertEqual((0, "PASS", 1, 0), (code, report["status"], report["modelCalls"], report["modelCost"]))
        self.assertEqual(self.candidate, report["candidateCommit"])
        hypothesis, = report["hypotheses"]
        self.assertEqual(("STOCK_RESERVATION", stock), (hypothesis["stage"], hypothesis["event"]))
        self.assertIn("재고 잠금", hypothesis["nextCheck"])
        self.assertIn("롤백", hypothesis["nextCheck"])
        self.assertIn("docs/runbooks/inventory.md", hypothesis["sources"])
        self.assertIn("InventoryIntegrationTest", [item["class"] for item in report["recommendedTests"]])

    def test_uncertain_approval_only_sends_selected_events_to_model(self):
        pending = event("PAYMENT_CONFIRM", "PENDING", status=202)
        self.write([event("ORDER_CREATE", "STOCK_CONFLICT", identifier=OTHER), pending])
        model = Mock(side_effect=ANALYSIS.mock_model)
        report = ANALYSIS.run(self.args(), model=model)
        model.assert_called_once()
        payload = json.loads(model.call_args.args[0])
        self.assertEqual([pending], payload["timeline"])
        self.assertNotIn(OTHER, json.dumps(report))
        self.assertEqual("APPROVAL_UNCERTAIN", report["hypotheses"][0]["stage"])
        self.assertTrue(report["hypotheses"][0]["nextCheck"].startswith("저장된 거래 상태를 먼저 조회"))
        self.assertNotIn("commands", report)

    def test_recovery_cites_failure_from_same_run(self):
        failed = event("RECOVERY_CANCELLATION", "FAILED", identifier=RUN, status=None)
        deferred = event("RECOVERY_APPROVAL", "DEFERRED", identifier=RUN, status=None)
        self.write([deferred, failed, event("RECOVERY_APPROVAL", "FAILED", identifier=OTHER, status=None)])
        code, report = self.cli(run=True)
        self.assertEqual((0, RUN), (code, report["id"]))
        hypothesis, = report["hypotheses"]
        self.assertEqual(("RECOVERY_CANCELLATION", 1, failed),
                         (hypothesis["stage"], hypothesis["eventIndex"], hypothesis["event"]))
        self.assertIn("ReturnRequestIntegrationTest", [item["class"] for item in report["recommendedTests"]])

    def test_missing_unsupported_unverified_and_mismatched_candidates_block_before_call(self):
        model = Mock()
        scenarios = [
            ([], None, "MISSING_FAILURE_EVIDENCE"),
            ([event("PAYMENT_CANCEL", "FAILED")], None, "UNSUPPORTED_INCIDENT"),
            ([], "0" * 40, "CANDIDATE_MISMATCH"),
        ]
        for events, commit, reason in scenarios:
            self.write(events, commit)
            with self.subTest(reason=reason):
                report = ANALYSIS.run(self.args(), model=model)
                self.assertEqual(("BLOCKED", reason, 0), (report["status"], report["reason"], report["modelCalls"]))
                self.assertNotIn("hypotheses", report)
        self.write([event("ORDER_CREATE", "STOCK_CONFLICT")])
        with patch.object(ANALYSIS.evidence, "candidate_has_references", return_value=False):
            report = ANALYSIS.run(self.args(), model=model)
        self.assertEqual("CANDIDATE_REFERENCES_MISSING", report["reason"])
        model.assert_not_called()

    def test_disallowed_field_anywhere_fails_without_echoing(self):
        self.write([event("ORDER_CREATE", "STOCK_CONFLICT"),
                    {**event("ORDER_CREATE", "STOCK_CONFLICT", identifier=OTHER), "raw": "private-example"}])
        code, report = self.cli()
        self.assertEqual((1, "INVALID_EVENT_FIELDS", 0), (code, report["reason"], report["modelCalls"]))
        self.assertNotIn("private-example", json.dumps(report))

    def test_invalid_model_output_and_invented_references_are_rejected(self):
        self.write([event("PAYMENT_CANCEL", "FAILED"), event("ORDER_CREATE", "STOCK_CONFLICT")])
        for invalid in (b"not-json", ANALYSIS.evidence.InvalidInput("private-example"),
                        {"eventIndex": 0}, {"sources": ["../../private-example"]},
                        {"cause": "private-example"}, {"nextCheck": "private-example"},
                        {"eventIndex": True}, {"commands": ["private-example"]}):
            def model(payload):
                if isinstance(invalid, Exception):
                    raise invalid
                if isinstance(invalid, bytes):
                    return invalid
                reply = json.loads(ANALYSIS.mock_model(payload))
                reply["hypotheses"][0].update(invalid)
                return ANALYSIS.as_bytes(reply)

            report = ANALYSIS.run(self.args(), model=model)
            self.assertEqual(("FAIL", "INVALID_MODEL_OUTPUT", 1),
                             (report["status"], report["reason"], report["modelCalls"]))
            self.assertNotIn("private-example", json.dumps(report))
            self.assertNotIn("hypotheses", report)

    def test_raw_and_model_input_limits_prevent_a_call(self):
        self.source.write_bytes(b" " * (ANALYSIS.MAX_INPUT_BYTES + 1))
        code, report = self.cli()
        self.assertEqual((2, "INPUT_TOO_LARGE", 0), (code, report["reason"], report["modelCalls"]))

        stock = event("ORDER_CREATE", "STOCK_CONFLICT")
        self.write([stock] * 2)
        size = self.source.stat().st_size
        model = Mock()
        with patch.object(ANALYSIS, "MAX_INPUT_BYTES", size):
            report = ANALYSIS.run(self.args(), model=model)
        self.assertEqual(("BLOCKED", "MODEL_INPUT_TOO_LARGE", 0),
                         (report["status"], report["reason"], report["modelCalls"]))
        model.assert_not_called()

    def test_model_output_budget_cannot_trigger_retry(self):
        self.write([event("ORDER_CREATE", "STOCK_CONFLICT")])
        model = Mock(return_value=b" " * (ANALYSIS.MAX_OUTPUT_BYTES + 1))
        report = ANALYSIS.run(self.args(), model=model)
        self.assertEqual(("BLOCKED", "MODEL_OUTPUT_TOO_LARGE", 1),
                         (report["status"], report["reason"], report["modelCalls"]))
        model.assert_called_once()

    def test_deadline_interrupts_evidence_and_model_work(self):
        self.write([event("ORDER_CREATE", "STOCK_CONFLICT")])
        with patch.object(ANALYSIS.evidence, "head_commit", side_effect=lambda: time.sleep(3)):
            model = Mock()
            report = ANALYSIS.run(self.args(), model=model, time_limit=0.1)
        self.assertEqual(("BLOCKED", "TIME_LIMIT_EXCEEDED", 0),
                         (report["status"], report["reason"], report["modelCalls"]))
        model.assert_not_called()

        def slow_model(_payload):
            time.sleep(3)

        model = Mock(side_effect=slow_model)
        report = ANALYSIS.run(self.args(), model=model, time_limit=1)
        self.assertEqual(("BLOCKED", "TIME_LIMIT_EXCEEDED", 1),
                         (report["status"], report["reason"], report["modelCalls"]))
        self.assertLess(report["durationMs"], 2500)
        model.assert_called_once()


if __name__ == "__main__":
    unittest.main()
