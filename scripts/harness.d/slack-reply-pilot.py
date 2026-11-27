"""Send one synthetic incident reply to an approved Slack test thread."""

from contextlib import closing
import http.client
import importlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

from slack_incident_intake import SLACK_ID, SLACK_TS, render_reply, unique_fields


evidence = importlib.import_module("incident-evidence")
STATE_PATH = ROOT / ".slack-reply-pilot/receipts.sqlite3"
MAX_RESPONSE_BYTES = 64 * 1024
EXIT_CODES = {"PASS": 0, "FAIL": 1, "BLOCKED": 2}
AUTH_ERRORS = {"invalid_auth", "not_authed", "token_revoked", "account_inactive", "missing_scope"}
POST_REJECTIONS = AUTH_ERRORS | {"channel_not_found", "not_in_channel", "is_archived", "thread_not_found"}
RESPONSE_ERRORS = (OSError, ValueError, RecursionError, http.client.HTTPException)


def result(status, reason, attempts=0, **fields):
    return {"status": status, "reason": reason, "postAttempts": attempts, **fields}


def slack_request(token, method, payload):
    # Direct HTTPS to Slack keeps the token off redirects and avoids automatic retries.
    with closing(http.client.HTTPSConnection("slack.com", timeout=5)) as connection:
        connection.request("POST", f"/api/{method}", body=json.dumps(payload).encode("utf-8"), headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json; charset=utf-8",
        })
        with connection.getresponse() as response:
            if response.status != 200:
                return response.status, {}
            raw = response.read(MAX_RESPONSE_BYTES + 1)
    if len(raw) > MAX_RESPONSE_BYTES:
        raise ValueError("RESPONSE_TOO_LARGE")
    data = json.loads(raw.decode("utf-8"), object_pairs_hook=unique_fields)
    if not isinstance(data, dict):
        raise ValueError("INVALID_RESPONSE")
    return 200, data


def build_reply():
    candidate = evidence.head_commit()
    if not evidence.matches(evidence.SHA, candidate):
        return None
    event = {
        "kind": "HTTP", "id": "155dd4fe-3483-4fde-8930-fcb520b705f1",
        "occurredAt": "2026-11-27T00:00:00Z", "operation": "PAYMENT_CONFIRM",
        "result": "PENDING", "httpStatus": 202, "durationMs": 10,
    }
    evidence.validate_event(event)
    report = evidence.report_for(candidate, [event], [], "HTTP", event["id"])
    if report["status"] != "PASS":
        return None
    references = report["sources"] + [evidence.TEST_PATHS[row["class"]] for row in report["recommendedTests"]]
    if not evidence.candidate_has_references(candidate, references):
        return None
    return render_reply("합성 테스트 · 결제 승인 미확정", report)


def reserve(path, destination):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    with closing(sqlite3.connect(path, timeout=2)) as db, db:
        db.execute("BEGIN IMMEDIATE")
        db.execute("""
            CREATE TABLE IF NOT EXISTS receipts (
                team TEXT NOT NULL,
                channel TEXT NOT NULL,
                thread TEXT NOT NULL,
                status TEXT NOT NULL,
                reason TEXT NOT NULL,
                reply_ts TEXT,
                PRIMARY KEY (team, channel, thread)
            )
        """)
        saved = db.execute("""
            SELECT status, reason, reply_ts FROM receipts WHERE team = ? AND channel = ? AND thread = ?
        """, destination).fetchone()
        if saved is not None:
            status, reason, reply_ts = saved
            if status == "PASS":
                return result("PASS", "ALREADY_SENT", replyTs=reply_ts)
            if status == "SENDING":
                return result("BLOCKED", "DELIVERY_UNCONFIRMED")
            return result(status, reason)
        db.execute("""
            INSERT INTO receipts VALUES (?, ?, ?, 'SENDING', 'DELIVERY_UNCONFIRMED', NULL)
        """, destination)
    return None


def save_result(path, destination, outcome):
    with closing(sqlite3.connect(path, timeout=2)) as db, db:
        cursor = db.execute("""
            UPDATE receipts SET status = ?, reason = ?, reply_ts = ?
            WHERE team = ? AND channel = ? AND thread = ? AND status = 'SENDING'
        """, (outcome["status"], outcome["reason"], outcome.get("replyTs"), *destination))
        if cursor.rowcount != 1:
            raise sqlite3.OperationalError("RESERVATION_MISSING")


def send_reply(request, token, channel, thread, reply):
    try:
        http_status, response = request(token, "chat.postMessage", {
            "channel": channel, "thread_ts": thread, "text": reply,
            "unfurl_links": False, "unfurl_media": False,
        })
    except RESPONSE_ERRORS:
        return result("BLOCKED", "DELIVERY_UNCONFIRMED", 1)
    if http_status == 429 or (response.get("ok") is False and response.get("error") == "ratelimited"):
        return result("BLOCKED", "SLACK_RATE_LIMITED", 1)
    error = response.get("error")
    if http_status in (401, 403) or (
        response.get("ok") is False and isinstance(error, str) and error in POST_REJECTIONS
    ):
        return result("FAIL", "SLACK_POST_REJECTED", 1)

    timestamp = response.get("ts")
    message = response.get("message")
    if (http_status == 200 and response.get("ok") is True and response.get("channel") == channel
            and isinstance(timestamp, str) and SLACK_TS.fullmatch(timestamp) and timestamp != thread
            and (not isinstance(message, dict) or message.get("thread_ts", thread) == thread)):
        return result("PASS", "REPLY_CONFIRMED", 1, replyTs=timestamp)
    return result("BLOCKED", "DELIVERY_UNCONFIRMED", 1)


def run(env, *, state_path=STATE_PATH, request=slack_request):
    token, team, channel, thread = (env.get("INCIDENT_SLACK_PILOT_" + key, "")
                                   for key in ("BOT_TOKEN", "TEAM", "CHANNEL", "THREAD_TS"))
    if (not token or not all("!" <= character <= "~" for character in token)
            or not team.startswith("T") or not SLACK_ID.fullmatch(team)
            or not channel.startswith(("C", "G")) or not SLACK_ID.fullmatch(channel)
            or not SLACK_TS.fullmatch(thread)):
        return result("BLOCKED", "PILOT_CONFIGURATION_UNAVAILABLE")
    try:
        reply = build_reply()
    except (OSError, subprocess.TimeoutExpired):
        return result("BLOCKED", "SYNTHETIC_EVIDENCE_UNAVAILABLE")
    if reply is None:
        return result("BLOCKED", "SYNTHETIC_EVIDENCE_UNAVAILABLE")

    try:
        code, identity = request(token, "auth.test", {})
    except RESPONSE_ERRORS:
        return result("BLOCKED", "SLACK_IDENTITY_UNAVAILABLE")
    if code == 429 or (identity.get("ok") is False and identity.get("error") == "ratelimited"):
        return result("BLOCKED", "SLACK_RATE_LIMITED")
    bot = identity.get("bot_id")
    if code != 200 or identity.get("ok") is not True:
        return result("BLOCKED", "SLACK_IDENTITY_UNAVAILABLE")
    if identity.get("team_id") != team or not isinstance(bot, str) or not bot.startswith("B") or not SLACK_ID.fullmatch(bot):
        return result("BLOCKED", "PILOT_IDENTITY_MISMATCH")

    destination = (team, channel, thread)
    try:
        saved = reserve(state_path, destination)
    except (OSError, sqlite3.Error):
        return result("BLOCKED", "PILOT_STATE_UNAVAILABLE")
    if saved is not None:
        return saved
    outcome = send_reply(request, token, channel, thread, reply)
    try:
        save_result(state_path, destination, outcome)
    except (OSError, sqlite3.Error):
        return result("BLOCKED", "DELIVERY_UNCONFIRMED", 1)
    return outcome


def main(args):
    os.umask(0o077)
    outcome = result("FAIL", "INVALID_ARGUMENTS") if args else run(os.environ)
    print(json.dumps(outcome, ensure_ascii=False))
    return EXIT_CODES[outcome["status"]]


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
