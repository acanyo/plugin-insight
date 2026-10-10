package com.xhhao.insight;

import com.xhhao.insight.collection.MonitoringCoordinator;
import com.xhhao.insight.diagnostics.DiagnosticsLifecycle;
import com.xhhao.insight.notification.NotificationDispatcher;
import com.xhhao.insight.event.EventRecorder;
import com.xhhao.insight.event.HaloApplicationLifecycleObserver;
import com.xhhao.insight.event.HaloConfigMapEventWatcher;
import com.xhhao.insight.event.HaloPluginEventWatcher;
import com.xhhao.insight.notification.AcknowledgedNotificationReconciler;
import com.xhhao.insight.optimization.OptimizationRuntimePolicy;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.overhead.InsightOverheadMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * <p>Plugin main class to manage the lifecycle of the plugin.</p>
 * <p>This class must be public and have a public constructor.</p>
 * <p>Only one main class extending {@link BasePlugin} is allowed per plugin.</p>
 *
 * @author Handsome
 * @since 1.0.0
 */
@Slf4j
@Component
public class InsightPlugin extends BasePlugin {

    private final SqliteStorageLifecycle storage;
    private final MonitoringCoordinator coordinator;
    private final EventRecorder events;
    private final HaloPluginEventWatcher pluginEventWatcher;
    private final HaloConfigMapEventWatcher configMapEventWatcher;
    private final HaloApplicationLifecycleObserver haloLifecycleObserver;
    private final AcknowledgedNotificationReconciler notificationReconciler;
    private final OptimizationRuntimePolicy optimizationPolicy;
    private final DiagnosticsLifecycle diagnostics;
    private final NotificationDispatcher notificationDispatcher;
    private InsightOverheadMetrics overhead;

    @Autowired
    void measureOverhead(InsightOverheadMetrics overhead) { this.overhead = overhead; }

    public InsightPlugin(PluginContext pluginContext, SqliteStorageLifecycle storage,
        MonitoringCoordinator coordinator, EventRecorder events,
        HaloPluginEventWatcher pluginEventWatcher,
        HaloConfigMapEventWatcher configMapEventWatcher,
        HaloApplicationLifecycleObserver haloLifecycleObserver,
        AcknowledgedNotificationReconciler notificationReconciler,
        OptimizationRuntimePolicy optimizationPolicy, DiagnosticsLifecycle diagnostics,
        NotificationDispatcher notificationDispatcher) {
        super(pluginContext);
        this.storage = storage;
        this.coordinator = coordinator;
        this.events = events;
        this.pluginEventWatcher = pluginEventWatcher;
        this.configMapEventWatcher = configMapEventWatcher;
        this.haloLifecycleObserver = haloLifecycleObserver;
        this.notificationReconciler = notificationReconciler;
        this.optimizationPolicy = optimizationPolicy;
        this.diagnostics = diagnostics;
        this.notificationDispatcher = notificationDispatcher;
    }

    @Override
    public void start() {
        if (overhead != null) overhead.reset();
        boolean storageAvailable = storage.initialize();
        optimizationPolicy.initialize(storageAvailable);
        notificationDispatcher.start();
        diagnostics.start();
        notificationReconciler.reconcile();
        haloLifecycleObserver.start();
        pluginEventWatcher.start();
        configMapEventWatcher.start();
        coordinator.start();
        events.record("lifecycle", "info", "insight", "Insight 监控组件已初始化",
            storageAvailable ? "监控采集与 SQLite 历史存储均已启用"
                : "SQLite 不可用，监控已切换为仅实时模式");
        log.info("Halo Insight started; SQLite available={}", storageAvailable);
    }

    @Override
    public void stop() {
        coordinator.stop();
        diagnostics.stop();
        notificationDispatcher.destroy();
        configMapEventWatcher.dispose();
        pluginEventWatcher.dispose();
        haloLifecycleObserver.stop();
        events.record("lifecycle", "info", "insight", "Insight 监控组件已停止",
            "Insight 自身的监控采集已停止；此事件不代表 Halo 应用关闭");
        storage.close();
        log.info("Halo Insight stopped");
    }
}
