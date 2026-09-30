"""Run on a host independent of Halo: python main.py."""
import json
import signal
import threading
import time

from channels import probe
from config import Config
from delivery import deliver
from monitor import evaluate
from store import Store


def run():
    config = Config.from_env()
    store = Store(config.database)
    stopping = threading.Event()
    for sig in (signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, lambda *_: stopping.set())
    channels = (["webhook"] if config.webhook else []) + (
        ["email"] if config.smtp_host and config.smtp_from and config.smtp_to else [])
    print(json.dumps({"event": "probe_started", "channels": channels}), flush=True)
    last_report = 0
    worker = threading.Thread(target=deliver, args=(config, stopping), name="delivery", daemon=True)
    worker.start()
    try:
        while not stopping.is_set():
            if not worker.is_alive():
                raise RuntimeError("Notification worker stopped; restart the probe")
            started = time.monotonic()
            now = time.time()
            healthy, detail = probe(config)
            state, event = evaluate(store.state(), healthy, now, config.failures, config.recoveries, detail)
            store.save(state, event, channels)
            if event:
                print(json.dumps(event, ensure_ascii=False), flush=True)
            elif now - last_report >= 300:
                print(json.dumps({"event": "probe_status", "status": state["status"], "last_check": now}), flush=True)
                last_report = now
            stopping.wait(max(0, config.interval - (time.monotonic() - started)))
    finally:
        stopping.set()
        worker.join(timeout=config.timeout + 2)
        store.close()


if __name__ == "__main__":
    run()
