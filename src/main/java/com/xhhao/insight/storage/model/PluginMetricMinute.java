package com.xhhao.insight.storage.model;

public record PluginMetricMinute(
    long bucketStartMs,
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

    public PluginMetricMinute {
        ModelChecks.nonNegative(bucketStartMs, "bucketStartMs");
        pluginId = ModelChecks.required(pluginId, "pluginId");
        ModelChecks.nonNegative(sampleCount, "sampleCount");
        ModelChecks.nonNegative(cpuActiveSamples, "cpuActiveSamples");
        ModelChecks.nonNegative(activeThreadSamples, "activeThreadSamples");
        ModelChecks.nonNegative(liveThreadsMax, "liveThreadsMax");
        ModelChecks.nonNegative(requestCount, "requestCount");
        ModelChecks.nonNegative(requestErrorCount, "requestErrorCount");
        ModelChecks.nonNegative(requestDurationSumUs, "requestDurationSumUs");
        ModelChecks.nonNegative(requestDurationMaxUs, "requestDurationMaxUs");
        ModelChecks.nonNegative(taskCount, "taskCount");
        ModelChecks.nonNegative(taskErrorCount, "taskErrorCount");
        ModelChecks.nonNegative(taskDurationSumUs, "taskDurationSumUs");
        ModelChecks.nonNegative(taskDurationMaxUs, "taskDurationMaxUs");
    }
}
