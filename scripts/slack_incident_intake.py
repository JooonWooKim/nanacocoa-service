"""Loopback Slack Events API receiver and mock delivery worker."""

import argparse
from dataclasses import dataclass
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import sys
import time

from slack_incident_queue import IncidentQueue, MAX_SEND_ATTEMPTS


ROOT = Path(__file__).resolve().parents[1]
ROUTE = "/integrations/slack/events"
MAX_BODY = 16 * 1024
SIGNED_AGE_SECONDS = 300
SLACK_ID = re.compile(r"[A-Z0-9]{2,32}")
EVENT_ID = re.compile(r"Ev[A-Za-z0-9]{1,62}")
SLACK_TS = re.compile(r"[0-9]{1,16}\.[0-9]{6}")
UUID_V4 = r"[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}"
COMMAND = re.compile(r"<@([A-Z0-9]{2,32})> incident (request|run) (" + UUID_V4 + r")")
SIG = re.compile(r"v0=[0-9a-f]{64}")
NEEDED_EVIDENCE = {
    "MISSING_FAILURE_EVIDENCE": "같은 번호의 실패·보류 또는 후속 처리 이벤트를 확인해 주세요.",
    "CANDIDATE_MISMATCH": "증거를 수집한 후보 커밋과 체크아웃이 일치하는지 확인해 주세요.",
    "CANDIDATE_REFERENCES_MISSING": "후보 커밋에 관련 코드·문서·검증 파일이 있는지 확인해 주세요.",
    "INPUT_UNAVAILABLE": "설정된 로컬 증거 파일을 확인해 주세요.",
}


@dataclass(frozen=True)
class Settings:
    signing_secret: bytes
    app: str
    team: str
    channel: str
    user: str
    bot: str

    @classmethod
    def from_env(cls, env):
        secret = env.get("INCIDENT_SLACK_SIGNING_SECRET", "")
        ids = [env.get("INCIDENT_SLACK_" + key, "") for key in ("APP", "TEAM", "CHANNEL", "USER", "BOT")]
        if (env.get("INCIDENT_SLACK_MODE") != "mock" or not 16 <= len(secret) <= 256
                or not secret.isascii() or any(not SLACK_ID.fullmatch(value) for value in ids)
                or not ids[2].startswith(("C", "G")) or ids[3] == ids[4]):
            raise ValueError("INCOMPLETE_MOCK_CONFIGURATION")
        return cls(secret.encode(), *ids)


def unique_fields(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("DUPLICATE_FIELD")
        result[key] = value
    return result


def receive(body, timestamp, signature, settings, queue, now):
    if not (re.fullmatch(r"[0-9]{10}", timestamp) and SIG.fullmatch(signature)):
        return 401, {}
    if abs(now - int(timestamp)) > SIGNED_AGE_SECONDS:
        return 401, {}
    base = b"v0:" + timestamp.encode() + b":" + body
    expected = "v0=" + hmac.new(settings.signing_secret, base, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, signature):
        return 401, {}

    try:
        payload = json.loads(body.decode("utf-8"), object_pairs_hook=unique_fields)
    except (ValueError, RecursionError):
        return 400, {}
    if not isinstance(payload, dict):
        return 400, {}
    if payload.get("type") == "url_verification":
        challenge = payload.get("challenge")
        if isinstance(challenge, str) and re.fullmatch(r"[A-Za-z0-9_-]{1,128}", challenge):
            return 200, {"challenge": challenge}
        return 400, {}

    event = payload.get("event")
    if payload.get("type") != "event_callback" or not isinstance(event, dict):
        return 400, {}
    if (payload.get("api_app_id") != settings.app or payload.get("team_id") != settings.team
            or payload.get("is_ext_shared_channel") is not False):
        return 403, {}
    if event.get("type") != "app_mention":
        return 200, {}
    if (event.get("channel") != settings.channel or event.get("user") != settings.user
            or event.get("channel_type") in ("im", "mpim")
            or ("is_ext_shared_channel" in event and event["is_ext_shared_channel"] is not False)
            or event.get("bot_id") is not None or event.get("subtype") is not None):
        return 403, {}
    if any(field in event for field in ("files", "attachments")):
        return 400, {}

    event_id = payload.get("event_id")
    text = event.get("text")
    thread = event.get("thread_ts", event.get("ts"))
    command = COMMAND.fullmatch(text) if isinstance(text, str) else None
    if (command is None or command[1] != settings.bot
            or not isinstance(event_id, str) or not EVENT_ID.fullmatch(event_id)
            or not isinstance(thread, str) or not SLACK_TS.fullmatch(thread)):
        return 400, {}

    try:
        queue.enqueue((settings.team, event_id, settings.channel, thread, command[2], command[3]))
    except sqlite3.Error:
        return 503, {}
    return 200, {}


class IntakeHandler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(2)

    def do_POST(self):
        if self.path != ROUTE:
            self.respond(404, {})
            return
        lengths = self.headers.get_all("Content-Length", [])
        if (self.headers.get("Transfer-Encoding") is not None or len(lengths) != 1
                or not re.fullmatch(r"[0-9]{1,8}", lengths[0])
                or self.headers.get_content_type() != "application/json"):
            self.respond(400, {})
            return
        length = int(lengths[0])
        if length > MAX_BODY:
            self.respond(413, {})
            return
        timestamp = self.headers.get_all("X-Slack-Request-Timestamp", [])
        signature = self.headers.get_all("X-Slack-Signature", [])
        if len(timestamp) != 1 or len(signature) != 1:
            self.respond(401, {})
            return

        deadline = time.monotonic() + 1.5
        body = bytearray()
        try:
            while len(body) < length:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError()
                self.connection.settimeout(remaining)
                part = self.rfile.read1(length - len(body))
                if not part:
                    break
                body.extend(part)
        except TimeoutError:
            self.respond(408, {})
            return
        if len(body) != length:
            self.respond(400, {})
            return
        self.connection.settimeout(1)
        code, response = receive(bytes(body), timestamp[0], signature[0], self.server.settings,
                                 self.server.queue, time.time())
        self.respond(code, response)

    def respond(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *_args):
        # Default request logs include the untrusted path and headers from malformed requests.
        return

    def send_error(self, code, message=None, explain=None):
        self.close_connection = True
        self.respond(code, {})


class IntakeServer(ThreadingHTTPServer):
    def __init__(self, port, settings, queue):
        self.settings = settings
        self.queue = queue
        super().__init__(("127.0.0.1", port), IntakeHandler)

    def handle_error(self, _request, _client_address):
        # Worker state is persisted separately. Never print raw HTTP exception data.
        return


def build_evidence(path, kind, incident_id):
    option = "--request-id" if kind == "request" else "--run-id"
    try:
        result = subprocess.run(
            [sys.executable, "-B", str(ROOT / "scripts/harness.d/incident-evidence.py"),
             "--input", str(path), option, incident_id],
            cwd=ROOT, capture_output=True, text=True, timeout=20,
        )
        report = json.loads(result.stdout)
        if result.returncode == {"PASS": 0, "FAIL": 1, "BLOCKED": 2}.get(report["status"]):
            return report
    except (OSError, subprocess.TimeoutExpired, ValueError, KeyError, TypeError):
        pass
    return {"status": "FAIL", "reason": "EVIDENCE_COMMAND_FAILED"}


def render_reply(job_id, report):
    status = report["status"]
    lines = [f"장애 처리 작업 {job_id}", f"해결 자료 생성 {status}"]
    if status == "PASS":
        lines.extend(f'{item["stage"]} · {item["procedure"]}' for item in report["findings"])
        tests = ", ".join(item["class"] for item in report["recommendedTests"])
        lines.extend((f"권장 테스트 {tests}", "실제 테스트 결과는 하네스에서 별도로 확인해 주세요."))
    else:
        lines.append(NEEDED_EVIDENCE.get(report.get("reason"), "로컬 증거 형식과 자료 생성 설정을 확인해 주세요."))
    return "\n".join(lines)


class RateLimited(Exception):
    """Mock transport outcome that guarantees the message was rejected."""

    def __init__(self, retry_after):
        self.retry_after = retry_after


class DeliveryRejected(Exception):
    """Mock transport outcome that guarantees permanent rejection."""


class MockSlack:
    def __init__(self, queue):
        self.queue = queue

    def send(self, job_id, message):
        self.queue.record_mock_reply(job_id, message)


def work_one(queue, evidence, adapter, clock=time.time):
    job = queue.take(clock())
    if job is None:
        return False
    if job["state"] == "ANALYZING":
        report = build_evidence(evidence, job["kind"], job["incident_id"])
        queue.analyzed(job, report["status"], render_reply(job["id"], report))
        return True

    message = {"channel": job["channel"], "thread_ts": job["thread"], "text": job["reply"],
               "unfurl_links": False, "unfurl_media": False}
    try:
        adapter.send(job["id"], message)
    except RateLimited as error:
        if job["attempts"] < MAX_SEND_ATTEMPTS and 1 <= error.retry_after <= 60:
            queue.delivery(job, "READY", "RATE_LIMITED", clock() + error.retry_after)
        else:
            queue.delivery(job, "FAILED", "RETRY_EXHAUSTED")
    except DeliveryRejected:
        queue.delivery(job, "FAILED", "DELIVERY_REJECTED")
    except Exception:
        queue.delivery(job, "BLOCKED", "DELIVERY_UNKNOWN")
    else:
        queue.delivery(job, "SENT", None)
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", type=Path, required=True)
    roles = parser.add_subparsers(dest="role", required=True)
    receiver = roles.add_parser("serve", help="Start the loopback HTTP receiver")
    receiver.add_argument("--port", type=int, default=18090)
    worker = roles.add_parser("worker", help="Process pending jobs with the mock Slack adapter")
    worker.add_argument("--evidence", type=Path, required=True)
    worker.add_argument("--once", action="store_true", help="Drain currently available jobs and exit")
    args = parser.parse_args()
    os.umask(0o077)
    try:
        if args.role == "serve":
            settings = Settings.from_env(os.environ)
            queue = IncidentQueue(args.database)
            with IntakeServer(args.port, settings, queue) as server:
                print(f"SLACK_MOCK_READY 127.0.0.1:{server.server_port}", flush=True)
                server.serve_forever()
        else:
            queue = IncidentQueue(args.database)
            adapter = MockSlack(queue)
            while True:
                if not work_one(queue, args.evidence, adapter):
                    if args.once:
                        break
                    time.sleep(0.25)
    except KeyboardInterrupt:
        return 0
    except (OSError, ValueError, OverflowError, sqlite3.Error):
        print("SLACK_MOCK_UNAVAILABLE", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
