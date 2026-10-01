package com.xhhao.insight.consumer;

/** Evidence-based ranking item for a plugin that remains expensive across multiple minutes. */
public record PluginConsumerItem(
    String pluginId,
    String displayName,
    boolean enabled,
    String phase,
    double pressureScore,
    String pressureLevel,
    String dominantResource,
    boolean sustained,
    String confidence,
    int observedMinutes,
    int activeMinutes,
    int topConsumerMinutes,
    double persistencePercent,
    double cpuSharePercent,
    double allocationSharePercent,
    double requestLoadSharePercent,
    double allocatedBytesPerMinute,
    int attributedThreadsMax,
    String version,
    String issuesUrl,
    long fromMs,
    long toMs
) {
}
