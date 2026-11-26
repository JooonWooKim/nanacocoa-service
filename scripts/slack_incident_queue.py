"""Durable queue for the local Slack incident intake."""

from contextlib import contextmanager, closing
import sqlite3
import uuid


LEASE_SECONDS = 60
MAX_SEND_ATTEMPTS = 3


class IncidentQueue:
    def __init__(self, path):
        self.path = path
        with closing(self.connect()) as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS jobs (
                    id TEXT PRIMARY KEY,
                    team TEXT NOT NULL,
                    event_id TEXT NOT NULL,
                    channel TEXT NOT NULL,
                    thread TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    incident_id TEXT NOT NULL,
                    state TEXT NOT NULL DEFAULT 'PENDING',
                    available_at REAL NOT NULL DEFAULT 0,
                    lease_until REAL,
                    claim TEXT,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    evidence_status TEXT,
                    reply TEXT,
                    delivery_reason TEXT,
                    UNIQUE (team, event_id)
                );
                CREATE INDEX IF NOT EXISTS jobs_by_state ON jobs(state, available_at);
                CREATE TABLE IF NOT EXISTS mock_replies (
                    job_id TEXT PRIMARY KEY,
                    channel TEXT NOT NULL,
                    thread_ts TEXT NOT NULL,
                    text TEXT NOT NULL,
                    unfurl_links INTEGER NOT NULL,
                    unfurl_media INTEGER NOT NULL
                );
            """)

    def connect(self):
        db = sqlite3.connect(self.path, timeout=0.5)
        db.row_factory = sqlite3.Row
        return db

    @contextmanager
    def transaction(self):
        with closing(self.connect()) as db, db:
            db.execute("BEGIN IMMEDIATE")
            yield db

    def enqueue(self, event):
        with self.transaction() as db:
            db.execute("""
                INSERT INTO jobs (id, team, event_id, channel, thread, kind, incident_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (team, event_id) DO NOTHING
            """, (str(uuid.uuid4()), *event))

    def take(self, now):
        with self.transaction() as db:
            db.execute("""
                UPDATE jobs SET state = 'PENDING', claim = NULL, lease_until = NULL
                WHERE state = 'ANALYZING' AND lease_until <= ?
            """, (now,))
            db.execute("""
                UPDATE jobs SET state = 'BLOCKED', delivery_reason = 'DELIVERY_UNKNOWN',
                                claim = NULL, lease_until = NULL
                WHERE state = 'SENDING' AND lease_until <= ?
            """, (now,))
            job = db.execute("""
                SELECT * FROM jobs WHERE state IN ('PENDING', 'READY') AND available_at <= ?
                ORDER BY available_at, rowid LIMIT 1
            """, (now,)).fetchone()
            if job is None:
                return None
            sending = job["state"] == "READY"
            state = "SENDING" if sending else "ANALYZING"
            attempts = job["attempts"] + int(sending)
            token = str(uuid.uuid4())
            db.execute("""
                UPDATE jobs SET state = ?, lease_until = ?, claim = ?, attempts = ? WHERE id = ?
            """, (state, now + LEASE_SECONDS, token, attempts, job["id"]))
            return dict(job) | {"state": state, "claim": token, "attempts": attempts}

    def analyzed(self, job, status, reply):
        with self.transaction() as db:
            db.execute("""
                UPDATE jobs SET state = 'READY', evidence_status = ?, reply = ?,
                                claim = NULL, lease_until = NULL
                WHERE id = ? AND state = 'ANALYZING' AND claim = ?
            """, (status, reply, job["id"], job["claim"]))

    def delivery(self, job, state, reason, available_at=0):
        with self.transaction() as db:
            db.execute("""
                UPDATE jobs SET state = ?, delivery_reason = ?, available_at = ?,
                                claim = NULL, lease_until = NULL
                WHERE id = ? AND state = 'SENDING' AND claim = ?
            """, (state, reason, available_at, job["id"], job["claim"]))

    def record_mock_reply(self, job_id, message):
        with self.transaction() as db:
            db.execute("INSERT INTO mock_replies VALUES (?, ?, ?, ?, ?, ?)", (
                job_id, message["channel"], message["thread_ts"], message["text"],
                message["unfurl_links"], message["unfurl_media"],
            ))
