package com.xhhao.insight.api.model;

public record RequestRouteItem(
    String scope,
    String route,
    String method,
    long requestCount,
    long errorCount,
    double errorRatePercent,
    double averageLatencyMs,
    double p95Ms,
    double p99Ms,
    double maximumLatencyMs
) {}
