package com.xhhao.insight.storage.model;

public record AlertIncident(
    String id,
    String ruleId,
    String status,
    String severity,
    String title,
    String summary,
    String fingerprint,
    long triggeredAtMs,
    long updatedAtMs,
    Long resolvedAtMs,
    long occurrenceCount,
    Double lastValue,
    Double thresholdValue,
    String detailsJson
) {

    public AlertIncident {
        id = ModelChecks.required(id, "id");
        ruleId = ModelChecks.required(ruleId, "ruleId");
        status = ModelChecks.required(status, "status");
        severity = ModelChecks.required(severity, "severity");
        title = ModelChecks.required(title, "title");
        summary = ModelChecks.required(summary, "summary");
        fingerprint = ModelChecks.required(fingerprint, "fingerprint");
        ModelChecks.nonNegative(triggeredAtMs, "triggeredAtMs");
        ModelChecks.nonNegative(updatedAtMs, "updatedAtMs");
        if (resolvedAtMs != null) {
            ModelChecks.nonNegative(resolvedAtMs, "resolvedAtMs");
        }
        ModelChecks.nonNegative(occurrenceCount, "occurrenceCount");
    }
}
