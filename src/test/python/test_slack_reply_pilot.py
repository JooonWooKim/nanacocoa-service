"""Verify the synthetic Slack reply pilot without a real Slack destination."""

from concurrent.futures import ThreadPoolExecutor
import importlib
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import threading
import unittest
from unittest.mock import MagicMock, Mock, patch


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts/harness.d"))
PILOT = importlib.import_module("slack-reply-pilot")
TEAM, CHANNEL, THREAD = "T123", "C456", "1795737600.000001"
REPLY_TS = "1795737600.000002"
AUTH = (200, {"ok": True, "team_id": TEAM, "bot_id": "B789"})
REPLY = (200, {"ok": True, "channel": CHANNEL, "ts": REPLY_TS, "message": {"thread_ts": THREAD}})
ENV = {f"INCIDENT_SLACK_PILOT_{key}": value for key, value in (
    ("TEAM", TEAM), ("CHANNEL", CHANNEL), ("THREAD_TS", THREAD), ("BOT_TOKEN", "synthetic-bot-token"),
)}


class SlackReplyPilotTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.state = Path(directory.name) / "state.sqlite3"

    def run_pilot(self, *, request, env=ENV):
        return PILOT.run(env, state_path=self.state, request=request)

    def test_wire_sends_only_the_synthetic_summary_to_the_configured_thread(self):
        connection, response = MagicMock(), MagicMock()
        connection.getresponse.return_value.__enter__.return_value = response

        def send(_method, path, **_kwargs):
            code, data = AUTH if path == "/api/auth.test" else REPLY
            response.status = code
            response.read.return_value = json.dumps(data).encode()

        connection.request.side_effect = send
        with patch.object(PILOT.http.client, "HTTPSConnection", return_value=connection) as connect:
            outcome = self.run_pilot(request=PILOT.slack_request)
        self.assertEqual({"status": "PASS", "reason": "REPLY_CONFIRMED", "postAttempts": 1, "replyTs": REPLY_TS}, outcome)
        self.assertEqual([("slack.com",)] * 2, [call.args for call in connect.call_args_list])
        auth, post = connection.request.call_args_list
        self.assertEqual(("POST", "/api/auth.test"), auth.args)
        self.assertEqual({}, json.loads(auth.kwargs["body"]))
        self.assertEqual(("POST", "/api/chat.postMessage"), post.args)
        self.assertEqual("Bearer synthetic-bot-token", post.kwargs["headers"]["Authorization"])
        payload = json.loads(post.kwargs["body"])
        self.assertEqual((CHANNEL, THREAD, False, False),
                         (payload["channel"], payload["thread_ts"], payload["unfurl_links"], payload["unfurl_media"]))
        self.assertEqual({"channel", "thread_ts", "text", "unfurl_links", "unfurl_media"}, set(payload))
        self.assertIn("합성 테스트", payload["text"])
        self.assertIn("APPROVAL_UNCERTAIN", payload["text"])
        self.assertIn("기존 거래 상태를 조회", payload["text"])
        self.assertNotIn("155dd4fe", payload["text"])
        self.assertNotIn(b"synthetic-bot-token", self.state.read_bytes())

    def test_repeat_reuses_confirmed_receipt_without_posting(self):
        request = Mock(side_effect=[AUTH, REPLY, AUTH])
        self.run_pilot(request=request)
        outcome = self.run_pilot(request=request)
        self.assertEqual({"status": "PASS", "reason": "ALREADY_SENT", "postAttempts": 0, "replyTs": REPLY_TS}, outcome)
        self.assertEqual(["auth.test", "chat.postMessage", "auth.test"],
                         [call.args[1] for call in request.call_args_list])

    def test_missing_or_invalid_configuration_never_calls_slack(self):
        request = Mock()
        for config in ({}, {**ENV, "INCIDENT_SLACK_PILOT_CHANNEL": "D456"},
                       {**ENV, "INCIDENT_SLACK_PILOT_THREAD_TS": "invalid-thread"},
                       {**ENV, "INCIDENT_SLACK_PILOT_BOT_TOKEN": "bad\r\nheader"}):
            self.assertEqual("PILOT_CONFIGURATION_UNAVAILABLE", self.run_pilot(request=request, env=config)["reason"])
        request.assert_not_called()
        self.assertFalse(self.state.exists())

    def test_wrong_workspace_or_non_bot_token_is_blocked_before_reserving(self):
        for identity in ({"ok": True, "team_id": "T999", "bot_id": "B789"},
                         {"ok": True, "team_id": TEAM, "user_id": "U789"}):
            request = Mock(return_value=(200, identity))
            self.assertEqual("PILOT_IDENTITY_MISMATCH", self.run_pilot(request=request)["reason"])
            request.assert_called_once_with("synthetic-bot-token", "auth.test", {})
        self.assertFalse(self.state.exists())

    def test_unverified_candidate_never_calls_slack(self):
        request = Mock()
        with patch.object(PILOT.evidence, "candidate_has_references", return_value=False):
            self.assertEqual("SYNTHETIC_EVIDENCE_UNAVAILABLE", self.run_pilot(request=request)["reason"])
        request.assert_not_called()

    def test_rate_limit_and_confirmed_rejection_have_distinct_durable_reasons(self):
        for number, (remote, status, reason) in enumerate([
            ((429, {}), "BLOCKED", "SLACK_RATE_LIMITED"),
            ((200, {"ok": False, "error": "not_in_channel"}), "FAIL", "SLACK_POST_REJECTED"),
        ], 1):
            with self.subTest(reason=reason):
                self.state = self.state.with_name(f"case{number}.sqlite3")
                request = Mock(side_effect=[AUTH, remote, AUTH])
                outcome = self.run_pilot(request=request)
                self.assertEqual((status, reason, 1), (outcome["status"], outcome["reason"], outcome["postAttempts"]))
                # Fresh execution uses the stored verdict and does not post again.
                saved = self.run_pilot(request=request)
                self.assertEqual((reason, 0), (saved["reason"], saved["postAttempts"]))
                self.assertEqual(3, request.call_count)

    def test_ambiguous_responses_never_retry_even_on_a_new_invocation(self):
        for number, remote in enumerate([
            TimeoutError("private-slack-response"),
            (503, {}),
            (200, {"ok": False, "error": "internal_error"}),
            (200, {"ok": True, "channel": "C999", "ts": REPLY_TS}),
            (200, {"ok": True, "channel": CHANNEL, "ts": REPLY_TS,
                   "message": {"thread_ts": "1795737600.000009"}}),
        ], 1):
            self.state = self.state.with_name(f"case{number}.sqlite3")
            request = Mock(side_effect=[AUTH, remote, AUTH])
            first, repeated = self.run_pilot(request=request), self.run_pilot(request=request)
            self.assertEqual(("BLOCKED", "DELIVERY_UNCONFIRMED", 1),
                             (first["status"], first["reason"], first["postAttempts"]))
            self.assertEqual(("DELIVERY_UNCONFIRMED", 0), (repeated["reason"], repeated["postAttempts"]))
            self.assertNotIn("private-slack-response", json.dumps(first))
            self.assertEqual(3, request.call_count)

    def test_state_save_failure_keeps_reservation_after_post(self):
        request = Mock(side_effect=[AUTH, REPLY, AUTH])
        with patch.object(PILOT, "save_result", side_effect=sqlite3.OperationalError):
            outcome = self.run_pilot(request=request)
        self.assertEqual(("DELIVERY_UNCONFIRMED", 1), (outcome["reason"], outcome["postAttempts"]))
        self.assertEqual("DELIVERY_UNCONFIRMED", self.run_pilot(request=request)["reason"])
        self.assertEqual(3, request.call_count)

    def test_racing_execution_cannot_post_the_same_thread_twice(self):
        started, finish = threading.Event(), threading.Event()
        posts = []

        def request(_token, method, payload):
            if method == "auth.test":
                return AUTH
            posts.append(payload)
            started.set()
            self.assertTrue(finish.wait(timeout=4))
            return REPLY

        with ThreadPoolExecutor() as pool:
            first = pool.submit(self.run_pilot, request=request)
            try:
                self.assertTrue(started.wait(timeout=4))
                self.assertEqual("DELIVERY_UNCONFIRMED", self.run_pilot(request=request)["reason"])
            finally:
                finish.set()
            self.assertEqual("PASS", first.result(timeout=4)["status"])
        self.assertEqual(1, len(posts))

    def test_transport_does_not_follow_redirect_or_read_its_body(self):
        connection, reply = MagicMock(), MagicMock()
        connection.getresponse.return_value.__enter__.return_value = reply
        reply.status = 302
        with patch.object(PILOT.http.client, "HTTPSConnection", return_value=connection) as connect:
            outcome = self.run_pilot(request=PILOT.slack_request)
        self.assertEqual("SLACK_IDENTITY_UNAVAILABLE", outcome["reason"])
        connect.assert_called_once()
        reply.read.assert_not_called()


if __name__ == "__main__":
    unittest.main()
