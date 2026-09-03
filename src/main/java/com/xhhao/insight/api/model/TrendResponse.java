package com.xhhao.insight.api.model;

import java.util.List;

public record TrendResponse(
    String range,
    long fromMs,
    long toMs,
    List<SystemPoint> system,
    List<RequestPoint> requests
) {

    public record SystemPoint(
        long timestampMs,
        double processCpuPercent,
        double systemCpuPercent,
        long heapUsedBytes,
        long heapMaxBytes,
        long memoryUsedBytes,
        long memoryTotalBytes,
        long diskUsedBytes,
        long diskTotalBytes,
        int liveThreads,
        long gcPauseMs
    ) {}

    public record RequestPoint(
        long timestampMs,
        long requestCount,
        long errorCount,
        double errorRatePercent,
        double averageLatencyMs,
        double p95Ms,
        double p99Ms
    ) {}
}
