package com.xhhao.insight.storage.model;

/** Minute-level persistence evidence for request work attributed to a plugin. */
public record PluginRequestPersistenceSummary(
    String pluginId,
    int observedMinutes,
    int activeMinutes,
    int topDurationMinutes,
    long requestCount,
    long requestDurationUs
) {
}
