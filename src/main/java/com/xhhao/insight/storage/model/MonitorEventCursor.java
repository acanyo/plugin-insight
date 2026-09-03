package com.xhhao.insight.storage.model;

public record MonitorEventCursor(long occurredAtMs, String id) {

    public MonitorEventCursor {
        ModelChecks.nonNegative(occurredAtMs, "occurredAtMs");
        id = ModelChecks.required(id, "id");
    }
}
