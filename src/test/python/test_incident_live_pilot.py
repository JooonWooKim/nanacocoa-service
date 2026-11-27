"""Synthetic pilot regression without external connections or real credentials."""

from contextlib import redirect_stdout
import importlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import MagicMock, Mock, patch


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts/harness.d"))
ANALYSIS = importlib.import_module("incident-analyze")
PILOT = ANALYSIS.incident_pilot
FIXTURE_KEY = "synthetic-fixture-key"


def selected(payload):
    return {"decision": "SELECTED", "candidateId": payload["candidateIds"][0],
            "evidenceNumber": payload["evidence"]["number"]}


def response_for(selection):
    return {
        "status": "completed",
        "output": [{"type": "message", "role": "assistant",
                    "content": [{"type": "output_text", "text": json.dumps(selection)}]}],
        "usage": {"input_tokens": 42, "output_tokens": 12},
    }


def wire(responder):
    connection, response = MagicMock(), MagicMock()
    connection.getresponse.return_value.__enter__.return_value = response

    def send(_method, _path, *, body, headers):
        request = json.loads(body)
        code, returned = responder(json.loads(request["input"]))
        response.status = code
        response.read.return_value = json.dumps(returned).encode()

    connection.request.side_effect = send
    return connection, response


class IncidentLivePilotTest(unittest.TestCase):
    def test_three_cases_use_fixed_safe_payload_and_preserve_local_references(self):
        connection, response = wire(lambda payload: (200, response_for(selected(payload))))
        with patch.object(PILOT.http.client, "HTTPSConnection", return_value=connection) as connect:
            report = ANALYSIS.run_live_pilot(FIXTURE_KEY)
        self.assertEqual(("PASS", "PILOT_PREPARED", 3),
                         (report["status"], report["reason"], report["modelCalls"]))
        self.assertEqual(["ORDER_CREATE", "PAYMENT_CONFIRM", "RECOVERY_CANCELLATION"],
                         [case["case"] for case in report["cases"]])
        self.assertEqual(3, connect.call_count)
        self.assertEqual(3, connection.close.call_count)
        self.assertEqual(3, response.read.call_count)
        for number, (call, case) in enumerate(zip(connection.request.call_args_list, report["cases"]), start=1):
            self.assertEqual(("POST", "/v1/responses"), call.args)
            self.assertEqual("Bearer " + FIXTURE_KEY, call.kwargs["headers"]["Authorization"])
            request = json.loads(call.kwargs["body"])
            payload = json.loads(request["input"])
            self.assertEqual({"stage", "evidence", "candidateIds"}, set(payload))
            self.assertEqual({"number", "operation", "result"}, set(payload["evidence"]))
            self.assertEqual(number, payload["evidence"]["number"])
            self.assertEqual("gpt-5.6-luna", request["model"])
            self.assertIs(request["store"], False)
            self.assertNotIn("tools", request)
            self.assertEqual(256, request["max_output_tokens"])
            self.assertIs(request["text"]["format"]["strict"], True)
            self.assertNotIn(case["hypothesis"]["event"]["id"], request["input"])
            self.assertNotIn(report["candidateCommit"], request["input"])
            self.assertNotIn("docs/", request["input"])
            self.assertEqual({"inputTokens": 42, "outputTokens": 12}, case["usage"])
            self.assertTrue(case["hypothesis"]["sources"])
        self.assertNotIn(FIXTURE_KEY, json.dumps(report))
        self.assertTrue(report["cases"][1]["hypothesis"]["nextCheck"].startswith("저장된 거래 상태를 먼저 조회"))
        self.assertEqual([("api.openai.com",)] * 3, [call.args for call in connect.call_args_list])

    def test_missing_pilot_key_and_mixed_arguments_do_not_call(self):
        environment = dict(os.environ, OPENAI_API_KEY=FIXTURE_KEY)
        environment.pop(PILOT.API_KEY_ENV, None)
        for extras, expected in (([], (2, "PILOT_KEY_UNAVAILABLE", 0)),
                                 (["--input", "private-example"], (1, "INVALID_ARGUMENTS", 0))):
            result = subprocess.run([str(ROOT / "scripts/harness"), "incident-analyze", "--live-pilot", *extras],
                                    cwd=ROOT, env=environment, capture_output=True, text=True, timeout=3)
            report = json.loads(result.stdout)
            self.assertEqual(expected, (result.returncode, report["reason"], report["modelCalls"]))
            self.assertEqual("", result.stderr)
        with patch.object(ANALYSIS.evidence, "head_commit") as head:
            report = ANALYSIS.run_live_pilot("invalid\r\nvalue")
        self.assertEqual("PILOT_KEY_UNAVAILABLE", report["reason"])
        head.assert_not_called()

    def test_default_mock_ignores_configured_pilot_key(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "events.json"
            candidate = ANALYSIS.evidence.head_commit()
            identifier = "155dd4fe-3483-4fde-8930-fcb520b705f1"
            manifest.write_text(json.dumps({"candidateCommit": candidate, "events": [{
                "kind": "HTTP", "id": identifier, "occurredAt": "2026-11-27T00:00:00Z",
                "operation": "ORDER_CREATE", "result": "STOCK_CONFLICT", "durationMs": 1, "httpStatus": 409,
            }]}))
            output = io.StringIO()
            with (patch.dict(os.environ, {PILOT.API_KEY_ENV: FIXTURE_KEY}),
                  patch.object(PILOT.http.client, "HTTPSConnection") as connect, redirect_stdout(output)):
                status = ANALYSIS.main(["--input", str(manifest), "--request-id", identifier])
        connect.assert_not_called()
        report = json.loads(output.getvalue())
        self.assertEqual((0, "PASS", 0), (status, report["status"], report["modelCost"]))
        self.assertNotIn("mode", report)

    def test_unverified_candidate_never_calls_provider(self):
        transport = Mock()
        with patch.object(ANALYSIS.evidence, "head_commit", return_value=None):
            report = ANALYSIS.run_live_pilot(FIXTURE_KEY, transport=transport)
        self.assertEqual(("BLOCKED", "CANDIDATE_UNAVAILABLE", 0),
                         (report["status"], report["reason"], report["modelCalls"]))
        with patch.object(ANALYSIS.evidence, "candidate_has_references", return_value=False):
            report = ANALYSIS.run_live_pilot(FIXTURE_KEY, transport=transport)
        self.assertEqual("CANDIDATE_REFERENCES_MISSING", report["reason"])
        transport.assert_not_called()

    def test_http_failures_stop_and_do_not_read_raw_error_or_follow_redirect(self):
        cases = [(302, "FAIL", "MODEL_HTTP_ERROR"), (401, "BLOCKED", "MODEL_ACCESS_UNAVAILABLE"),
                 (403, "BLOCKED", "MODEL_ACCESS_UNAVAILABLE"), (408, "BLOCKED", "TIME_LIMIT_EXCEEDED"),
                 (429, "BLOCKED", "MODEL_RATE_LIMITED"), (503, "BLOCKED", "MODEL_UNAVAILABLE")]
        for status, result, reason in cases:
            with self.subTest(status=status):
                connection, response = wire(lambda _: (status, {"error": "private-example"}))
                with patch.object(PILOT.http.client, "HTTPSConnection", return_value=connection):
                    report = ANALYSIS.run_live_pilot(FIXTURE_KEY)
                self.assertEqual((result, reason, 1), (report["status"], report["reason"], report["modelCalls"]))
                connection.request.assert_called_once()
                response.read.assert_not_called()
                self.assertNotIn("private-example", json.dumps(report))

    def test_rate_limit_after_success_preserves_success_and_ends_batch(self):
        def responder(payload):
            if payload["evidence"]["number"] == 1:
                return 200, response_for(selected(payload))
            return 429, {"error": "private-example"}

        connection, _ = wire(responder)
        with patch.object(PILOT.http.client, "HTTPSConnection", return_value=connection):
            report = ANALYSIS.run_live_pilot(FIXTURE_KEY)
        self.assertEqual(("BLOCKED", "MODEL_RATE_LIMITED", 2),
                         (report["status"], report["reason"], report["modelCalls"]))
        self.assertEqual(["PASS", "BLOCKED"], [case["status"] for case in report["cases"]])

    def test_model_refusal_incomplete_and_insufficient_evidence_block(self):
        refusal = {"status": "completed", "output": [{"type": "message", "role": "assistant",
                                                       "content": [{"type": "refusal", "refusal": "private-example"}]}]}
        for response, reason in (
            (refusal, "MODEL_REFUSED"),
            ({"status": "incomplete", "incomplete_details": {"reason": "max_output_tokens"}}, "MODEL_INCOMPLETE"),
            (response_for({"decision": "INSUFFICIENT_EVIDENCE", "candidateId": None, "evidenceNumber": None}),
             "MODEL_INSUFFICIENT_EVIDENCE"),
        ):
            transport = Mock(return_value=response)
            report = ANALYSIS.run_live_pilot(FIXTURE_KEY, transport=transport)
            self.assertEqual(("BLOCKED", reason, 1), (report["status"], report["reason"], report["modelCalls"]))
            self.assertNotIn("private-example", json.dumps(report))
            transport.assert_called_once()

    def test_model_cannot_supply_other_case_references_arbitrary_text_or_invalid_type(self):
        for change in ({"candidateId": "APPROVAL_CONFIRMATION_PENDING"}, {"evidenceNumber": 2},
                       {"evidenceNumber": True}, {"cause": "private-example"},
                       {"candidateId": "private-example"},
                       {"decision": "INSUFFICIENT_EVIDENCE"}):
            def transport(_key, payload):
                return response_for({**selected(payload), **change})

            report = ANALYSIS.run_live_pilot(FIXTURE_KEY, transport=transport)
            self.assertEqual(("FAIL", "INVALID_MODEL_OUTPUT", 1),
                             (report["status"], report["reason"], report["modelCalls"]))
            self.assertNotIn("hypothesis", report["cases"][0])
            self.assertNotIn("private-example", json.dumps(report))

    def test_transport_and_case_deadline_stop_without_retry(self):
        connection = MagicMock()
        connection.request.side_effect = TimeoutError("private-example")
        with patch.object(PILOT.http.client, "HTTPSConnection", return_value=connection):
            report = ANALYSIS.run_live_pilot(FIXTURE_KEY)
        self.assertEqual(("BLOCKED", "TIME_LIMIT_EXCEEDED", 1),
                         (report["status"], report["reason"], report["modelCalls"]))
        self.assertNotIn("private-example", json.dumps(report))
        connection.request.assert_called_once()

        transport = Mock(side_effect=lambda *_: time.sleep(3))
        report = ANALYSIS.run_live_pilot(FIXTURE_KEY, transport=transport, time_limit=0.5)
        self.assertEqual(("BLOCKED", "TIME_LIMIT_EXCEEDED", 1),
                         (report["status"], report["reason"], report["modelCalls"]))
        self.assertLess(report["durationMs"], 2500)
        transport.assert_called_once()

    def test_malformed_and_oversized_response_are_fixed_failures(self):
        for raw in (b'{"status":"completed","status":"incomplete"}', b" " * (PILOT.MAX_RESPONSE_BYTES + 1)):
            connection, reply = wire(lambda _: (200, {}))
            reply.read.side_effect = lambda *_: raw
            with patch.object(PILOT.http.client, "HTTPSConnection", return_value=connection):
                report = ANALYSIS.run_live_pilot(FIXTURE_KEY)
            self.assertEqual(("FAIL", "INVALID_MODEL_OUTPUT", 1),
                             (report["status"], report["reason"], report["modelCalls"]))
            connection.request.assert_called_once()

    def test_usage_is_only_validated_provider_counts(self):
        self.assertIsNone(PILOT.usage_tokens({"usage": {"input_tokens": True, "output_tokens": -1}}))
        self.assertEqual({"inputTokens": 9}, PILOT.usage_tokens({"usage": {
            "input_tokens": 9, "output_tokens": "private-example", "raw": "private-example",
        }}))
        self.assertIsNone(PILOT.usage_tokens({}))


if __name__ == "__main__":
    unittest.main()
