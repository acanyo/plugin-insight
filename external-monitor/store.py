"""Durable transitions and delivery retries on the probe host."""
import json
import sqlite3
from pathlib import Path


class Store:
    def __init__(self, path):
        if path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(path)
        self.db.row_factory = sqlite3.Row
        self.db.executescript("""
            PRAGMA journal_mode=WAL;
            PRAGMA busy_timeout=5000;
            CREATE TABLE IF NOT EXISTS state (id INTEGER PRIMARY KEY, payload TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS event (id TEXT PRIMARY KEY, at REAL NOT NULL, payload TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS delivery (
                id TEXT NOT NULL, channel TEXT NOT NULL, payload TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0, next_at REAL NOT NULL,
                state TEXT NOT NULL DEFAULT 'pending', message TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(id,channel));
        """)

    def state(self):
        row = self.db.execute("SELECT payload FROM state WHERE id=1").fetchone()
        return json.loads(row[0]) if row else {
            "status": "unknown", "failures": 0, "successes": 0, "down_since": 0, "last_check": 0
        }

    def save(self, state, event=None, channels=()):
        with self.db:
            self.db.execute("INSERT OR REPLACE INTO state VALUES(1,?)", (json.dumps(state),))
            if event:
                payload = json.dumps(event, ensure_ascii=False)
                self.db.execute("UPDATE delivery SET state='superseded',message='newer availability state' WHERE state='pending'")
                self.db.execute("INSERT INTO event VALUES(?,?,?)", (event["id"], event["at"], payload))
                for channel in channels:
                    self.db.execute("INSERT INTO delivery(id,channel,payload,next_at) VALUES(?,?,?,?)",
                                    (event["id"], channel, payload, event["at"]))
            self.db.execute("DELETE FROM event WHERE id IN (SELECT id FROM event ORDER BY at DESC LIMIT -1 OFFSET 2000)")
            self.db.execute("""DELETE FROM delivery WHERE state <> 'pending' AND rowid IN
                (SELECT rowid FROM delivery WHERE state <> 'pending' ORDER BY rowid DESC LIMIT -1 OFFSET 4000)""")

    def due(self, now):
        return self.db.execute(
            "SELECT * FROM delivery WHERE state='pending' AND next_at<=? ORDER BY next_at LIMIT 10", (now,)
        ).fetchall()

    def complete(self, row, now, success, message):
        attempts = row["attempts"] + 1
        state = "sent" if success else ("failed" if attempts >= 5 else "pending")
        with self.db:
            self.db.execute("""UPDATE delivery SET attempts=?,next_at=?,state=?,message=?
                WHERE id=? AND channel=? AND state='pending'""",
                (attempts, now + min(900, 30 * 2 ** (attempts - 1)), state, message, row["id"], row["channel"]))

    def close(self):
        self.db.close()
