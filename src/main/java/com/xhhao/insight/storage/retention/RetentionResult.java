package com.xhhao.insight.storage.retention;

public record RetentionResult(
    int systemSamples,
    int systemMetrics,
    int pluginMetrics,
    int pluginResourceMetrics,
    int resourceAttributionMetrics,
    int requestMetrics,
    int events,
    int resolvedAlerts,
    int diagnostics,
    int deliveries
) {

    public RetentionResult(int systemSamples, int systemMetrics, int pluginMetrics, int pluginResourceMetrics,
        int resourceAttributionMetrics, int requestMetrics, int events, int resolvedAlerts) {
        this(systemSamples, systemMetrics, pluginMetrics, pluginResourceMetrics, resourceAttributionMetrics,
            requestMetrics, events, resolvedAlerts, 0, 0);
    }

    public int totalDeleted() {
        return systemSamples + systemMetrics + pluginMetrics + pluginResourceMetrics
            + resourceAttributionMetrics + requestMetrics + events + resolvedAlerts + diagnostics + deliveries;
    }

    public boolean mayHaveMore(int batchSize) {
        return systemSamples == batchSize
            || systemMetrics == batchSize
            || pluginMetrics == batchSize
            || pluginResourceMetrics == batchSize
            || resourceAttributionMetrics == batchSize
            || requestMetrics == batchSize
            || events == batchSize
            || resolvedAlerts == batchSize || diagnostics == batchSize || deliveries == batchSize;
    }

    public RetentionResult(
        int systemSamples,
        int systemMetrics,
        int pluginMetrics,
        int requestMetrics,
        int events,
        int resolvedAlerts
    ) {
        this(systemSamples, systemMetrics, pluginMetrics, 0, 0, requestMetrics, events,
            resolvedAlerts);
    }

    public RetentionResult(
        int systemSamples,
        int pluginMetrics,
        int requestMetrics,
        int events,
        int resolvedAlerts
    ) {
        this(systemSamples, 0, pluginMetrics, 0, 0, requestMetrics, events, resolvedAlerts);
    }
}
