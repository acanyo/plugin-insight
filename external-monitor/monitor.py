"""Availability transition logic, separately testable without network calls."""
import uuid


def evaluate(previous, healthy, now, failure_threshold, recovery_threshold, detail):
    state = dict(previous)
    state["last_check"] = now
    event = None
    if healthy:
        state["failures"] = 0
        state["successes"] += 1
        if state["status"] == "down" and state["successes"] >= recovery_threshold:
            event = {"id": str(uuid.uuid4()), "at": now, "status": "recovered",
                     "downtime_seconds": max(0, now - state["down_since"]), "message": "Halo 已恢复访问"}
            state["status"] = "up"
            state["down_since"] = 0
        elif state["status"] == "unknown" and state["successes"] >= recovery_threshold:
            state["status"] = "up"
    else:
        if state["failures"] == 0 and state["status"] != "down":
            state["down_since"] = now
        state["successes"] = 0
        state["failures"] += 1
        if state["status"] != "down" and state["failures"] >= failure_threshold:
            state["status"] = "down"
            event = {"id": str(uuid.uuid4()), "at": now, "status": "down",
                     "downtime_seconds": max(0, now - state["down_since"]), "message": "Halo 无法访问：" + detail}
    return state, event

