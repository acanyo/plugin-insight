package com.xhhao.insight.storage.model;

/** Stable keyset cursor for alert history ordered by updated time and id. */
public record AlertIncidentCursor(long updatedAtMs, String id) {

    public AlertIncidentCursor {
        ModelChecks.nonNegative(updatedAtMs, "updatedAtMs");
        id = ModelChecks.required(id, "id");
    }
}
