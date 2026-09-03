package com.xhhao.insight.storage.model;

/** Aggregated plugin resource attribution for an API time range. */
public record PluginResourceSummary(
    String pluginId,
    long sampleCount,
    long cpuMeasurementSamples,
    long allocationMeasurementSamples,
    long cpuActiveSamples,
    long activeThreadSamples,
    int liveThreadsMax,
    long cpuTimeNs,
    long allocatedBytes
) {
}
