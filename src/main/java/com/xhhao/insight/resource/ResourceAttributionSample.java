package com.xhhao.insight.resource;

import java.util.List;

/** One bounded sampling interval and the coverage needed to interpret it honestly. */
public record ResourceAttributionSample(
    long sampledAtMs,
    boolean cpuAvailable,
    boolean allocationAvailable,
    boolean truncated,
    int inspectedThreads,
    int attributedThreads,
    long totalCpuTimeNs,
    long attributedCpuTimeNs,
    long totalAllocatedBytes,
    long attributedAllocatedBytes,
    List<PluginResourceDelta> plugins
) {
}
