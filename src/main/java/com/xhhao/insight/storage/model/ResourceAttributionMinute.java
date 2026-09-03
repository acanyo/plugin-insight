package com.xhhao.insight.storage.model;

/** Coverage metadata that makes sampled plugin resource attribution explainable. */
public record ResourceAttributionMinute(
    long bucketStartMs,
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

    public ResourceAttributionMinute {
        ModelChecks.nonNegative(bucketStartMs, "bucketStartMs");
        ModelChecks.nonNegative(sampleCount, "sampleCount");
        ModelChecks.nonNegative(cpuCapableSamples, "cpuCapableSamples");
        ModelChecks.nonNegative(allocationCapableSamples, "allocationCapableSamples");
        ModelChecks.nonNegative(truncatedSamples, "truncatedSamples");
        ModelChecks.nonNegative(inspectedThreadSamples, "inspectedThreadSamples");
        ModelChecks.nonNegative(attributedThreadSamples, "attributedThreadSamples");
        ModelChecks.nonNegative(totalCpuTimeNs, "totalCpuTimeNs");
        ModelChecks.nonNegative(attributedCpuTimeNs, "attributedCpuTimeNs");
        ModelChecks.nonNegative(totalAllocatedBytes, "totalAllocatedBytes");
        ModelChecks.nonNegative(attributedAllocatedBytes, "attributedAllocatedBytes");
    }
}
