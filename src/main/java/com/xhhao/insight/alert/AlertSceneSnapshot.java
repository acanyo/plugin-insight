package com.xhhao.insight.alert;

import java.util.List;

/** Safe structured evidence; never contains raw logs, stack traces or request payloads. */
public record AlertSceneSnapshot(
        long capturedAtMs,
        long sampledAtMs,
        Double processCpuPercent,
        Double heapUsedPercent,
        Double gcPercent,
        long requestCount,
        Long p95Ms,
        long requestFromMs,
        long requestToMs,
        List<PluginEvidence> plugins,
        List<RouteEvidence> routes,
        String coverage) {
    public record PluginEvidence(
            String pluginId,
            String displayName,
            double pressureScore,
            double cpuSharePercent,
            double allocatedBytesPerMinute,
            boolean sustained) {}

    public record RouteEvidence(
            String method, String route, long requests, long errors, double p95Ms) {}
}
