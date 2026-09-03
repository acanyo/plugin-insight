package com.xhhao.insight.metrics;

import java.util.Arrays;

public record RequestMetric(
    long bucketStartMs,
    String scope,
    String route,
    String method,
    int statusGroup,
    long requestCount,
    long errorCount,
    long durationTotalMs,
    long durationMaxMs,
    long[] latencyBuckets
) {

    public RequestMetric {
        latencyBuckets = latencyBuckets == null ? new long[LatencyBuckets.size()]
            : Arrays.copyOf(latencyBuckets, latencyBuckets.length);
    }

    @Override
    public long[] latencyBuckets() {
        return Arrays.copyOf(latencyBuckets, latencyBuckets.length);
    }

    public double averageLatencyMs() {
        return requestCount == 0 ? 0 : durationTotalMs / (double) requestCount;
    }

    public double errorRatePercent() {
        return requestCount == 0 ? 0 : errorCount * 100d / requestCount;
    }
}
