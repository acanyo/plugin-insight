package com.xhhao.insight.metrics;

import java.util.Map;

/** Aggregated request metrics for the live monitoring window. */
public record RequestWindowSnapshot(
    long fromMs,
    long toMs,
    long requestCount,
    long errorCount,
    long durationTotalMs,
    long durationMaxMs,
    long p95Ms,
    long p99Ms,
    Map<Integer, Long> statusGroups
) {

    public RequestWindowSnapshot {
        statusGroups = Map.copyOf(statusGroups);
    }

    public double averageLatencyMs() {
        return requestCount == 0 ? 0 : durationTotalMs / (double) requestCount;
    }

    public double errorRatePercent() {
        return requestCount == 0 ? 0 : errorCount * 100d / requestCount;
    }

    public static RequestWindowSnapshot empty(long nowMs) {
        return new RequestWindowSnapshot(nowMs, nowMs, 0, 0, 0, 0, 0, 0, Map.of());
    }
}
