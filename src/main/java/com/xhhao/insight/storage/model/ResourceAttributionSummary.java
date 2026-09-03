package com.xhhao.insight.storage.model;

/** Aggregated sampling capabilities and coverage for an API time range. */
public record ResourceAttributionSummary(
    long sampleCount,
    long cpuCapableSamples,
    long allocationCapableSamples,
    long truncatedSamples,
    long inspectedThreadSamples,
    long attributedThreadSamples,
    long totalCpuTimeNs,
    long attributedCpuTimeNs,
    long totalAllocatedBytes,
    long attributedAllocatedBytes
) {

    public static ResourceAttributionSummary empty() {
        return new ResourceAttributionSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public double threadCoveragePercent() {
        return percentage(attributedThreadSamples, inspectedThreadSamples);
    }

    public double cpuCoveragePercent() {
        return percentage(attributedCpuTimeNs, totalCpuTimeNs);
    }

    public double allocationCoveragePercent() {
        return percentage(attributedAllocatedBytes, totalAllocatedBytes);
    }

    private static double percentage(long attributed, long total) {
        return total == 0 ? 0 : Math.min(100, (double) attributed / total * 100);
    }
}
