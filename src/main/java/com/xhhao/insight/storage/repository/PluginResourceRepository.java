package com.xhhao.insight.storage.repository;

import com.xhhao.insight.storage.database.SqliteConnectionManager;
import com.xhhao.insight.storage.model.PluginResourceMinute;
import com.xhhao.insight.storage.model.PluginResourcePersistenceSummary;
import com.xhhao.insight.storage.model.PluginResourceSort;
import com.xhhao.insight.storage.model.PluginResourceSummary;
import com.xhhao.insight.storage.model.ResourceAttributionMinute;
import com.xhhao.insight.storage.model.ResourceAttributionSummary;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Stores plugin deltas and their JVM-wide attribution coverage in one transaction. */
@Repository
public class PluginResourceRepository {

    private static final String PLUGIN_UPSERT = """
        INSERT INTO plugin_resource_minute(
            bucket_start_ms, plugin_id, sample_count, cpu_measurement_samples,
            allocation_measurement_samples, cpu_active_samples, active_thread_samples,
            live_threads_max, cpu_time_ns, allocated_bytes
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(bucket_start_ms, plugin_id) DO UPDATE SET
            sample_count=sample_count + excluded.sample_count,
            cpu_measurement_samples=cpu_measurement_samples
                + excluded.cpu_measurement_samples,
            allocation_measurement_samples=allocation_measurement_samples
                + excluded.allocation_measurement_samples,
            cpu_active_samples=cpu_active_samples + excluded.cpu_active_samples,
            active_thread_samples=active_thread_samples + excluded.active_thread_samples,
            live_threads_max=MAX(live_threads_max, excluded.live_threads_max),
            cpu_time_ns=cpu_time_ns + excluded.cpu_time_ns,
            allocated_bytes=allocated_bytes + excluded.allocated_bytes
        """;

    private static final String COVERAGE_UPSERT = """
        INSERT INTO resource_attribution_minute(
            bucket_start_ms, sample_count, cpu_capable_samples,
            allocation_capable_samples, truncated_samples, inspected_thread_samples,
            attributed_thread_samples, total_cpu_time_ns, attributed_cpu_time_ns,
            total_allocated_bytes, attributed_allocated_bytes
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(bucket_start_ms) DO UPDATE SET
            sample_count=sample_count + excluded.sample_count,
            cpu_capable_samples=cpu_capable_samples + excluded.cpu_capable_samples,
            allocation_capable_samples=allocation_capable_samples
                + excluded.allocation_capable_samples,
            truncated_samples=truncated_samples + excluded.truncated_samples,
            inspected_thread_samples=inspected_thread_samples
                + excluded.inspected_thread_samples,
            attributed_thread_samples=attributed_thread_samples
                + excluded.attributed_thread_samples,
            total_cpu_time_ns=total_cpu_time_ns + excluded.total_cpu_time_ns,
            attributed_cpu_time_ns=attributed_cpu_time_ns + excluded.attributed_cpu_time_ns,
            total_allocated_bytes=total_allocated_bytes + excluded.total_allocated_bytes,
            attributed_allocated_bytes=attributed_allocated_bytes
                + excluded.attributed_allocated_bytes
        """;

    private final SqliteConnectionManager connectionManager;

    public PluginResourceRepository(SqliteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void save(
        List<PluginResourceMinute> pluginMetrics,
        ResourceAttributionMinute coverage
    ) {
        connectionManager.write(connection -> {
            savePlugins(connection, pluginMetrics);
            saveCoverage(connection, coverage);
            return null;
        });
    }

    public List<PluginResourceSummary> findRanking(
        long fromInclusiveMs,
        long toExclusiveMs,
        PluginResourceSort sort,
        int limit
    ) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs, limit);
        String orderExpression = switch (sort) {
            case CPU_TIME -> "SUM(cpu_time_ns)";
            case ALLOCATED_BYTES -> "SUM(allocated_bytes)";
            case ACTIVE_THREADS -> "SUM(active_thread_samples)";
        };
        String sql = """
            SELECT plugin_id,
                   SUM(sample_count) AS sample_count,
                   SUM(cpu_measurement_samples) AS cpu_measurement_samples,
                   SUM(allocation_measurement_samples) AS allocation_measurement_samples,
                   SUM(cpu_active_samples) AS cpu_active_samples,
                   SUM(active_thread_samples) AS active_thread_samples,
                   MAX(live_threads_max) AS live_threads_max,
                   SUM(cpu_time_ns) AS cpu_time_ns,
                   SUM(allocated_bytes) AS allocated_bytes
            FROM plugin_resource_minute
            WHERE bucket_start_ms >= ? AND bucket_start_ms < ?
            GROUP BY plugin_id
            ORDER BY %s DESC, plugin_id ASC
            LIMIT ?
            """.formatted(orderExpression);
        return connectionManager.read(connection -> {
            List<PluginResourceSummary> summaries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, fromInclusiveMs);
                statement.setLong(2, toExclusiveMs);
                statement.setInt(3, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        summaries.add(mapSummary(rows));
                    }
                }
            }
            return summaries;
        });
    }

    public ResourceAttributionSummary summarizeCoverage(
        long fromInclusiveMs,
        long toExclusiveMs
    ) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs);
        return connectionManager.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(SUM(sample_count), 0) AS sample_count,
                       COALESCE(SUM(cpu_capable_samples), 0) AS cpu_capable_samples,
                       COALESCE(SUM(allocation_capable_samples), 0)
                           AS allocation_capable_samples,
                       COALESCE(SUM(truncated_samples), 0) AS truncated_samples,
                       COALESCE(SUM(inspected_thread_samples), 0)
                           AS inspected_thread_samples,
                       COALESCE(SUM(attributed_thread_samples), 0)
                           AS attributed_thread_samples,
                       COALESCE(SUM(total_cpu_time_ns), 0) AS total_cpu_time_ns,
                       COALESCE(SUM(attributed_cpu_time_ns), 0)
                           AS attributed_cpu_time_ns,
                       COALESCE(SUM(total_allocated_bytes), 0) AS total_allocated_bytes,
                       COALESCE(SUM(attributed_allocated_bytes), 0)
                           AS attributed_allocated_bytes
                FROM resource_attribution_minute
                WHERE bucket_start_ms >= ? AND bucket_start_ms < ?
                """)) {
                statement.setLong(1, fromInclusiveMs);
                statement.setLong(2, toExclusiveMs);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? mapCoverage(rows) : ResourceAttributionSummary.empty();
                }
            }
        });
    }

    /**
     * Finds how consistently each plugin consumes resources and how often it is the top
     * consumer in a minute. Ties are retained so the result never hides equal consumers.
     */
    public List<PluginResourcePersistenceSummary> findPersistence(
        long fromInclusiveMs,
        long toExclusiveMs,
        int limit
    ) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs, limit);
        return connectionManager.read(connection -> {
            List<PluginResourcePersistenceSummary> summaries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                WITH ranked AS (
                    SELECT plugin_id, bucket_start_ms, cpu_time_ns, allocated_bytes,
                           live_threads_max,
                           DENSE_RANK() OVER (
                               PARTITION BY bucket_start_ms ORDER BY cpu_time_ns DESC
                           ) AS cpu_rank,
                           DENSE_RANK() OVER (
                               PARTITION BY bucket_start_ms ORDER BY allocated_bytes DESC
                           ) AS allocation_rank
                    FROM plugin_resource_minute
                    WHERE bucket_start_ms >= ? AND bucket_start_ms < ?
                )
                SELECT plugin_id,
                       COUNT(*) AS observed_minutes,
                       SUM(CASE WHEN cpu_time_ns > 0 THEN 1 ELSE 0 END)
                           AS cpu_active_minutes,
                       SUM(CASE WHEN allocated_bytes > 0 THEN 1 ELSE 0 END)
                           AS allocation_active_minutes,
                       SUM(CASE WHEN live_threads_max > 0 THEN 1 ELSE 0 END)
                           AS thread_present_minutes,
                       SUM(CASE WHEN cpu_rank = 1 AND cpu_time_ns > 0 THEN 1 ELSE 0 END)
                           AS top_cpu_minutes,
                       SUM(CASE WHEN allocation_rank = 1 AND allocated_bytes > 0
                           THEN 1 ELSE 0 END) AS top_allocation_minutes,
                       SUM(cpu_time_ns) AS cpu_time_ns,
                       SUM(allocated_bytes) AS allocated_bytes,
                       MAX(live_threads_max) AS live_threads_max
                FROM ranked
                GROUP BY plugin_id
                ORDER BY plugin_id ASC
                LIMIT ?
                """)) {
                statement.setLong(1, fromInclusiveMs);
                statement.setLong(2, toExclusiveMs);
                statement.setInt(3, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        summaries.add(new PluginResourcePersistenceSummary(
                            rows.getString("plugin_id"), rows.getInt("observed_minutes"),
                            rows.getInt("cpu_active_minutes"),
                            rows.getInt("allocation_active_minutes"),
                            rows.getInt("thread_present_minutes"),
                            rows.getInt("top_cpu_minutes"),
                            rows.getInt("top_allocation_minutes"),
                            rows.getLong("cpu_time_ns"), rows.getLong("allocated_bytes"),
                            rows.getInt("live_threads_max")
                        ));
                    }
                }
            }
            return summaries;
        });
    }

    public int deleteAll() {
        return connectionManager.write(connection ->
            deleteAll(connection, "plugin_resource_minute")
                + deleteAll(connection, "resource_attribution_minute"));
    }

    private static void savePlugins(Connection connection, List<PluginResourceMinute> metrics)
        throws SQLException {
        if (metrics.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(PLUGIN_UPSERT)) {
            for (PluginResourceMinute metric : metrics) {
                statement.setLong(1, metric.bucketStartMs());
                statement.setString(2, metric.pluginId());
                statement.setLong(3, metric.sampleCount());
                statement.setLong(4, metric.cpuMeasurementSamples());
                statement.setLong(5, metric.allocationMeasurementSamples());
                statement.setLong(6, metric.cpuActiveSamples());
                statement.setLong(7, metric.activeThreadSamples());
                statement.setInt(8, metric.liveThreadsMax());
                statement.setLong(9, metric.cpuTimeNs());
                statement.setLong(10, metric.allocatedBytes());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void saveCoverage(Connection connection, ResourceAttributionMinute metric)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COVERAGE_UPSERT)) {
            statement.setLong(1, metric.bucketStartMs());
            statement.setLong(2, metric.sampleCount());
            statement.setLong(3, metric.cpuCapableSamples());
            statement.setLong(4, metric.allocationCapableSamples());
            statement.setLong(5, metric.truncatedSamples());
            statement.setLong(6, metric.inspectedThreadSamples());
            statement.setLong(7, metric.attributedThreadSamples());
            statement.setLong(8, metric.totalCpuTimeNs());
            statement.setLong(9, metric.attributedCpuTimeNs());
            statement.setLong(10, metric.totalAllocatedBytes());
            statement.setLong(11, metric.attributedAllocatedBytes());
            statement.executeUpdate();
        }
    }

    private static PluginResourceSummary mapSummary(ResultSet rows) throws SQLException {
        return new PluginResourceSummary(
            rows.getString("plugin_id"), rows.getLong("sample_count"),
            rows.getLong("cpu_measurement_samples"),
            rows.getLong("allocation_measurement_samples"),
            rows.getLong("cpu_active_samples"), rows.getLong("active_thread_samples"),
            rows.getInt("live_threads_max"), rows.getLong("cpu_time_ns"),
            rows.getLong("allocated_bytes")
        );
    }

    private static ResourceAttributionSummary mapCoverage(ResultSet rows) throws SQLException {
        return new ResourceAttributionSummary(
            rows.getLong("sample_count"), rows.getLong("cpu_capable_samples"),
            rows.getLong("allocation_capable_samples"), rows.getLong("truncated_samples"),
            rows.getLong("inspected_thread_samples"),
            rows.getLong("attributed_thread_samples"), rows.getLong("total_cpu_time_ns"),
            rows.getLong("attributed_cpu_time_ns"), rows.getLong("total_allocated_bytes"),
            rows.getLong("attributed_allocated_bytes")
        );
    }

    private static int deleteAll(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate("DELETE FROM " + table);
        }
    }
}
