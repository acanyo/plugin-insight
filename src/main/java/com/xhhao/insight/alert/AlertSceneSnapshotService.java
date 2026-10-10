package com.xhhao.insight.alert;

import com.xhhao.insight.consumer.PluginConsumerService;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.storage.model.RequestRouteSort;
import com.xhhao.insight.storage.repository.RequestMetricRepository;

import org.springframework.stereotype.Component;

import java.util.List;

/** Invoked only when a new incident opens; one capture is reused within the evaluation cycle. */
@Component
public class AlertSceneSnapshotService {
    private final MonitoringState state;
    private final PluginConsumerService consumers;
    private final RequestMetricRepository requests;
    private AlertSceneSnapshot cached;

    public AlertSceneSnapshotService(
            MonitoringState state,
            PluginConsumerService consumers,
            RequestMetricRepository requests) {
        this.state = state;
        this.consumers = consumers;
        this.requests = requests;
    }

    public synchronized AlertSceneSnapshot capture(AlertEvaluationInput input, long now) {
        if (cached != null && cached.capturedAtMs() == now) return cached;
        var system = state.system();
        var window = state.requests();
        var problems = new java.util.ArrayList<String>();
        List<AlertSceneSnapshot.PluginEvidence> plugins;
        List<AlertSceneSnapshot.RouteEvidence> routes;
        try {
            plugins =
                    consumers.find(Math.max(0, now - 900_000), now + 1).stream()
                            .limit(5)
                            .map(
                                    plugin ->
                                            new AlertSceneSnapshot.PluginEvidence(
                                                    bound(plugin.pluginId()),
                                                    bound(plugin.displayName()),
                                                    plugin.pressureScore(),
                                                    plugin.cpuSharePercent(),
                                                    plugin.allocatedBytesPerMinute(),
                                                    plugin.sustained()))
                            .toList();
        } catch (RuntimeException unavailable) {
            plugins = List.of();
            problems.add("插件排行不可用");
        }
        try {
            routes =
                    requests
                            .findTopRoutes(
                                    Math.max(0, now - 900_000),
                                    now + 1,
                                    RequestRouteSort.P95_DURATION,
                                    5)
                            .stream()
                            .map(
                                    route ->
                                            new AlertSceneSnapshot.RouteEvidence(
                                                    bound(route.method()),
                                                    bound(route.route()),
                                                    route.requestCount(),
                                                    route.errorCount(),
                                                    route.p95DurationUs() / 1000d))
                            .toList();
        } catch (RuntimeException unavailable) {
            routes = List.of();
            problems.add("接口排行不可用");
        }
        if (system == null) problems.add("系统采样不可用");
        else if (now - system.timestampMs() > 900_000 || now < system.timestampMs())
            problems.add("系统采样已过期");
        cached =
                new AlertSceneSnapshot(
                        now,
                        system == null ? 0 : system.timestampMs(),
                        system == null ? null : system.processCpuPercent(),
                        system == null ? null : system.heapUsedPercent(),
                        Double.isFinite(input.recentGcPercent()) ? input.recentGcPercent() : null,
                        window.requestCount(),
                        window.requestCount() == 0 ? null : window.p95Ms(),
                        window.fromMs(),
                        window.toMs(),
                        plugins,
                        routes,
                        "首次触发现场，恢复后保留；插件和接口排行来自触发前 15 分钟已落盘数据，可能存在聚合延迟。"
                                + (problems.isEmpty() ? "" : String.join("；", problems) + "。"));
        return cached;
    }

    private static String bound(String value) {
        return value == null ? "" : value.substring(0, Math.min(240, value.length()));
    }
}
