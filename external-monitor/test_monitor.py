import unittest
import tempfile
from config import checked_url
from monitor import evaluate
from store import Store


class MonitorTests(unittest.TestCase):
    def test_restart_preserves_retries_and_recovery_supersedes_pending_alarm(self):
        with tempfile.TemporaryDirectory() as directory:
            path = directory + "/probe.sqlite"
            store = Store(path)
            store.save(store.state(), {"id": "down", "at": 1}, ("webhook",))
            store.close()
            store = Store(path)
            stale = store.due(10)[0]
            store.save(store.state(), {"id": "up", "at": 20}, ("webhook",))
            store.complete(stale, 21, False, "timeout")
            self.assertEqual([row["id"] for row in store.due(100)], ["up"])
            self.assertEqual(store.db.execute("SELECT state FROM delivery WHERE id='down'").fetchone()[0], "superseded")
            store.close()

    def test_threshold_suppression_and_recovery_survive_restart(self):
        store = Store(":memory:")
        state = store.state()
        for now in (10, 40):
            state, event = evaluate(state, False, now, 3, 2, "timeout")
            self.assertIsNone(event)
        state, event = evaluate(state, False, 70, 3, 2, "timeout")
        self.assertEqual(event["status"], "down")
        store.save(state, event, ("webhook",))
        state, duplicate = evaluate(store.state(), False, 100, 3, 2, "timeout")
        self.assertIsNone(duplicate)
        state, event = evaluate(state, True, 130, 3, 2, "")
        self.assertIsNone(event)
        state, event = evaluate(state, True, 160, 3, 2, "")
        self.assertEqual(event["status"], "recovered")
        self.assertEqual(event["downtime_seconds"], 150)
        self.assertEqual(len(store.due(100)), 1)
        row = store.due(100)[0]
        store.complete(row, 100, False, "timeout")
        self.assertEqual(len(store.due(120)), 0)
        self.assertEqual(len(store.due(130)), 1)
        store.close()

    def test_success_before_threshold_resets_incident(self):
        store = Store(":memory:")
        state, _ = evaluate(store.state(), False, 10, 3, 2, "")
        state, _ = evaluate(state, True, 20, 3, 2, "")
        state, event = evaluate(state, False, 30, 3, 2, "")
        self.assertEqual(state["failures"], 1)
        self.assertEqual(state["down_since"], 30)
        self.assertIsNone(event)
        store.close()

    def test_retries_stop_at_five(self):
        store = Store(":memory:")
        event = {"id": "test", "at": 1, "message": "down"}
        store.save(store.state(), event, ("webhook",))
        for attempt in range(5):
            row = store.due(100000)[0]
            store.complete(row, 1, False, "timeout")
        self.assertEqual(store.due(100000), [])
        self.assertEqual(store.db.execute("SELECT state FROM delivery").fetchone()[0], "failed")
        store.close()

    def test_secure_urls(self):
        self.assertEqual(checked_url("https://example.com/"), "https://example.com/")
        for url in ("file:///etc/passwd", "https://user:password@example.com", "http://example.com"):
            with self.assertRaises(ValueError):
                checked_url(url)


if __name__ == "__main__":
    unittest.main()
