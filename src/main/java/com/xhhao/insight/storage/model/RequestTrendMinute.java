package com.xhhao.insight.storage.model;

/**
 * Route-independent request totals for one minute, used by long-range trend queries.
 */
public record RequestTrendMinute(
    long bucketStartMs,
    long requestCount,
    long errorCount,
    long durationSumUs,
    long durationMaxUs,
    LatencyHistogram histogram
) {

    public RequestTrendMinute {
        ModelChecks.nonNegative(bucketStartMs, "bucketStartMs");
        ModelChecks.nonNegative(requestCount, "requestCount");
        ModelChecks.nonNegative(errorCount, "errorCount");
        ModelChecks.nonNegative(durationSumUs, "durationSumUs");
        ModelChecks.nonNegative(durationMaxUs, "durationMaxUs");
        if (histogram == null) {
            throw new IllegalArgumentException("histogram must not be null");
        }
    }
}
