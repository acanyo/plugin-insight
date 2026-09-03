package com.xhhao.insight.api;

import com.xhhao.insight.alert.AlertEngine;
import com.xhhao.insight.api.model.OverviewResponse;
import com.xhhao.insight.config.InsightSettingsService;
import com.xhhao.insight.halo.DependencyStatus;
import com.xhhao.insight.metrics.SystemSnapshot;
import com.xhhao.insight.runtime.BackgroundTaskStatus;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.capacity.StorageCapacityGuard;
import com.xhhao.insight.storage.model.AlertIncident;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Assembles the current runtime overview from live state and storage health. */
@Component
public class OverviewQueryService {

    private static final long MEGABYTE = 1_024L * 1_024L;

    private final MonitoringState state;
    private final SqliteStorageLifecycle storage;
    private final StorageCapacityGuard capacityGuard;
    private final InsightSettingsService settings;
    private final AlertEngine alertEngine;
    private final Clock clock;

    @Autowired
    public OverviewQueryService(
        MonitoringState state,
        SqliteStorageLifecycle storage,
        StorageCapacityGuard capacityGuard,
        InsightSettingsService settings,
        AlertEngine alertEngine
    ) {
        this(state, storage, capacityGuard, settings, alertEngine, Clock.systemUTC());
    }

    OverviewQueryService(
        MonitoringState state,
        SqliteStorageLifecycle storage,
        StorageCapacityGuard capacityGuard,
        InsightSettingsService settings,
        AlertEngine alertEngine,
        Clock clock
    ) {
        this.state = state;
        this.storage = storage;
        this.capacityGuard = capacityGuard;
        this.settings = settings;
        this.alertEngine = alertEngine;
        this.clock = clock;
    }

    public OverviewResponse overview() {
        long now = clock.millis();
        SystemSnapshot system = state.system();
        var requests = state.requests();
        var storageHealth = storage.health();
        var halo = state.halo();
        List<AlertIncident> activeAlerts = alertEngine.activeIncidents();
        long diskUsed = system == null ? 0
            : Math.max(0, system.diskTotalBytes() - system.diskFreeBytes());

        var runtime = new OverviewResponse.RuntimeView(
            state.running(), system == null ? 0 : system.uptimeMs(), halo.haloVersion(),
            System.getProperty("java.version", "unknown"),
            System.getProperty("os.name", "unknown"),
            system == null ? 0 : system.processCpuPercent(),
            system == null ? 0 : system.systemCpuPercent(),
            system == null ? 0 : system.systemMemoryUsedBytes(),
            system == null ? 0 : system.systemMemoryTotalBytes(), diskUsed,
            system == null ? 0 : system.diskTotalBytes(), state.lastCollectedAtMs(),
            state.lastCollectionError()
        );
        var jvm = new OverviewResponse.JvmView(
            system == null ? 0 : system.heapUsedBytes(),
            system == null ? 0 : system.heapMaxBytes(),
            system == null ? 0 : system.heapUsedPercent(),
            system == null ? 0 : system.nonHeapUsedBytes(),
            system == null ? 0 : system.gcCount(), system == null ? 0 : system.gcTimeMs(),
            system == null ? 0 : system.threadCount(),
            system == null ? 0 : system.daemonThreadCount(),
            system == null ? 0 : system.peakThreadCount(),
            system == null ? 0 : system.deadlockedThreadCount()
        );
        Map<String, Long> statusCodes = requests.statusGroups().entrySet().stream()
            .collect(Collectors.toMap(entry -> entry.getKey() + "xx", Map.Entry::getValue,
                Long::sum, LinkedHashMap::new));
        var requestView = new OverviewResponse.RequestView(
            requests.fromMs(), requests.toMs(), requests.requestCount(), requests.errorCount(),
            requests.errorRatePercent(), requests.averageLatencyMs(), requests.p95Ms(),
            requests.p99Ms(), requests.durationMaxMs(), statusCodes
        );
        int maxMb = Math.max(1, settings.collection().getMaxDatabaseMegabytes());
        var storageView = new OverviewResponse.StorageView(
            storageHealth.state().name().toLowerCase(Locale.ROOT), storageHealth.message(),
            storageHealth.available(), state.storageWritePaused(), capacityGuard.currentBytes(),
            maxMb * MEGABYTE, storageHealth.checkedAtMs(), storageHealth.failureCount()
        );
        List<DependencyStatus> dependencies = dependencies(now);
        return new OverviewResponse(now,
            health(activeAlerts, storageHealth.available(), system, dependencies),
            runtime, jvm, requestView, storageView, dependencies, halo.plugins(), activeAlerts);
    }

    private List<DependencyStatus> dependencies(long now) {
        List<DependencyStatus> dependencies = new ArrayList<>(state.halo().dependencies());
        long staleAfterMs = Math.max(15_000,
            settings.collection().getSamplingSeconds() * 3_000L);
        if (state.running() && state.lastCollectedAtMs() > 0
            && now - state.lastCollectedAtMs() <= staleAfterMs) {
            dependencies.add(DependencyStatus.healthy(
                "collector", "监控采集任务", "采样任务运行正常"));
        } else {
            dependencies.add(DependencyStatus.critical(
                "collector", "监控采集任务", "采样任务未运行或数据已过期"));
        }
        dependencies.add(taskDependency(
            "halo-runtime-refresh", "Halo 运行状态刷新", state.haloRefreshStatus(), now,
            Duration.ofMinutes(3).toMillis()));
        dependencies.add(taskDependency(
            "history-retention", "历史清理任务", state.retentionStatus(), now,
            Duration.ofMinutes(15).toMillis()));
        return dependencies;
    }

    private String health(
        List<AlertIncident> active,
        boolean storageAvailable,
        SystemSnapshot system,
        List<DependencyStatus> dependencies
    ) {
        if (!state.running() || system != null && system.deadlockedThreadCount() > 0
            || active.stream().anyMatch(alert -> "critical".equalsIgnoreCase(alert.severity()))
            || dependencies.stream()
                .anyMatch(item -> "critical".equalsIgnoreCase(item.status()))) {
            return "critical";
        }
        if (!storageAvailable || state.storageWritePaused() || !active.isEmpty()
            || dependencies.stream().anyMatch(item -> !"healthy".equalsIgnoreCase(item.status()))) {
            return "warning";
        }
        return "healthy";
    }

    private static DependencyStatus taskDependency(
        String key,
        String name,
        BackgroundTaskStatus status,
        long now,
        long staleAfterMs
    ) {
        if (status.failed()) {
            return DependencyStatus.warning(key, name, "最近执行失败：" + status.lastError());
        }
        if (status.pendingFirstRun()) {
            return DependencyStatus.warning(key, name, "等待首次执行");
        }
        if (now - status.lastSuccessAtMs() > staleAfterMs) {
            return DependencyStatus.warning(key, name, "最近一次成功执行已超过预期周期");
        }
        return DependencyStatus.healthy(key, name, "任务按计划运行");
    }
}
