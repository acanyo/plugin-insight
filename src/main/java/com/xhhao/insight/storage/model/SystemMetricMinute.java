package com.xhhao.insight.storage.model;

/**
 * One minute projection of the raw system/JVM samples.
 *
 * <p>CPU, load, memory and live-thread gauges retain their average, maximum and/or last
 * value according to how they are charted. Capacity and uptime values use the last sample.
 * GC values are deltas observed during the minute, rather than process-lifetime counters.</p>
 */
public record SystemMetricMinute(
    long bucketStartMs,
    long lastSampledAtMs,
    int sampleCount,
    long uptimeLastMs,
    double processCpuAverage,
    double processCpuMax,
    double processCpuLast,
    double systemCpuAverage,
    double systemCpuMax,
    double systemCpuLast,
    double systemLoadAverage,
    double systemLoadMax,
    double systemLoadLast,
    long heapUsedAverageBytes,
    long heapUsedMaxBytes,
    long heapUsedLastBytes,
    long heapMaxLastBytes,
    long nonHeapUsedAverageBytes,
    long nonHeapUsedMaxBytes,
    long nonHeapUsedLastBytes,
    long systemMemoryUsedAverageBytes,
    long systemMemoryUsedMaxBytes,
    long systemMemoryUsedLastBytes,
    long systemMemoryTotalLastBytes,
    long diskUsedLastBytes,
    long diskTotalLastBytes,
    double liveThreadsAverage,
    int liveThreadsMax,
    int liveThreadsLast,
    int daemonThreadsLast,
    int peakThreadsMax,
    int deadlockedThreadsMax,
    int deadlockedThreadsLast,
    long gcCountDelta,
    long gcPauseMsDelta
) {

    public SystemMetricMinute {
        ModelChecks.nonNegative(bucketStartMs, "bucketStartMs");
        ModelChecks.nonNegative(lastSampledAtMs, "lastSampledAtMs");
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("sampleCount must be positive");
        }
        ModelChecks.nonNegative(uptimeLastMs, "uptimeLastMs");
        ModelChecks.nonNegative(heapUsedAverageBytes, "heapUsedAverageBytes");
        ModelChecks.nonNegative(heapUsedMaxBytes, "heapUsedMaxBytes");
        ModelChecks.nonNegative(heapUsedLastBytes, "heapUsedLastBytes");
        ModelChecks.nonNegative(heapMaxLastBytes, "heapMaxLastBytes");
        ModelChecks.nonNegative(nonHeapUsedAverageBytes, "nonHeapUsedAverageBytes");
        ModelChecks.nonNegative(nonHeapUsedMaxBytes, "nonHeapUsedMaxBytes");
        ModelChecks.nonNegative(nonHeapUsedLastBytes, "nonHeapUsedLastBytes");
        ModelChecks.nonNegative(systemMemoryUsedAverageBytes,
            "systemMemoryUsedAverageBytes");
        ModelChecks.nonNegative(systemMemoryUsedMaxBytes, "systemMemoryUsedMaxBytes");
        ModelChecks.nonNegative(systemMemoryUsedLastBytes, "systemMemoryUsedLastBytes");
        ModelChecks.nonNegative(systemMemoryTotalLastBytes, "systemMemoryTotalLastBytes");
        ModelChecks.nonNegative(diskUsedLastBytes, "diskUsedLastBytes");
        ModelChecks.nonNegative(diskTotalLastBytes, "diskTotalLastBytes");
        ModelChecks.nonNegative(liveThreadsMax, "liveThreadsMax");
        ModelChecks.nonNegative(liveThreadsLast, "liveThreadsLast");
        ModelChecks.nonNegative(daemonThreadsLast, "daemonThreadsLast");
        ModelChecks.nonNegative(peakThreadsMax, "peakThreadsMax");
        ModelChecks.nonNegative(deadlockedThreadsMax, "deadlockedThreadsMax");
        ModelChecks.nonNegative(deadlockedThreadsLast, "deadlockedThreadsLast");
        ModelChecks.nonNegative(gcCountDelta, "gcCountDelta");
        ModelChecks.nonNegative(gcPauseMsDelta, "gcPauseMsDelta");
    }
}
