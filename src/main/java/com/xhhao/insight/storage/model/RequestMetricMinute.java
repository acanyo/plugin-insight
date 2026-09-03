package com.xhhao.insight.storage.model;

import java.util.Locale;

public record RequestMetricMinute(
    long bucketStartMs,
    String scope,
    String route,
    String method,
    int statusGroup,
    long requestCount,
    long errorCount,
    long durationSumUs,
    long durationMaxUs,
    LatencyHistogram histogram
) {

    public RequestMetricMinute {
        ModelChecks.nonNegative(bucketStartMs, "bucketStartMs");
        scope = ModelChecks.required(scope, "scope");
        route = ModelChecks.required(route, "route");
        if (route.indexOf('?') >= 0 || route.indexOf('#') >= 0) {
            throw new IllegalArgumentException("route must be normalized and exclude query/fragment data");
        }
        method = ModelChecks.required(method, "method").toUpperCase(Locale.ROOT);
        if (statusGroup < 1 || statusGroup > 5) {
            throw new IllegalArgumentException("statusGroup must be between 1 and 5");
        }
        ModelChecks.nonNegative(requestCount, "requestCount");
        ModelChecks.nonNegative(errorCount, "errorCount");
        ModelChecks.nonNegative(durationSumUs, "durationSumUs");
        ModelChecks.nonNegative(durationMaxUs, "durationMaxUs");
        if (histogram == null) {
            throw new IllegalArgumentException("histogram must not be null");
        }
        if (histogram.totalCount() != requestCount) {
            throw new IllegalArgumentException("histogram total must equal requestCount");
        }
    }
}
