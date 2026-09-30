package com.xhhao.insight.api.model;

import java.util.List;

public record TrendResponse(
    String range,
    long fromMs,
    long toMs,
    List<SystemPoint> system,
    List<RequestPoint> requests
) {

    /**
     * One chart point derived from either a raw sample or one or more minute aggregates.
     *
     * <p>Gauge values are the raw reading for raw points. For minute-derived points,
     * {@code systemLoadAverage} and {@code nonHeapUsedBytes} are sample-weighted averages,
     * {@code daemonThreads} is the last reading, while {@code peakThreads} and
     * {@code deadlockedThreads} are the maximum observed values. GC fields are interval deltas,
     * never process-lifetime counters.</p>
     */
    public record SystemPoint(
        long timestampMs,
        double processCpuPercent,
        double systemCpuPercent,
        double systemLoadAverage,
        long heapUsedBytes,
        long heapMaxBytes,
        long nonHeapUsedBytes,
        long memoryUsedBytes,
        long memoryTotalBytes,
        long diskUsedBytes,
        long diskTotalBytes,
        int liveThreads,
        int daemonThreads,
        int peakThreads,
        int deadlockedThreads,
        long gcCountDelta,
        long gcPauseMs
    ) {}

    public record RequestPoint(
        long timestampMs,
        long requestCount,
        double requestsPerMinute,
        long clientErrorCount,
        double clientErrorRatePercent,
        long serverErrorCount,
        double serverErrorRatePercent,
        long errorCount,
        double errorRatePercent,
        double averageLatencyMs,
        double p95Ms,
        double p99Ms
    ) {}
}
