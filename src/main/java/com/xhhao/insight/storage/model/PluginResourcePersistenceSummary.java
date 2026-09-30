package com.xhhao.insight.storage.model;

/** Minute-level persistence evidence for plugin CPU, allocation, and thread pressure. */
public record PluginResourcePersistenceSummary(
    String pluginId,
    int observedMinutes,
    int cpuActiveMinutes,
    int allocationActiveMinutes,
    int threadPresentMinutes,
    int topCpuMinutes,
    int topAllocationMinutes,
    long cpuTimeNs,
    long allocatedBytes,
    int liveThreadsMax
) {
}
