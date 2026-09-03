package com.xhhao.insight.storage.model;

/** One minute projection of safely attributable plugin thread resource deltas. */
public record PluginResourceMinute(
    long bucketStartMs,
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

    public PluginResourceMinute {
        ModelChecks.nonNegative(bucketStartMs, "bucketStartMs");
        pluginId = ModelChecks.required(pluginId, "pluginId");
        ModelChecks.nonNegative(sampleCount, "sampleCount");
        ModelChecks.nonNegative(cpuMeasurementSamples, "cpuMeasurementSamples");
        ModelChecks.nonNegative(allocationMeasurementSamples, "allocationMeasurementSamples");
        ModelChecks.nonNegative(cpuActiveSamples, "cpuActiveSamples");
        ModelChecks.nonNegative(activeThreadSamples, "activeThreadSamples");
        ModelChecks.nonNegative(liveThreadsMax, "liveThreadsMax");
        ModelChecks.nonNegative(cpuTimeNs, "cpuTimeNs");
        ModelChecks.nonNegative(allocatedBytes, "allocatedBytes");
    }
}
