package com.xhhao.insight.alert;

/**
 * Immutable values consumed by one alert evaluation cycle.
 */
public record AlertEvaluationInput(
    double systemCpuPercent,
    double heapUsedPercent,
    double diskUsedPercent,
    double requestErrorRatePercent,
    long requestP95Millis,
    int deadlockedThreadCount
) {

    public AlertEvaluationInput {
        systemCpuPercent = percentage(systemCpuPercent);
        heapUsedPercent = percentage(heapUsedPercent);
        diskUsedPercent = percentage(diskUsedPercent);
        requestErrorRatePercent = percentage(requestErrorRatePercent);
        requestP95Millis = Math.max(0, requestP95Millis);
        deadlockedThreadCount = Math.max(0, deadlockedThreadCount);
    }

    private static double percentage(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        return Math.max(0, Math.min(100, value));
    }
}
