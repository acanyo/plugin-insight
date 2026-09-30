package com.xhhao.insight.alert;

import com.xhhao.insight.halo.DependencyStatus;
import java.util.List;

/**
 * Immutable values consumed by one alert evaluation cycle.
 */
public record AlertEvaluationInput(
    double systemCpuPercent,
    double heapUsedPercent,
    double diskUsedPercent,
    double requestErrorRatePercent,
    long requestP95Millis,
    int deadlockedThreadCount,
    double recentGcPercent,
    double sustainedPluginCount,
    double failedDependencyCount,
    double failedTaskCount,
    double growingHeap,
    List<DependencyStatus> failedDependencies
) {

    public AlertEvaluationInput(double cpu, double heap, double disk, double errors, long p95, int deadlocks) {
        this(cpu, heap, disk, errors, p95, deadlocks, Double.NaN, Double.NaN,
            Double.NaN, Double.NaN, Double.NaN, List.of());
    }

    public AlertEvaluationInput(
        double systemCpuPercent,
        double heapUsedPercent,
        double diskUsedPercent,
        double requestErrorRatePercent,
        long requestP95Millis,
        int deadlockedThreadCount,
        double recentGcPercent,
        double sustainedPluginCount,
        double failedDependencyCount,
        double failedTaskCount,
        double growingHeap
    ) {
        this(systemCpuPercent, heapUsedPercent, diskUsedPercent, requestErrorRatePercent,
            requestP95Millis, deadlockedThreadCount, recentGcPercent, sustainedPluginCount,
            failedDependencyCount, failedTaskCount, growingHeap, List.of());
    }

    public AlertEvaluationInput {
        systemCpuPercent = percentage(systemCpuPercent);
        heapUsedPercent = percentage(heapUsedPercent);
        diskUsedPercent = percentage(diskUsedPercent);
        requestErrorRatePercent = percentage(requestErrorRatePercent);
        requestP95Millis = Math.max(0, requestP95Millis);
        deadlockedThreadCount = Math.max(0, deadlockedThreadCount);
        failedDependencies = failedDependencies == null
            ? List.of()
            : List.copyOf(failedDependencies);
    }

    private static double percentage(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        return Math.max(0, Math.min(100, value));
    }
}
