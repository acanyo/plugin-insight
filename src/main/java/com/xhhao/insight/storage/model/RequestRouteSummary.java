package com.xhhao.insight.storage.model;

public record RequestRouteSummary(
    String scope,
    String route,
    String method,
    long requestCount,
    long errorCount,
    long durationSumUs,
    long durationMaxUs,
    LatencyHistogram histogram
) {

    public double averageDurationUs() {
        return requestCount == 0 ? 0 : (double) durationSumUs / requestCount;
    }

    public double errorRate() {
        return requestCount == 0 ? 0 : (double) errorCount / requestCount;
    }

    public long p95DurationUs() {
        return histogram.estimatePercentileUs(0.95, durationMaxUs);
    }

    public long p99DurationUs() {
        return histogram.estimatePercentileUs(0.99, durationMaxUs);
    }
}
