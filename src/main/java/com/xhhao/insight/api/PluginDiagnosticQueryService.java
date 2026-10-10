package com.xhhao.insight.api;

import com.xhhao.insight.api.model.RequestRouteItem;
import com.xhhao.insight.consumer.PluginConsumerItem;
import com.xhhao.insight.consumer.PluginConsumerService;
import com.xhhao.insight.diagnostics.DiagnosticRecords;
import com.xhhao.insight.halo.PluginRuntimeInfo;
import com.xhhao.insight.optimization.OptimizationObservationService.Observation;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.repository.PluginDiagnosticRepository;
import com.xhhao.insight.storage.repository.PluginDiagnosticRepository.Event;
import com.xhhao.insight.storage.repository.PluginDiagnosticRepository.Point;
import com.xhhao.insight.storage.repository.RequestMetricRepository;

import org.springframework.stereotype.Component;

import java.util.List;

/** Joins bounded structured evidence only; logs remain behind the diagnostic permission. */
@Component
public class PluginDiagnosticQueryService {
    private final MonitoringState state;
    private final SqliteStorageLifecycle storage;
    private final PluginDiagnosticRepository repository;
    private final PluginConsumerService consumers;
    private final RequestMetricRepository requests;
    private final DiagnosticRecords records;

    public PluginDiagnosticQueryService(
            MonitoringState state,
            SqliteStorageLifecycle storage,
            PluginDiagnosticRepository repository,
            PluginConsumerService consumers,
            RequestMetricRepository requests,
            DiagnosticRecords records) {
        this.state = state;
        this.storage = storage;
        this.repository = repository;
        this.consumers = consumers;
        this.requests = requests;
        this.records = records;
    }

    public Detail detail(String pluginId, String rangeName) {
        var plugin = plugin(pluginId);
        var range = ApiTimeRange.parse(rangeName, System.currentTimeMillis());
        if (!storage.health().available())
            return new Detail(
                    plugin,
                    range.name(),
                    range.fromMs(),
                    range.toMs(),
                    false,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "历史存储不可用，无法读取插件诊断历史。");
        var routes =
                requests.findPluginRoutes(pluginId, range.fromMs(), range.toMs(), 10).stream()
                        .map(
                                route ->
                                        new RequestRouteItem(
                                                route.scope(),
                                                route.route(),
                                                route.method(),
                                                route.requestCount(),
                                                route.errorCount(),
                                                route.errorRate() * 100,
                                                route.averageDurationUs() / 1000,
                                                route.p95DurationUs() / 1000d,
                                                route.p99DurationUs() / 1000d,
                                                route.durationMaxUs() / 1000d))
                        .toList();
        return new Detail(
                plugin,
                range.name(),
                range.fromMs(),
                range.toMs(),
                true,
                consumers.findPlugin(pluginId, range.fromMs(), range.toMs()),
                repository.timeline(pluginId, range.fromMs(), range.toMs()),
                routes,
                repository.events(pluginId, range.fromMs(), range.toMs()),
                records.listForPlugin("optimization-observation", pluginId, Observation.class, 10),
                "资源只覆盖可安全归属的线程；分配量不等于持有堆。趋势最多 122 个汇总点，慢接口最多 10 项，事件最近 20 条，优化报告最近 10"
                    + " 条。共享线程池可能无法归属。");
    }

    PluginRuntimeInfo plugin(String pluginId) {
        return state.halo().plugins().stream()
                .filter(plugin -> plugin.name().equals(pluginId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("插件不存在，请刷新插件列表"));
    }

    public record Detail(
            PluginRuntimeInfo plugin,
            String range,
            long fromMs,
            long toMs,
            boolean available,
            PluginConsumerItem pressure,
            List<Point> timeline,
            List<RequestRouteItem> routes,
            List<Event> events,
            List<Observation> observations,
            String coverage) {}
}
