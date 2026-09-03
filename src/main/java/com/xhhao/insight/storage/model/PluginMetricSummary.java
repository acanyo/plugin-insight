package com.xhhao.insight.storage.model;

public record PluginMetricSummary(
    String pluginId,
    long sampleCount,
    long cpuActiveSamples,
    long activeThreadSamples,
    int liveThreadsMax,
    long requestCount,
    long requestErrorCount,
    long requestDurationSumUs,
    long requestDurationMaxUs,
    long taskCount,
    long taskErrorCount,
    long taskDurationSumUs,
    long taskDurationMaxUs
) {

    public double averageRequestDurationUs() {
        return requestCount == 0 ? 0 : (double) requestDurationSumUs / requestCount;
    }

    public double requestErrorRate() {
        return requestCount == 0 ? 0 : (double) requestErrorCount / requestCount;
    }
}
