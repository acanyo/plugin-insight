package com.xhhao.insight.storage.model;

public record SystemSample(
    long sampledAtMs,
    long uptimeMs,
    double processCpuLoad,
    double systemCpuLoad,
    double systemLoadAverage,
    long heapUsedBytes,
    long heapMaxBytes,
    long nonHeapUsedBytes,
    long systemMemoryUsedBytes,
    long systemMemoryTotalBytes,
    long diskUsedBytes,
    long diskTotalBytes,
    int liveThreads,
    int daemonThreads,
    int peakThreads,
    int deadlockedThreads,
    long gcCount,
    long gcPauseMs
) {

    public SystemSample {
        ModelChecks.nonNegative(sampledAtMs, "sampledAtMs");
        ModelChecks.nonNegative(uptimeMs, "uptimeMs");
        ModelChecks.nonNegative(heapUsedBytes, "heapUsedBytes");
        ModelChecks.nonNegative(heapMaxBytes, "heapMaxBytes");
        ModelChecks.nonNegative(nonHeapUsedBytes, "nonHeapUsedBytes");
        ModelChecks.nonNegative(systemMemoryUsedBytes, "systemMemoryUsedBytes");
        ModelChecks.nonNegative(systemMemoryTotalBytes, "systemMemoryTotalBytes");
        ModelChecks.nonNegative(diskUsedBytes, "diskUsedBytes");
        ModelChecks.nonNegative(diskTotalBytes, "diskTotalBytes");
        ModelChecks.nonNegative(liveThreads, "liveThreads");
        ModelChecks.nonNegative(daemonThreads, "daemonThreads");
        ModelChecks.nonNegative(peakThreads, "peakThreads");
        ModelChecks.nonNegative(deadlockedThreads, "deadlockedThreads");
        ModelChecks.nonNegative(gcCount, "gcCount");
        ModelChecks.nonNegative(gcPauseMs, "gcPauseMs");
    }
}
