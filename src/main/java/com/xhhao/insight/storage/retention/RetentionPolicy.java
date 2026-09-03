package com.xhhao.insight.storage.retention;

import java.time.Duration;
import java.util.Objects;

public record RetentionPolicy(
    long systemSamplesBeforeMs,
    long systemMetricsBeforeMs,
    long pluginMetricsBeforeMs,
    long requestMetricsBeforeMs,
    long eventsBeforeMs,
    long resolvedAlertsBeforeMs
) {

    public RetentionPolicy {
        if (systemSamplesBeforeMs < 0 || systemMetricsBeforeMs < 0 || pluginMetricsBeforeMs < 0
            || requestMetricsBeforeMs < 0 || eventsBeforeMs < 0
            || resolvedAlertsBeforeMs < 0) {
            throw new IllegalArgumentException("Retention cutoffs must not be negative");
        }
    }

    /** Compatibility constructor for callers that retain both system granularities equally. */
    public RetentionPolicy(
        long systemSamplesBeforeMs,
        long pluginMetricsBeforeMs,
        long requestMetricsBeforeMs,
        long eventsBeforeMs,
        long resolvedAlertsBeforeMs
    ) {
        this(systemSamplesBeforeMs, systemSamplesBeforeMs, pluginMetricsBeforeMs,
            requestMetricsBeforeMs, eventsBeforeMs, resolvedAlertsBeforeMs);
    }

    public static RetentionPolicy fromDurations(
        long nowMs,
        Duration systemSamples,
        Duration systemMetrics,
        Duration pluginMetrics,
        Duration requestMetrics,
        Duration events,
        Duration resolvedAlerts
    ) {
        return new RetentionPolicy(
            cutoff(nowMs, systemSamples),
            cutoff(nowMs, systemMetrics),
            cutoff(nowMs, pluginMetrics),
            cutoff(nowMs, requestMetrics),
            cutoff(nowMs, events),
            cutoff(nowMs, resolvedAlerts)
        );
    }

    private static long cutoff(long nowMs, Duration duration) {
        Objects.requireNonNull(duration, "retention duration must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("retention duration must not be negative");
        }
        return Math.max(0, nowMs - duration.toMillis());
    }
}
