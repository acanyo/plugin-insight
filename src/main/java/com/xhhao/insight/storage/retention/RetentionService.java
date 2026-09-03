package com.xhhao.insight.storage.retention;

import com.xhhao.insight.storage.database.SqliteConnectionManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.stereotype.Component;

@Component
public class RetentionService {

    private final SqliteConnectionManager connectionManager;

    public RetentionService(SqliteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Deletes at most {@code batchSize} rows from each data domain in one short transaction.
     */
    public RetentionResult deleteExpiredBatch(RetentionPolicy policy, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return connectionManager.write(connection -> new RetentionResult(
            deleteByCutoff(connection, "system_sample_15s", "sampled_at_ms",
                policy.systemSamplesBeforeMs(), batchSize, null),
            deleteByCutoff(connection, "system_metric_minute", "bucket_start_ms",
                policy.systemMetricsBeforeMs(), batchSize, null),
            deleteByCutoff(connection, "plugin_metric_minute", "bucket_start_ms",
                policy.pluginMetricsBeforeMs(), batchSize, null),
            deleteByCutoff(connection, "plugin_resource_minute", "bucket_start_ms",
                policy.pluginMetricsBeforeMs(), batchSize, null),
            deleteByCutoff(connection, "resource_attribution_minute", "bucket_start_ms",
                policy.pluginMetricsBeforeMs(), batchSize, null),
            deleteByCutoff(connection, "request_metric_minute", "bucket_start_ms",
                policy.requestMetricsBeforeMs(), batchSize, null),
            deleteByCutoff(connection, "monitor_event", "occurred_at_ms",
                policy.eventsBeforeMs(), batchSize, null),
            deleteByCutoff(connection, "alert_incident", "resolved_at_ms",
                policy.resolvedAlertsBeforeMs(), batchSize, "resolved_at_ms IS NOT NULL")
        ));
    }

    /**
     * Explicitly clears historical monitoring data while preserving the schema history.
     */
    public RetentionResult clearAllData() {
        return connectionManager.write(connection -> new RetentionResult(
            deleteAll(connection, "system_sample_15s"),
            deleteAll(connection, "system_metric_minute"),
            deleteAll(connection, "plugin_metric_minute"),
            deleteAll(connection, "plugin_resource_minute"),
            deleteAll(connection, "resource_attribution_minute"),
            deleteAll(connection, "request_metric_minute"),
            deleteAll(connection, "monitor_event"),
            deleteAll(connection, "alert_incident")
        ));
    }

    /** Clears both raw and minute-level system history atomically. */
    public int clearSystemData() {
        return connectionManager.write(connection ->
            deleteAll(connection, "system_sample_15s")
                + deleteAll(connection, "system_metric_minute"));
    }

    private static int deleteByCutoff(
        java.sql.Connection connection,
        String table,
        String timeColumn,
        long cutoffMs,
        int batchSize,
        String additionalCondition
    ) throws SQLException {
        String condition = additionalCondition == null
            ? timeColumn + " < ?"
            : additionalCondition + " AND " + timeColumn + " < ?";
        String sql = "DELETE FROM " + table + " WHERE rowid IN (SELECT rowid FROM "
            + table + " WHERE " + condition + " ORDER BY " + timeColumn + " ASC LIMIT ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cutoffMs);
            statement.setInt(2, batchSize);
            return statement.executeUpdate();
        }
    }

    private static int deleteAll(java.sql.Connection connection, String table)
        throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate("DELETE FROM " + table);
        }
    }
}
