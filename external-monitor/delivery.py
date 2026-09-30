"""Delivery worker has a separate connection and cannot delay health probes."""
import json
import time
from channels import send
from store import Store


def deliver(config, stopping):
    store = Store(config.database)
    try:
        while not stopping.is_set():
            for row in store.due(time.time()):
                if stopping.is_set():
                    break
                try:
                    send(config, row["channel"], json.loads(row["payload"]))
                    store.complete(row, time.time(), True, "accepted")
                except Exception as error:
                    store.complete(row, time.time(), False, type(error).__name__)
                    print(json.dumps({"event": "delivery_failed", "id": row["id"],
                                      "channel": row["channel"], "attempt": row["attempts"] + 1}), flush=True)
            stopping.wait(5)
    finally:
        store.close()
