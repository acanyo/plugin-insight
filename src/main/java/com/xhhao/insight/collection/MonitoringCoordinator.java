package com.xhhao.insight.collection;

import com.xhhao.insight.alert.AlertEngine;
import com.xhhao.insight.diagnostics.AdvancedAlertSignals;
import com.xhhao.insight.config.CollectionSettings;
import com.xhhao.insight.config.InsightSettingsService;
import com.xhhao.insight.event.EventRecorder;
import com.xhhao.insight.event.RequestFailureEventBuffer;
import com.xhhao.insight.metrics.JvmSystemMetricsCollector;
import com.xhhao.insight.metrics.RequestMetric;
import com.xhhao.insight.metrics.RequestMetricsRegistry;
import com.xhhao.insight.metrics.RequestWindowSnapshot;
import com.xhhao.insight.optimization.OptimizationRuntimePolicy;
import com.xhhao.insight.resource.PluginResourceSampler;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.storage.capacity.HistoryWriteGate;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.repository.SystemMetricStorage;
import com.xhhao.insight.storage.repository.PluginResourceRepository;
import com.xhhao.insight.storage.retention.RetentionPolicy;
import com.xhhao.insight.storage.retention.RetentionService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MonitoringCoordinator {

    private static final long TICK_SECONDS = 5;
    private static final long RETENTION_INTERVAL_MS = Duration.ofMinutes(5).toMillis();

    private final JvmSystemMetricsCollector systemCollector;
    private final RequestMetricsRegistry requestRegistry;
    private final RollingRequestWindow requestWindow;
    private final SystemMetricStorage systemMetrics;
    private final PluginResourceSampler pluginResourceSampler;
    private final PluginResourceRepository pluginResources;
    private final CompletedMetricBuffer completedMetricBuffer;
    private final RetentionService retentionService;
    private final SqliteStorageLifecycle storage;
    private final HistoryWriteGate historyWriteGate;
    private final InsightSettingsService settingsService;
    private final HaloRuntimeRefreshSession haloRuntimeRefresh;
    private final MonitoringState state;
    private final EventRecorder events;
    private final RequestFailureEventBuffer requestFailureEvents;
    private final AlertEngine alertEngine;
    private final StorageRecoveryMonitor storageRecoveryMonitor;
    private final Clock clock;
    private final MonitoringSchedulerFactory schedulerFactory;
    private final OptimizationRuntimePolicy optimizationPolicy;
    private final AdvancedAlertSignals advancedSignals;

    private volatile ScheduledFuture<?> task;
    private ScheduledExecutorService executor;
    private long lastSystemSampleMs;
    private long lastHaloRefreshMs;
    private long lastRetentionMs;

    @Autowired
    public MonitoringCoordinator(
        JvmSystemMetricsCollector systemCollector,
        RequestMetricsRegistry requestRegistry,
        RollingRequestWindow requestWindow,
        SystemMetricStorage systemMetrics,
        PluginResourceSampler pluginResourceSampler,
        PluginResourceRepository pluginResources,
        CompletedMetricBuffer completedMetricBuffer,
        RetentionService retentionService,
        SqliteStorageLifecycle storage,
        HistoryWriteGate historyWriteGate,
        InsightSettingsService settingsService,
        HaloRuntimeRefreshSession haloRuntimeRefresh,
        MonitoringState state,
        EventRecorder events,
        RequestFailureEventBuffer requestFailureEvents,
        AlertEngine alertEngine,
        StorageRecoveryMonitor storageRecoveryMonitor,
        MonitoringSchedulerFactory schedulerFactory,
        OptimizationRuntimePolicy optimizationPolicy,
        AdvancedAlertSignals advancedSignals
    ) {
        this(systemCollector, requestRegistry, requestWindow, systemMetrics,
            pluginResourceSampler, pluginResources,
            completedMetricBuffer, retentionService, storage, historyWriteGate, settingsService,
            haloRuntimeRefresh, state, events, requestFailureEvents, alertEngine,
            storageRecoveryMonitor,
            schedulerFactory, optimizationPolicy, advancedSignals, Clock.systemUTC());
    }

    MonitoringCoordinator(
        JvmSystemMetricsCollector systemCollector,
        RequestMetricsRegistry requestRegistry,
        RollingRequestWindow requestWindow,
        SystemMetricStorage systemMetrics,
        PluginResourceSampler pluginResourceSampler,
        PluginResourceRepository pluginResources,
        CompletedMetricBuffer completedMetricBuffer,
        RetentionService retentionService,
        SqliteStorageLifecycle storage,
        HistoryWriteGate historyWriteGate,
        InsightSettingsService settingsService,
        HaloRuntimeRefreshSession haloRuntimeRefresh,
        MonitoringState state,
        EventRecorder events,
        RequestFailureEventBuffer requestFailureEvents,
        AlertEngine alertEngine,
        StorageRecoveryMonitor storageRecoveryMonitor,
        MonitoringSchedulerFactory schedulerFactory,
        OptimizationRuntimePolicy optimizationPolicy,
        AdvancedAlertSignals advancedSignals,
        Clock clock
    ) {
        this.systemCollector = systemCollector;
        this.requestRegistry = requestRegistry;
        this.requestWindow = requestWindow;
        this.systemMetrics = systemMetrics;
        this.pluginResourceSampler = pluginResourceSampler;
        this.pluginResources = pluginResources;
        this.completedMetricBuffer = completedMetricBuffer;
        this.retentionService = retentionService;
        this.storage = storage;
        this.historyWriteGate = historyWriteGate;
        this.settingsService = settingsService;
        this.haloRuntimeRefresh = haloRuntimeRefresh;
        this.state = state;
        this.events = events;
        this.requestFailureEvents = requestFailureEvents;
        this.alertEngine = alertEngine;
        this.storageRecoveryMonitor = storageRecoveryMonitor;
        this.schedulerFactory = schedulerFactory;
        this.optimizationPolicy = optimizationPolicy;
        this.advancedSignals = advancedSignals;
        this.clock = clock;
    }

    public synchronized void start() {
        if (task != null && !task.isDone()) {
            return;
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        executor = schedulerFactory.create();
        state.setRunning(true);
        pluginResourceSampler.start(clock.millis());
        haloRuntimeRefresh.start();
        try {
            task = executor.scheduleWithFixedDelay(this::safeTick, 0, TICK_SECONDS,
                TimeUnit.SECONDS);
        } catch (RuntimeException failure) {
            state.setRunning(false);
            pluginResourceSampler.stop(clock.millis());
            haloRuntimeRefresh.stop();
            executor.shutdownNow();
            executor = null;
            throw failure;
        }
    }

    public synchronized void stop() {
        state.setRunning(false);
        pluginResourceSampler.stop(clock.millis());
        haloRuntimeRefresh.stop();
        ScheduledExecutorService activeExecutor = executor;
        if (activeExecutor == null) {
            return;
        }
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        flushForShutdown();
        activeExecutor.shutdown();
        try {
            if (!activeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                activeExecutor.shutdownNow();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            activeExecutor.shutdownNow();
        } finally {
            executor = null;
        }
    }

    private void safeTick() {
        try {
            tick();
        } catch (RuntimeException error) {
            if (state.collectionFailed(error)) {
                events.recordFailure("collector", "warning", "system", "监控采集任务异常",
                    "系统与 JVM 指标本轮未完成采集，将按计划重试",
                    "monitoring_collection_tick", error);
            }
            log.warn("Insight collection tick failed", error);
        }
    }

    private void flushForShutdown() {
        try {
            flushRequestFailureEvents();
            List<RequestMetric> pending = requestRegistry.drainForShutdown();
            retainCompletedMetrics(pending);
            while (canWriteHistory() && completedMetricBuffer.pendingCount() > 0) {
                updateCapacityState();
                if (!canWriteHistory()) {
                    break;
                }
                completedMetricBuffer.flush();
            }
        } catch (RuntimeException error) {
            log.warn("Failed to flush Insight request metrics during shutdown", error);
        } finally {
            long dropped = completedMetricBuffer.discardPending();
            if (dropped > 0) {
                log.warn("Dropped {} buffered Insight metric aggregates during shutdown", dropped);
            }
        }
    }

    private void tick() {
        long now = clock.millis();
        if (storageRecoveryMonitor.recheckIfDue(now)) {
            state.setStorageWritePaused(false);
            log.info("Insight SQLite storage recovered after runtime degradation");
        }
        flushRequestFailureEvents();
        CollectionSettings settings = settingsService.collection();
        List<RequestMetric> completed = requestRegistry.drainCompleted();
        requestWindow.addCompleted(completed);
        retainCompletedMetrics(completed);
        RequestWindowSnapshot requestSnapshot = requestWindow.snapshot(
            requestRegistry.currentSnapshot());
        state.updateRequests(requestSnapshot);
        if (storage.health().available() && completedMetricBuffer.pendingCount() > 0) {
            updateCapacityState();
        }
        if (canWriteHistory()) {
            completedMetricBuffer.flush();
        }
        if (!state.running()) {
            return;
        }

        if (now - lastSystemSampleMs >= optimizationPolicy.sampleIntervalMs(settings)) {
            var snapshot = systemCollector.collect();
            var pluginResourceSample = pluginResourceSampler.sample(
                now, settings.isPluginResourceSamplingEnabled(),
                optimizationPolicy.pluginResourceMaxThreads(settings));
            if (state.updateSystem(snapshot)) {
                events.record("collector", "info", "system", "监控采集任务已恢复",
                    "系统与 JVM 指标采集恢复正常");
            }
            alertEngine.evaluate(advancedSignals.enrich(AlertEvaluationInputMapper.from(snapshot, requestSnapshot)),
                settingsService.alerts());
            lastSystemSampleMs = now;
            updateCapacityState();
            if (canWriteHistory()) {
                systemMetrics.save(MetricStorageMapper.toSystemSample(snapshot));
                pluginResourceSample.ifPresent(sample -> pluginResources.save(
                    PluginResourceStorageMapper.toPluginMetrics(sample),
                    PluginResourceStorageMapper.toCoverage(sample)));
            }
        }
        if (now - lastHaloRefreshMs >= Duration.ofMinutes(1).toMillis()) {
            refreshHaloRuntime();
            lastHaloRefreshMs = now;
        }
        if (now - lastRetentionMs >= RETENTION_INTERVAL_MS
            && storage.health().available()) {
            try {
                runRetention(now, settings);
                if (state.retentionSucceeded(clock.millis())) {
                    events.record("retention", "info", "sqlite", "历史清理任务已恢复",
                        "过期监控数据清理恢复正常");
                }
            } catch (RuntimeException error) {
                if (state.retentionFailed(clock.millis(), error)) {
                    events.recordFailure("retention", "warning", "sqlite", "历史清理任务异常",
                        "过期监控数据本轮未完成清理，将按计划重试",
                        "monitoring_history_retention", error);
                }
                log.warn("Failed to remove expired Insight history", error);
            }
            lastRetentionMs = now;
        }
    }

    private void updateCapacityState() {
        boolean wasPaused = state.storageWritePaused();
        historyWriteGate.allowsWrite();
        boolean isPaused = state.storageWritePaused();
        if (isPaused && !wasPaused) {
            long dropped = completedMetricBuffer.discardPending();
            if (dropped > 0) {
                log.warn("Dropped {} buffered Insight metric aggregates after reaching the "
                    + "history capacity limit", dropped);
            }
            events.record("storage", "warning", "sqlite", "历史写入已暂停",
                "Insight 数据库达到容量上限，实时监控继续运行");
        } else if (!isPaused && wasPaused) {
            events.record("storage", "info", "sqlite", "历史写入已恢复",
                "Insight 数据库已低于容量上限");
        }
    }

    private void refreshHaloRuntime() {
        haloRuntimeRefresh.refresh();
    }

    private void flushRequestFailureEvents() {
        if (storage.health().available() && canWriteHistory()) {
            events.recordBatch(requestFailureEvents.drain());
        }
    }

    private void runRetention(long now, CollectionSettings settings) {
        RetentionPolicy policy = RetentionPolicy.fromDurations(
            now,
            Duration.ofDays(clampDays(settings.getSystemRetentionDays(), 1, 30)),
            Duration.ofDays(clampDays(settings.getAggregateRetentionDays(), 7, 180)),
            Duration.ofDays(clampDays(settings.getAggregateRetentionDays(), 7, 180)),
            Duration.ofDays(clampDays(settings.getAggregateRetentionDays(), 7, 180)),
            Duration.ofDays(clampDays(settings.getEventRetentionDays(), 7, 365)),
            Duration.ofDays(clampDays(settings.getEventRetentionDays(), 7, 365))
        );
        for (int batch = 0; batch < 20; batch++) {
            var result = retentionService.deleteExpiredBatch(policy, 500);
            if (!result.mayHaveMore(500)) {
                break;
            }
        }
    }

    private boolean canWriteHistory() {
        return historyWriteGate.allowsWrite();
    }

    private void retainCompletedMetrics(List<RequestMetric> metrics) {
        if (metrics.isEmpty()) {
            return;
        }
        long dropped = state.storageWritePaused()
            ? completedMetricBuffer.discard(metrics.size())
            : completedMetricBuffer.enqueue(metrics);
        if (dropped > 0) {
            log.warn("Dropped {} Insight metric aggregates because history buffering reached its "
                + "limit or capacity-based writes were paused", dropped);
        }
    }

    private static int clampDays(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

}
