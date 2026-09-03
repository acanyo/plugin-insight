package com.xhhao.insight.storage.repository;

import com.xhhao.insight.storage.database.SqliteConnectionManager;
import com.xhhao.insight.storage.model.PluginMetricMinute;
import com.xhhao.insight.storage.model.PluginMetricSort;
import com.xhhao.insight.storage.model.PluginMetricSummary;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PluginMetricRepository {

    private static final String UPSERT_SQL = """
        INSERT INTO plugin_metric_minute(
            bucket_start_ms, plugin_id, sample_count, cpu_active_samples,
            active_thread_samples, live_threads_max, request_count, request_error_count,
            request_duration_sum_us, request_duration_max_us, task_count, task_error_count,
            task_duration_sum_us, task_duration_max_us
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(bucket_start_ms, plugin_id) DO UPDATE SET
            sample_count=sample_count + excluded.sample_count,
            cpu_active_samples=cpu_active_samples + excluded.cpu_active_samples,
            active_thread_samples=active_thread_samples + excluded.active_thread_samples,
            live_threads_max=MAX(live_threads_max, excluded.live_threads_max),
            request_count=request_count + excluded.request_count,
            request_error_count=request_error_count + excluded.request_error_count,
            request_duration_sum_us=request_duration_sum_us + excluded.request_duration_sum_us,
            request_duration_max_us=MAX(request_duration_max_us, excluded.request_duration_max_us),
            task_count=task_count + excluded.task_count,
            task_error_count=task_error_count + excluded.task_error_count,
            task_duration_sum_us=task_duration_sum_us + excluded.task_duration_sum_us,
            task_duration_max_us=MAX(task_duration_max_us, excluded.task_duration_max_us)
        """;

    private final SqliteConnectionManager connectionManager;

    public PluginMetricRepository(SqliteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void saveBatch(List<PluginMetricMinute> metrics) {
        if (metrics.isEmpty()) {
            return;
        }
        connectionManager.write(connection -> {
            saveBatch(connection, metrics);
            return null;
        });
    }

    void saveBatch(Connection connection, List<PluginMetricMinute> metrics) throws SQLException {
        if (metrics.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            for (PluginMetricMinute metric : metrics) {
                bind(statement, metric);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public List<PluginMetricMinute> findRange(
        long fromInclusiveMs,
        long toExclusiveMs,
        int limit
    ) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs, limit);
        return connectionManager.read(connection -> {
            List<PluginMetricMinute> metrics = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM plugin_metric_minute
                WHERE bucket_start_ms >= ? AND bucket_start_ms < ?
                ORDER BY bucket_start_ms ASC, plugin_id ASC
                LIMIT ?
                """)) {
                statement.setLong(1, fromInclusiveMs);
                statement.setLong(2, toExclusiveMs);
                statement.setInt(3, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        metrics.add(mapMetric(resultSet));
                    }
                }
            }
            return metrics;
        });
    }

    public List<PluginMetricSummary> findRanking(
        long fromInclusiveMs,
        long toExclusiveMs,
        PluginMetricSort sort,
        int limit
    ) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs, limit);
        String orderExpression = switch (sort) {
            case CPU_ACTIVE_SAMPLES -> "SUM(cpu_active_samples)";
            case ACTIVE_THREAD_SAMPLES -> "SUM(active_thread_samples)";
            case REQUEST_COUNT -> "SUM(request_count)";
            case REQUEST_ERROR_COUNT -> "SUM(request_error_count)";
            case REQUEST_DURATION -> "SUM(request_duration_sum_us)";
            case AVERAGE_REQUEST_DURATION ->
                "CASE WHEN SUM(request_count)=0 THEN 0 ELSE "
                    + "CAST(SUM(request_duration_sum_us) AS REAL)/SUM(request_count) END";
            case TASK_DURATION -> "SUM(task_duration_sum_us)";
        };
        String sql = """
            SELECT plugin_id,
                   SUM(sample_count) AS sample_count,
                   SUM(cpu_active_samples) AS cpu_active_samples,
                   SUM(active_thread_samples) AS active_thread_samples,
                   MAX(live_threads_max) AS live_threads_max,
                   SUM(request_count) AS request_count,
                   SUM(request_error_count) AS request_error_count,
                   SUM(request_duration_sum_us) AS request_duration_sum_us,
                   MAX(request_duration_max_us) AS request_duration_max_us,
                   SUM(task_count) AS task_count,
                   SUM(task_error_count) AS task_error_count,
                   SUM(task_duration_sum_us) AS task_duration_sum_us,
                   MAX(task_duration_max_us) AS task_duration_max_us
            FROM plugin_metric_minute
            WHERE bucket_start_ms >= ? AND bucket_start_ms < ?
            GROUP BY plugin_id
            ORDER BY %s DESC, plugin_id ASC
            LIMIT ?
            """.formatted(orderExpression);
        return connectionManager.read(connection -> {
            List<PluginMetricSummary> summaries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, fromInclusiveMs);
                statement.setLong(2, toExclusiveMs);
                statement.setInt(3, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        summaries.add(mapSummary(resultSet));
                    }
                }
            }
            return summaries;
        });
    }

    public long totalRequestDuration(long fromInclusiveMs, long toExclusiveMs) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs);
        return connectionManager.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(SUM(request_duration_sum_us), 0) AS total_duration_us
                FROM plugin_metric_minute
                WHERE bucket_start_ms >= ? AND bucket_start_ms < ?
                """)) {
                statement.setLong(1, fromInclusiveMs);
                statement.setLong(2, toExclusiveMs);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getLong("total_duration_us") : 0L;
                }
            }
        });
    }

    public int deleteAll() {
        return connectionManager.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM plugin_metric_minute")) {
                return statement.executeUpdate();
            }
        });
    }

    private static void bind(PreparedStatement statement, PluginMetricMinute metric)
        throws SQLException {
        statement.setLong(1, metric.bucketStartMs());
        statement.setString(2, metric.pluginId());
        statement.setLong(3, metric.sampleCount());
        statement.setLong(4, metric.cpuActiveSamples());
        statement.setLong(5, metric.activeThreadSamples());
        statement.setInt(6, metric.liveThreadsMax());
        statement.setLong(7, metric.requestCount());
        statement.setLong(8, metric.requestErrorCount());
        statement.setLong(9, metric.requestDurationSumUs());
        statement.setLong(10, metric.requestDurationMaxUs());
        statement.setLong(11, metric.taskCount());
        statement.setLong(12, metric.taskErrorCount());
        statement.setLong(13, metric.taskDurationSumUs());
        statement.setLong(14, metric.taskDurationMaxUs());
    }

    private static PluginMetricMinute mapMetric(ResultSet resultSet) throws SQLException {
        return new PluginMetricMinute(
            resultSet.getLong("bucket_start_ms"),
            resultSet.getString("plugin_id"),
            resultSet.getLong("sample_count"),
            resultSet.getLong("cpu_active_samples"),
            resultSet.getLong("active_thread_samples"),
            resultSet.getInt("live_threads_max"),
            resultSet.getLong("request_count"),
            resultSet.getLong("request_error_count"),
            resultSet.getLong("request_duration_sum_us"),
            resultSet.getLong("request_duration_max_us"),
            resultSet.getLong("task_count"),
            resultSet.getLong("task_error_count"),
            resultSet.getLong("task_duration_sum_us"),
            resultSet.getLong("task_duration_max_us")
        );
    }

    private static PluginMetricSummary mapSummary(ResultSet resultSet) throws SQLException {
        return new PluginMetricSummary(
            resultSet.getString("plugin_id"),
            resultSet.getLong("sample_count"),
            resultSet.getLong("cpu_active_samples"),
            resultSet.getLong("active_thread_samples"),
            resultSet.getInt("live_threads_max"),
            resultSet.getLong("request_count"),
            resultSet.getLong("request_error_count"),
            resultSet.getLong("request_duration_sum_us"),
            resultSet.getLong("request_duration_max_us"),
            resultSet.getLong("task_count"),
            resultSet.getLong("task_error_count"),
            resultSet.getLong("task_duration_sum_us"),
            resultSet.getLong("task_duration_max_us")
        );
    }
}
