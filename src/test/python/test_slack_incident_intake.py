"""Real loopback HTTP and durable worker tests with synthetic Slack events."""

from concurrent.futures import ThreadPoolExecutor
from contextlib import closing
import hashlib
import hmac
from http.client import HTTPConnection
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts"))

from slack_incident_intake import (
    DeliveryRejected, IntakeServer, MockSlack, RateLimited, ROUTE, Settings, work_one,
)
from slack_incident_queue import IncidentQueue, LEASE_SECONDS


REQUEST = "155dd4fe-3483-4fde-8930-fcb520b705f1"
OTHER = "04346762-897e-4755-9146-96b0884d67a2"
RUN = "ed58be6b-ccb1-4fde-b01f-1cf4efd2516c"
SETTINGS = Settings(b"synthetic-fixture-signing-secret", "A123", "T123", "C123", "U123", "U999")


def envelope(key="Ev123", kind="request", trace=REQUEST, **event_fields):
    return {
        "type": "event_callback", "api_app_id": SETTINGS.app, "team_id": SETTINGS.team,
        "event_id": key, "is_ext_shared_channel": False, "token": "synthetic-envelope-marker",
        "event": {"type": "app_mention", "channel": SETTINGS.channel, "user": SETTINGS.user,
                  "text": f"<@{SETTINGS.bot}> incident {kind} {trace}",
                  "ts": "1780000000.000001", **event_fields},
    }


def trace(event_id, operation, result, http=None):
    event = {"kind": "HTTP" if http else "RECOVERY", "id": event_id,
             "occurredAt": "2026-11-26T01:00:00Z", "operation": operation, "result": result,
             "durationMs": 2}
    if http:
        event["httpStatus"] = http
    return event


class SlackIncidentIntakeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.candidate = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.database = Path(self.directory.name) / "queue.db"
        self.evidence = Path(self.directory.name) / "incident.json"
        self.write_evidence([
            trace(REQUEST, "PAYMENT_CONFIRM", "PENDING", 202),
            trace(OTHER, "ORDER_CREATE", "STOCK_CONFLICT", 409),
            trace(RUN, "RECOVERY_CANCELLATION", "FAILED"),
        ])
        self.queue = IncidentQueue(self.database)
        self.server = IntakeServer(0, SETTINGS, self.queue)
        self.thread = threading.Thread(target=self.server.serve_forever, kwargs={"poll_interval": 0.02})
        self.thread.start()
        self.addCleanup(self.stop_server)

    def stop_server(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def write_evidence(self, events, candidate=None):
        self.evidence.write_text(json.dumps({"candidateCommit": candidate or self.candidate, "events": events}))

    def post(self, event=None, *, raw=None, delta=0, signature=None, path=ROUTE):
        body = raw if raw is not None else json.dumps(event).encode()
        timestamp = str(int(time.time()) + delta)
        signed = hmac.new(SETTINGS.signing_secret, f"v0:{timestamp}:".encode() + body, hashlib.sha256).hexdigest()
        headers = {"Content-Type": "application/json", "X-Slack-Request-Timestamp": timestamp,
                   "X-Slack-Signature": signature if signature is not None else "v0=" + signed}
        with closing(HTTPConnection("127.0.0.1", self.server.server_port, timeout=3)) as connection:
            connection.request("POST", path, body, headers)
            response = connection.getresponse()
            return response.status, json.loads(response.read())

    def rows(self, table):
        with closing(self.queue.connect()) as connection:
            return connection.execute(f"SELECT * FROM {table} ORDER BY rowid").fetchall()

    def finish_in_new_process(self):
        return subprocess.run(
            [sys.executable, "-B", str(ROOT / "scripts/slack_incident_intake.py"),
             "--database", str(self.database), "worker", "--evidence", str(self.evidence), "--once"],
            cwd=ROOT, capture_output=True, text=True, timeout=30, check=True,
        )

    def test_receipt_is_fast_and_worker_replies_to_original_thread(self):
        started = time.monotonic()
        self.assertEqual((200, {}), self.post(envelope(thread_ts="1770000000.000001")))
        self.assertLess(time.monotonic() - started, 3)
        self.assertEqual("PENDING", self.rows("jobs")[0]["state"])
        self.assertEqual([], self.rows("mock_replies"))
        self.finish_in_new_process()
        answer, = self.rows("mock_replies")
        self.assertEqual(("C123", "1770000000.000001", 0, 0), (
            answer["channel"], answer["thread_ts"], answer["unfurl_links"], answer["unfurl_media"],
        ))
        self.assertIn("해결 자료 생성 PASS", answer["text"])
        self.assertIn("APPROVAL_UNCERTAIN", answer["text"])
        self.assertIn("PaymentOperationsIntegrationTest", answer["text"])
        for marker in (REQUEST, OTHER, "STOCK_RESERVATION", "synthetic-envelope-marker", "httpStatus"):
            self.assertNotIn(marker, answer["text"])
        self.assertNotIn(b"synthetic-envelope-marker", self.database.read_bytes())
        self.assertEqual("SENT", self.rows("jobs")[0]["state"])

    def test_signature_shape_replays_timing_and_challenge(self):
        self.assertEqual(401, self.post(envelope(), signature="v0=" + "0" * 64)[0])
        self.assertEqual(401, self.post(envelope(), delta=-301)[0])
        self.assertEqual(401, self.post(envelope(), delta=301)[0])
        verify = {"type": "url_verification", "challenge": "synthetic-challenge"}
        self.assertEqual((200, {"challenge": "synthetic-challenge"}), self.post(verify))
        self.assertEqual(401, self.post(verify, signature="bad")[0])
        self.assertEqual((400, {}), self.post({**verify, "challenge": "unexpected input"}))
        self.assertEqual(400, self.post(raw=b'{"type":"url_verification","challenge":"one","challenge":"two"}')[0])
        self.assertEqual([], self.rows("jobs"))
        self.assertEqual([], self.rows("mock_replies"))

    def test_access_checks_and_unexpected_input_enqueue_nothing(self):
        unauthorized = [
            {**envelope(), "api_app_id": "A999"}, {**envelope(), "team_id": "T999"},
            {**envelope(), "is_ext_shared_channel": True}, envelope(channel="C999"),
            envelope(channel_type="im"), envelope(user="U888"), envelope(bot_id="B123"),
            envelope(is_ext_shared_channel=True),
        ]
        for event in unauthorized:
            with self.subTest(event=event):
                self.assertEqual((403, {}), self.post(event))
        self.assertEqual(400, self.post(envelope(text=f"<@U999> incident request {REQUEST} https://example.test"))[0])
        self.assertEqual(400, self.post(envelope(files=[]))[0])
        self.assertEqual(413, self.post(raw=b"x" * 16385)[0])
        self.assertEqual(404, self.post(envelope(), path="/unexpected")[0])
        self.assertEqual([], self.rows("jobs"))

    def test_concurrent_duplicate_receipt_and_worker_claim_keep_first_destination(self):
        with ThreadPoolExecutor(max_workers=6) as pool:
            self.assertEqual([200] * 8, list(pool.map(lambda _: self.post(envelope())[0], range(8))))
        changed = envelope(trace=OTHER, thread_ts="1860000000.000001")
        self.assertEqual(200, self.post(changed)[0])
        self.assertEqual(1, len(self.rows("jobs")))
        self.assertEqual(REQUEST, self.rows("jobs")[0]["incident_id"])
        with ThreadPoolExecutor(max_workers=3) as pool:
            list(pool.map(lambda _: self.finish_in_new_process(), range(3)))
        self.assertEqual(200, self.post(envelope())[0])
        self.finish_in_new_process()
        self.assertEqual(["1780000000.000001"], [row["thread_ts"] for row in self.rows("mock_replies")])
        self.assertEqual(1, self.rows("jobs")[0]["attempts"])

    def test_storage_failure_is_retryable(self):
        with self.queue.transaction():
            self.assertEqual((503, {}), self.post(envelope()))
        self.assertEqual([], self.rows("jobs"))
        self.assertEqual(200, self.post(envelope())[0])
        self.assertEqual(1, len(self.rows("jobs")))

    def test_recovery_and_stock_reference_distinct_incidents(self):
        self.assertEqual(200, self.post(envelope("EvRun", "run", RUN))[0])
        self.assertEqual(200, self.post(envelope("EvStock", trace=OTHER))[0])
        self.finish_in_new_process()
        answers = [row["text"] for row in self.rows("mock_replies")]
        self.assertIn("RECOVERY_CANCELLATION", answers[0])
        self.assertIn("ReturnRequestIntegrationTest", answers[0])
        self.assertIn("STOCK_RESERVATION", answers[1])
        self.assertIn("OrderServiceTest", answers[1])

    def test_missing_candidate_and_invalid_evidence_are_distinct_without_raw_values(self):
        variants = [
            ([], None, "BLOCKED", "실패·보류"),
            ([], "a" * 40, "BLOCKED", "후보 커밋"),
            ([{**trace(REQUEST, "ORDER_CREATE", "STOCK_CONFLICT", 409),
               "raw": "synthetic-private-marker"}], None, "FAIL", "형식"),
        ]
        for index, (events, candidate, status, hint) in enumerate(variants):
            self.write_evidence(events, candidate)
            self.assertEqual(200, self.post(envelope(f"Ev{index}"))[0])
            self.finish_in_new_process()
            last = self.rows("mock_replies")[-1]["text"]
            self.assertIn(status, last)
            self.assertIn(hint, last)
            self.assertNotIn("synthetic-private-marker", last)

    def test_restart_resumes_analysis_and_reuses_prepared_reply(self):
        self.assertEqual(200, self.post(envelope())[0])
        first = self.queue.take(100)
        self.assertEqual("ANALYZING", first["state"])
        now = lambda: 100 + LEASE_SECONDS
        queue_after_restart = IncidentQueue(self.database)
        self.assertTrue(work_one(queue_after_restart, self.evidence, MockSlack(queue_after_restart), now))
        self.queue.analyzed(first, "FAIL", "synthetic-stale-claim")
        self.evidence.unlink()
        self.finish_in_new_process()
        answer, = self.rows("mock_replies")
        self.assertIn("APPROVAL_UNCERTAIN", answer["text"])
        self.assertNotIn("synthetic-stale-claim", answer["text"])

    def test_rate_limit_retries_are_delayed_bounded_and_keep_prepared_reply(self):
        self.assertEqual(200, self.post(envelope())[0])
        adapter = MockSlack(self.queue)
        work_one(self.queue, self.evidence, adapter, lambda: 100)
        with patch.object(adapter, "send", side_effect=RateLimited(5)) as send:
            self.assertTrue(work_one(self.queue, self.evidence, adapter, lambda: 100))
            self.assertFalse(work_one(self.queue, self.evidence, adapter, lambda: 104))
            self.assertTrue(work_one(self.queue, self.evidence, adapter, lambda: 105))
            self.assertTrue(work_one(self.queue, self.evidence, adapter, lambda: 110))
            self.assertEqual(3, send.call_count)
            messages = [call.args[1] for call in send.call_args_list]
            self.assertEqual([messages[0]] * 3, messages)
        job, = self.rows("jobs")
        self.assertEqual(("FAILED", "RETRY_EXHAUSTED", "PASS"),
                         (job["state"], job["delivery_reason"], job["evidence_status"]))

    def test_lost_response_and_interruption_do_not_resend(self):
        adapter = MockSlack(self.queue)
        self.assertEqual(200, self.post(envelope())[0])
        work_one(self.queue, self.evidence, adapter, lambda: 100)
        send = adapter.send

        def post_then_lose_response(job_id, message):
            send(job_id, message)
            raise TimeoutError("synthetic-transport-marker")
        with patch.object(adapter, "send", side_effect=post_then_lose_response):
            work_one(self.queue, self.evidence, adapter, lambda: 100)
        self.assertEqual("BLOCKED", self.rows("jobs")[0]["state"])
        self.assertEqual(200, self.post(envelope("EvNext"))[0])
        work_one(self.queue, self.evidence, adapter, lambda: 100)
        interrupted = self.queue.take(100)
        self.assertEqual("SENDING", interrupted["state"])
        restarted = IncidentQueue(self.database)
        self.assertFalse(work_one(restarted, self.evidence, MockSlack(restarted), lambda: 100 + LEASE_SECONDS))
        self.assertEqual(["BLOCKED", "BLOCKED"], [row["state"] for row in self.rows("jobs")])
        self.assertEqual(1, len(self.rows("mock_replies")))
        self.assertNotIn(b"synthetic-transport-marker", self.database.read_bytes())

    def test_permanent_rejection_does_not_retry(self):
        self.assertEqual(200, self.post(envelope())[0])
        adapter = MockSlack(self.queue)
        work_one(self.queue, self.evidence, adapter, lambda: 100)
        with patch.object(adapter, "send", side_effect=DeliveryRejected()):
            self.assertTrue(work_one(self.queue, self.evidence, adapter, lambda: 100))
            self.assertFalse(work_one(self.queue, self.evidence, adapter, lambda: 110))
        self.assertEqual("DELIVERY_REJECTED", self.rows("jobs")[0]["delivery_reason"])

    def test_incomplete_configuration_fails_closed(self):
        config = {key: value for key, value in os.environ.items() if not key.startswith("INCIDENT_SLACK_")}
        result = subprocess.run([sys.executable, "-B", str(ROOT / "scripts/slack_incident_intake.py"),
                                 "--database", str(self.database), "serve", "--port", "0"],
                                capture_output=True, text=True, env=config, timeout=3)
        self.assertEqual(1, result.returncode)
        self.assertEqual("SLACK_MOCK_UNAVAILABLE", result.stderr.strip())
        self.assertEqual("", result.stdout)


if __name__ == "__main__":
    unittest.main()
