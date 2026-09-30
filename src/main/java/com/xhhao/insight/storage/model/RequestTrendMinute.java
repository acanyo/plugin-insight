package com.xhhao.insight.storage.model;

/**
 * Route-independent request totals for one minute, used by long-range trend queries.
 */
public record RequestTrendMinute(
    long bucketStartMs,
    long intervalMs,
    long requestCount,
    long clientErrorCount,
    long errorCount,
    long durationSumUs,
    long durationMaxUs,
    LatencyHistogram histogram
) {

    public RequestTrendMinute {
        ModelChecks.nonNegative(bucketStartMs, "bucketStartMs");
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("intervalMs must be positive");
        }
        ModelChecks.nonNegative(requestCount, "requestCount");
        ModelChecks.nonNegative(clientErrorCount, "clientErrorCount");
        ModelChecks.nonNegative(errorCount, "errorCount");
        ModelChecks.nonNegative(durationSumUs, "durationSumUs");
        ModelChecks.nonNegative(durationMaxUs, "durationMaxUs");
        if (histogram == null) {
            throw new IllegalArgumentException("histogram must not be null");
        }
    }
}
