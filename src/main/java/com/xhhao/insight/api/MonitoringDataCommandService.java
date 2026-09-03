package com.xhhao.insight.api;

import com.xhhao.insight.api.model.ClearDataResponse;
import com.xhhao.insight.alert.AlertEngine;
import com.xhhao.insight.event.EventRecorder;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.repository.AlertIncidentRepository;
import com.xhhao.insight.storage.repository.MonitorEventRepository;
import com.xhhao.insight.storage.repository.PluginMetricRepository;
import com.xhhao.insight.storage.repository.PluginResourceRepository;
import com.xhhao.insight.storage.repository.RequestMetricRepository;
import com.xhhao.insight.storage.retention.RetentionService;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Deletes an explicitly selected monitoring history projection. */
@Component
public class MonitoringDataCommandService {

    private static final Set<String> SUPPORTED_SCOPES = Set.of(
        "all", "system", "requests", "plugins", "events", "alerts");

    private final SqliteStorageLifecycle storage;
    private final RetentionService retention;
    private final RequestMetricRepository requestMetrics;
    private final PluginMetricRepository pluginMetrics;
    private final PluginResourceRepository pluginResources;
    private final MonitorEventRepository events;
    private final AlertIncidentRepository alerts;
    private final AlertEngine alertEngine;
    private final EventRecorder eventRecorder;

    /**
     * Creates the command service with access to the live alert evaluator.
     */
    @Autowired
    public MonitoringDataCommandService(
        SqliteStorageLifecycle storage,
        RetentionService retention,
        RequestMetricRepository requestMetrics,
        PluginMetricRepository pluginMetrics,
        PluginResourceRepository pluginResources,
        MonitorEventRepository events,
        AlertIncidentRepository alerts,
        EventRecorder eventRecorder,
        AlertEngine alertEngine
    ) {
        this.storage = storage;
        this.retention = retention;
        this.requestMetrics = requestMetrics;
        this.pluginMetrics = pluginMetrics;
        this.pluginResources = pluginResources;
        this.events = events;
        this.alerts = alerts;
        this.eventRecorder = eventRecorder;
        this.alertEngine = alertEngine;
    }

    /**
     * Compatibility constructor for integrations that only use non-alert data scopes.
     */
    public MonitoringDataCommandService(
        SqliteStorageLifecycle storage,
        RetentionService retention,
        RequestMetricRepository requestMetrics,
        PluginMetricRepository pluginMetrics,
        PluginResourceRepository pluginResources,
        MonitorEventRepository events,
        AlertIncidentRepository alerts,
        EventRecorder eventRecorder
    ) {
        this(storage, retention, requestMetrics, pluginMetrics, pluginResources, events, alerts,
            eventRecorder, null);
    }

    public ClearDataResponse clearData(String requestedScope) {
        String scope = normalizeScope(requestedScope);
        if (!storage.health().available()) {
            throw new IllegalStateException("Monitoring storage is unavailable");
        }
        int deleted = switch (scope) {
            case "all" -> {
                // Serialize the bulk delete with alert evaluation so no new incident can be
                // persisted between deleting the rows and resetting the in-memory evaluator.
                synchronized (alertEngine == null ? this : alertEngine) {
                    int cleared = retention.clearAllData().totalDeleted();
                    clearLiveAlertState();
                    yield cleared;
                }
            }
            case "system" -> retention.clearSystemData();
            case "requests" -> requestMetrics.deleteAll();
            case "plugins" -> pluginMetrics.deleteAll() + pluginResources.deleteAll();
            case "events" -> events.deleteAll();
            case "alerts" -> {
                if (alertEngine == null) {
                    int cleared = alerts.deleteAll();
                    clearLiveAlertState();
                    yield cleared;
                }
                yield alertEngine.clearIncidentHistory();
            }
            default -> throw new IllegalArgumentException("Unsupported data scope: " + scope);
        };
        eventRecorder.record("data", "info", "sqlite", "监控数据已清理",
            "清理范围 " + scope + "，共删除 " + deleted + " 行");
        return new ClearDataResponse(scope, deleted);
    }

    private void clearLiveAlertState() {
        if (alertEngine != null) {
            alertEngine.clearIncidentState();
        }
    }

    private static String normalizeScope(String value) {
        String scope = value == null || value.isBlank()
            ? "all" : value.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_SCOPES.contains(scope)) {
            throw new IllegalArgumentException("Unsupported data scope: " + scope);
        }
        return scope;
    }
}
