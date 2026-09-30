package com.xhhao.insight.optimization;

import java.util.List;

/** Current JVM/Halo process diagnosis with evidence-based findings. */
public record RuntimeDiagnosis(
    String level,
    String summary,
    long heapUsedBytes,
    long heapMaxBytes,
    double heapUsedPercent,
    double processCpuPercent,
    double systemCpuPercent,
    long gcCount,
    long gcTimeMs,
    double gcOverheadPercent,
    int liveThreads,
    int deadlockedThreads,
    List<Finding> findings,
    boolean gcWindowSufficient
) {

    public record Finding(
        String id,
        String severity,
        String title,
        String description
    ) {
    }
}
