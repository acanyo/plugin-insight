package com.xhhao.insight.metrics;

public record SystemSnapshot(
    long timestampMs,
    long uptimeMs,
    double processCpuPercent,
    double systemCpuPercent,
    double systemLoadAverage,
    long heapUsedBytes,
    long heapMaxBytes,
    long nonHeapUsedBytes,
    long systemMemoryUsedBytes,
    long systemMemoryTotalBytes,
    long gcCount,
    long gcTimeMs,
    int threadCount,
    int daemonThreadCount,
    int peakThreadCount,
    int deadlockedThreadCount,
    long diskTotalBytes,
    long diskFreeBytes
) {

    public double heapUsedPercent() {
        return percent(heapUsedBytes, heapMaxBytes);
    }

    public double diskUsedPercent() {
        return diskTotalBytes <= 0 ? 0 : 100 - percent(diskFreeBytes, diskTotalBytes);
    }

    private static double percent(long value, long total) {
        return total <= 0 ? 0 : Math.max(0, Math.min(100, value * 100d / total));
    }
}
