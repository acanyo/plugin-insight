package com.xhhao.insight.api.model;

import com.xhhao.insight.halo.DependencyStatus;
import com.xhhao.insight.halo.PluginRuntimeInfo;
import com.xhhao.insight.storage.model.AlertIncident;
import java.util.List;
import java.util.Map;

public record OverviewResponse(
    long generatedAtMs,
    String health,
    RuntimeView runtime,
    JvmView jvm,
    RequestView requests,
    StorageView storage,
    List<DependencyStatus> dependencies,
    List<PluginRuntimeInfo> plugins,
    List<AlertIncident> activeAlerts
) {

    public record RuntimeView(
        boolean running,
        long uptimeMs,
        String haloVersion,
        String javaVersion,
        String osName,
        double processCpuPercent,
        double systemCpuPercent,
        long systemMemoryUsedBytes,
        long systemMemoryTotalBytes,
        long diskUsedBytes,
        long diskTotalBytes,
        long lastCollectedAtMs,
        String lastCollectionError
    ) {}

    public record JvmView(
        long heapUsedBytes,
        long heapMaxBytes,
        double heapUsedPercent,
        long nonHeapUsedBytes,
        long gcCount,
        long gcPauseMs,
        int liveThreads,
        int daemonThreads,
        int peakThreads,
        int deadlockedThreads
    ) {}

    public record RequestView(
        long windowFromMs,
        long windowToMs,
        long requestCount,
        long errorCount,
        double errorRatePercent,
        double averageLatencyMs,
        long p95Ms,
        long p99Ms,
        long maximumLatencyMs,
        Map<String, Long> statusCodes
    ) {}

    public record StorageView(
        String state,
        String message,
        boolean available,
        boolean writePaused,
        long sizeBytes,
        long maxSizeBytes,
        long checkedAtMs,
        long failureCount
    ) {}
}
