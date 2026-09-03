package com.xhhao.insight.storage.model;

public record MonitorEvent(
    String id,
    long occurredAtMs,
    String type,
    String severity,
    String source,
    String title,
    String summary,
    String fingerprint,
    long firstSeenAtMs,
    long lastSeenAtMs,
    long occurrenceCount,
    String detailsJson
) {

    public MonitorEvent {
        id = ModelChecks.required(id, "id");
        ModelChecks.nonNegative(occurredAtMs, "occurredAtMs");
        type = ModelChecks.required(type, "type");
        severity = ModelChecks.required(severity, "severity");
        source = ModelChecks.required(source, "source");
        title = ModelChecks.required(title, "title");
        summary = ModelChecks.required(summary, "summary");
        fingerprint = ModelChecks.required(fingerprint, "fingerprint");
        ModelChecks.nonNegative(firstSeenAtMs, "firstSeenAtMs");
        ModelChecks.nonNegative(lastSeenAtMs, "lastSeenAtMs");
        ModelChecks.nonNegative(occurrenceCount, "occurrenceCount");
    }
}
