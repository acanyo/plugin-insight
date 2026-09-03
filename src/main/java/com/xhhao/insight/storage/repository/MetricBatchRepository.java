package com.xhhao.insight.storage.repository;

import com.xhhao.insight.storage.database.SqliteConnectionManager;
import com.xhhao.insight.storage.model.PluginMetricMinute;
import com.xhhao.insight.storage.model.RequestMetricMinute;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Persists the request and plugin projections of one completed metrics batch atomically.
 */
@Repository
public class MetricBatchRepository {

    private final SqliteConnectionManager connectionManager;
    private final RequestMetricRepository requestMetrics;
    private final PluginMetricRepository pluginMetrics;

    public MetricBatchRepository(
        SqliteConnectionManager connectionManager,
        RequestMetricRepository requestMetrics,
        PluginMetricRepository pluginMetrics
    ) {
        this.connectionManager = connectionManager;
        this.requestMetrics = requestMetrics;
        this.pluginMetrics = pluginMetrics;
    }

    public void save(
        List<RequestMetricMinute> requestBatch,
        List<PluginMetricMinute> pluginBatch
    ) {
        if (requestBatch.isEmpty() && pluginBatch.isEmpty()) {
            return;
        }
        connectionManager.write(connection -> {
            requestMetrics.saveBatch(connection, requestBatch);
            pluginMetrics.saveBatch(connection, pluginBatch);
            return null;
        });
    }
}
