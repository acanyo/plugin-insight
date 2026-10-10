package com.xhhao.insight.optimization;

import com.xhhao.insight.api.model.OptimizationStatus;
import com.xhhao.insight.api.model.OptimizationStatus.OptimizationChange;
import com.xhhao.insight.config.CollectionSettings;
import com.xhhao.insight.config.InsightSettingsService;
import com.xhhao.insight.consumer.PluginConsumerItem;
import com.xhhao.insight.consumer.PluginConsumerService;
import com.xhhao.insight.event.EventRecorder;
import com.xhhao.insight.metrics.SystemSnapshot;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.storage.StorageException;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Coordinates runtime diagnosis with the optional Insight-only low-overhead profile. */
@Component
public class OptimizationService {

    private static final Duration CONSUMER_WINDOW = Duration.ofHours(1);

    private final OptimizationRuntimePolicy runtimePolicy;
    private final StorageOptimizationService storageOptimization;
    private final SqliteStorageLifecycle storage;
    private final InsightSettingsService settings;
    private final MonitoringState monitoringState;
    private final PluginConsumerService consumers;
    private final RuntimeDiagnosisService runtimeDiagnosis;
    private final EventRecorder events;
    private final Clock clock;

    @Autowired
    public OptimizationService(
        OptimizationRuntimePolicy runtimePolicy,
        StorageOptimizationService storageOptimization,
        SqliteStorageLifecycle storage,
        InsightSettingsService settings,
        MonitoringState monitoringState,
        PluginConsumerService consumers,
        RuntimeDiagnosisService runtimeDiagnosis,
        EventRecorder events
    ) {
        this(runtimePolicy, storageOptimization, storage, settings, monitoringState, consumers,
            runtimeDiagnosis, events, Clock.systemUTC());
    }

    OptimizationService(
        OptimizationRuntimePolicy runtimePolicy,
        StorageOptimizationService storageOptimization,
        SqliteStorageLifecycle storage,
        InsightSettingsService settings,
        MonitoringState monitoringState,
        PluginConsumerService consumers,
        RuntimeDiagnosisService runtimeDiagnosis,
        EventRecorder events,
        Clock clock
    ) {
        this.runtimePolicy = runtimePolicy;
        this.storageOptimization = storageOptimization;
        this.storage = storage;
        this.settings = settings;
        this.monitoringState = monitoringState;
        this.consumers = consumers;
        this.runtimeDiagnosis = runtimeDiagnosis;
        this.events = events;
        this.clock = clock;
    }

    public OptimizationStatus status() {
        return buildStatus(runtimePolicy.snapshot());
    }

    public OptimizationStatus apply() {
        requireStorage();
        long now = clock.millis();
        OptimizationProfile previous = runtimePolicy.snapshot();
        OptimizationProfile updated = runtimePolicy.enable(now);
        storageOptimization.optimize();
        if (!previous.enabled()) {
            events.record("optimization", "info", "insight", "已降低运行洞察监控开销",
                "运行洞察已降低自身采样频率和插件线程扫描上限；Halo 与其他插件配置未被修改");
        }
        return buildStatus(updated);
    }

    public OptimizationStatus rollback() {
        requireStorage();
        OptimizationProfile previous = runtimePolicy.snapshot();
        OptimizationProfile updated = runtimePolicy.disable(clock.millis());
        if (previous.enabled()) {
            events.record("optimization", "info", "insight", "已恢复运行洞察标准采集",
                "运行洞察已恢复设置中配置的采样频率和插件线程扫描上限");
        }
        return buildStatus(updated);
    }

    private OptimizationStatus buildStatus(OptimizationProfile profile) {
        CollectionSettings configured = settings.collection();
        SystemSnapshot system = monitoringState.system();
        PluginConsumerItem topConsumer = topConsumer();
        RuntimeDiagnosis diagnosis = runtimeDiagnosis.analyze(system, topConsumer);
        boolean storageAvailable = storage.health().available();
        List<OptimizationChange> changes = List.of(
            new OptimizationChange(
                "sampling-interval", "降低采样频率",
                "系统和 JVM 指标采样间隔最低调整为 30 秒，减少监控线程唤醒与写入。",
                true, profile.enabled()),
            new OptimizationChange(
                "plugin-thread-scan", "限制插件线程扫描",
                "每轮插件资源归因最多扫描 256 个线程；超出时会明确降低结果置信度。",
                true, profile.enabled()),
            new OptimizationChange(
                "sqlite-planner", "维护监控数据库",
                "更新 SQLite 查询规划统计，不删除有效历史数据。",
                true, profile.enabled())
        );
        return new OptimizationStatus(
            profile.enabled() ? "low-overhead" : "standard", profile.enabled(),
            storageAvailable, profile.appliedAtMs(), profile.updatedAtMs(),
            configured.getSamplingSeconds(), runtimePolicy.effectiveSamplingSeconds(configured),
            runtimePolicy.optimizedSamplingSeconds(configured),
            configured.getPluginResourceMaxThreads(),
            runtimePolicy.pluginResourceMaxThreads(configured),
            runtimePolicy.optimizedPluginThreadLimit(configured),
            configured.isPluginResourceSamplingEnabled(), diagnosis,
            changes, topConsumer);
    }

    private PluginConsumerItem topConsumer() {
        if (!storage.health().available()) {
            return null;
        }
        long now = clock.millis();
        return consumers.find(Math.max(0, now - CONSUMER_WINDOW.toMillis()), now).stream()
            .findFirst().orElse(null);
    }

    private void requireStorage() {
        if (!storage.health().available()) {
            throw new StorageException("Optimization state requires available SQLite storage");
        }
    }
}
