package com.xhhao.insight.resource;

/** Resource deltas attributed only when a thread keeps the same plugin class loader. */
public record PluginResourceDelta(
    String pluginId,
    long cpuMeasurementSamples,
    long allocationMeasurementSamples,
    long cpuActiveSamples,
    long activeThreadSamples,
    int liveThreads,
    long cpuTimeNs,
    long allocatedBytes
) {
}
