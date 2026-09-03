package com.xhhao.insight.storage.repository;

import com.xhhao.insight.storage.aggregation.SystemMetricMinuteAggregator;
import com.xhhao.insight.storage.database.SqliteConnectionManager;
import com.xhhao.insight.storage.model.SystemMetricMinute;
import com.xhhao.insight.storage.model.SystemSample;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SystemMetricMinuteRepository {

    private static final String UPSERT_SQL = """
        INSERT INTO system_metric_minute(
            bucket_start_ms, last_sampled_at_ms, sample_count, uptime_last_ms,
            process_cpu_load_avg, process_cpu_load_max, process_cpu_load_last,
            system_cpu_load_avg, system_cpu_load_max, system_cpu_load_last,
            system_load_average_avg, system_load_average_max, system_load_average_last,
            heap_used_bytes_avg, heap_used_bytes_max, heap_used_bytes_last,
            heap_max_bytes_last, non_heap_used_bytes_avg, non_heap_used_bytes_max,
            non_heap_used_bytes_last, system_memory_used_bytes_avg,
            system_memory_used_bytes_max, system_memory_used_bytes_last,
            system_memory_total_bytes_last, disk_used_bytes_last, disk_total_bytes_last,
            live_threads_avg, live_threads_max, live_threads_last, daemon_threads_last,
            peak_threads_max, deadlocked_threads_max, deadlocked_threads_last,
            gc_count_delta, gc_pause_ms_delta
        ) VALUES (
            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
        )
        ON CONFLICT(bucket_start_ms) DO UPDATE SET
            last_sampled_at_ms=excluded.last_sampled_at_ms,
            sample_count=excluded.sample_count,
            uptime_last_ms=excluded.uptime_last_ms,
            process_cpu_load_avg=excluded.process_cpu_load_avg,
            process_cpu_load_max=excluded.process_cpu_load_max,
            process_cpu_load_last=excluded.process_cpu_load_last,
            system_cpu_load_avg=excluded.system_cpu_load_avg,
            system_cpu_load_max=excluded.system_cpu_load_max,
            system_cpu_load_last=excluded.system_cpu_load_last,
            system_load_average_avg=excluded.system_load_average_avg,
            system_load_average_max=excluded.system_load_average_max,
            system_load_average_last=excluded.system_load_average_last,
            heap_used_bytes_avg=excluded.heap_used_bytes_avg,
            heap_used_bytes_max=excluded.heap_used_bytes_max,
            heap_used_bytes_last=excluded.heap_used_bytes_last,
            heap_max_bytes_last=excluded.heap_max_bytes_last,
            non_heap_used_bytes_avg=excluded.non_heap_used_bytes_avg,
            non_heap_used_bytes_max=excluded.non_heap_used_bytes_max,
            non_heap_used_bytes_last=excluded.non_heap_used_bytes_last,
            system_memory_used_bytes_avg=excluded.system_memory_used_bytes_avg,
            system_memory_used_bytes_max=excluded.system_memory_used_bytes_max,
            system_memory_used_bytes_last=excluded.system_memory_used_bytes_last,
            system_memory_total_bytes_last=excluded.system_memory_total_bytes_last,
            disk_used_bytes_last=excluded.disk_used_bytes_last,
            disk_total_bytes_last=excluded.disk_total_bytes_last,
            live_threads_avg=excluded.live_threads_avg,
            live_threads_max=excluded.live_threads_max,
            live_threads_last=excluded.live_threads_last,
            daemon_threads_last=excluded.daemon_threads_last,
            peak_threads_max=excluded.peak_threads_max,
            deadlocked_threads_max=excluded.deadlocked_threads_max,
            deadlocked_threads_last=excluded.deadlocked_threads_last,
            gc_count_delta=excluded.gc_count_delta,
            gc_pause_ms_delta=excluded.gc_pause_ms_delta
        """;

    private final SqliteConnectionManager connectionManager;

    public SystemMetricMinuteRepository(SqliteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public List<SystemMetricMinute> findRange(long fromInclusiveMs, long toExclusiveMs) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs);
        return connectionManager.read(connection -> {
            List<SystemMetricMinute> metrics = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM system_metric_minute
                WHERE bucket_start_ms >= ? AND bucket_start_ms < ?
                ORDER BY bucket_start_ms ASC
                """)) {
                statement.setLong(1, SystemMetricMinuteAggregator.bucketStart(fromInclusiveMs));
                statement.setLong(2, toExclusiveMs);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        SystemMetricMinute metric = map(resultSet);
                        if (metric.lastSampledAtMs() >= fromInclusiveMs) {
                            metrics.add(metric);
                        }
                    }
                }
            }
            return metrics;
        });
    }

    void rebuild(Connection connection, long bucketStartMs) throws SQLException {
        List<SystemSample> samples = findRawBucket(connection, bucketStartMs);
        if (samples.isEmpty()) {
            try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM system_metric_minute WHERE bucket_start_ms=?")) {
                delete.setLong(1, bucketStartMs);
                delete.executeUpdate();
            }
            return;
        }
        SystemSample previous = findPreviousRaw(connection, bucketStartMs).orElse(null);
        upsert(connection,
            SystemMetricMinuteAggregator.aggregate(bucketStartMs, samples, previous));
    }

    public int deleteAll() {
        return connectionManager.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM system_metric_minute")) {
                return statement.executeUpdate();
            }
        });
    }

    private static List<SystemSample> findRawBucket(Connection connection, long bucketStartMs)
        throws SQLException {
        List<SystemSample> samples = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT * FROM system_sample_15s
            WHERE sampled_at_ms >= ? AND sampled_at_ms < ?
            ORDER BY sampled_at_ms ASC
            """)) {
            statement.setLong(1, bucketStartMs);
            statement.setLong(2, bucketStartMs + SystemMetricMinuteAggregator.BUCKET_MILLIS);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    samples.add(mapSample(resultSet));
                }
            }
        }
        return samples;
    }

    private static Optional<SystemSample> findPreviousRaw(Connection connection, long bucketStartMs)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT * FROM system_sample_15s
            WHERE sampled_at_ms < ?
            ORDER BY sampled_at_ms DESC LIMIT 1
            """)) {
            statement.setLong(1, bucketStartMs);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapSample(resultSet)) : Optional.empty();
            }
        }
    }

    private static void upsert(Connection connection, SystemMetricMinute metric)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            bind(statement, metric);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, SystemMetricMinute metric)
        throws SQLException {
        int index = 1;
        statement.setLong(index++, metric.bucketStartMs());
        statement.setLong(index++, metric.lastSampledAtMs());
        statement.setInt(index++, metric.sampleCount());
        statement.setLong(index++, metric.uptimeLastMs());
        statement.setDouble(index++, metric.processCpuAverage());
        statement.setDouble(index++, metric.processCpuMax());
        statement.setDouble(index++, metric.processCpuLast());
        statement.setDouble(index++, metric.systemCpuAverage());
        statement.setDouble(index++, metric.systemCpuMax());
        statement.setDouble(index++, metric.systemCpuLast());
        statement.setDouble(index++, metric.systemLoadAverage());
        statement.setDouble(index++, metric.systemLoadMax());
        statement.setDouble(index++, metric.systemLoadLast());
        statement.setLong(index++, metric.heapUsedAverageBytes());
        statement.setLong(index++, metric.heapUsedMaxBytes());
        statement.setLong(index++, metric.heapUsedLastBytes());
        statement.setLong(index++, metric.heapMaxLastBytes());
        statement.setLong(index++, metric.nonHeapUsedAverageBytes());
        statement.setLong(index++, metric.nonHeapUsedMaxBytes());
        statement.setLong(index++, metric.nonHeapUsedLastBytes());
        statement.setLong(index++, metric.systemMemoryUsedAverageBytes());
        statement.setLong(index++, metric.systemMemoryUsedMaxBytes());
        statement.setLong(index++, metric.systemMemoryUsedLastBytes());
        statement.setLong(index++, metric.systemMemoryTotalLastBytes());
        statement.setLong(index++, metric.diskUsedLastBytes());
        statement.setLong(index++, metric.diskTotalLastBytes());
        statement.setDouble(index++, metric.liveThreadsAverage());
        statement.setInt(index++, metric.liveThreadsMax());
        statement.setInt(index++, metric.liveThreadsLast());
        statement.setInt(index++, metric.daemonThreadsLast());
        statement.setInt(index++, metric.peakThreadsMax());
        statement.setInt(index++, metric.deadlockedThreadsMax());
        statement.setInt(index++, metric.deadlockedThreadsLast());
        statement.setLong(index++, metric.gcCountDelta());
        statement.setLong(index, metric.gcPauseMsDelta());
    }

    private static SystemMetricMinute map(ResultSet resultSet) throws SQLException {
        return new SystemMetricMinute(
            resultSet.getLong("bucket_start_ms"),
            resultSet.getLong("last_sampled_at_ms"),
            resultSet.getInt("sample_count"),
            resultSet.getLong("uptime_last_ms"),
            resultSet.getDouble("process_cpu_load_avg"),
            resultSet.getDouble("process_cpu_load_max"),
            resultSet.getDouble("process_cpu_load_last"),
            resultSet.getDouble("system_cpu_load_avg"),
            resultSet.getDouble("system_cpu_load_max"),
            resultSet.getDouble("system_cpu_load_last"),
            resultSet.getDouble("system_load_average_avg"),
            resultSet.getDouble("system_load_average_max"),
            resultSet.getDouble("system_load_average_last"),
            resultSet.getLong("heap_used_bytes_avg"),
            resultSet.getLong("heap_used_bytes_max"),
            resultSet.getLong("heap_used_bytes_last"),
            resultSet.getLong("heap_max_bytes_last"),
            resultSet.getLong("non_heap_used_bytes_avg"),
            resultSet.getLong("non_heap_used_bytes_max"),
            resultSet.getLong("non_heap_used_bytes_last"),
            resultSet.getLong("system_memory_used_bytes_avg"),
            resultSet.getLong("system_memory_used_bytes_max"),
            resultSet.getLong("system_memory_used_bytes_last"),
            resultSet.getLong("system_memory_total_bytes_last"),
            resultSet.getLong("disk_used_bytes_last"),
            resultSet.getLong("disk_total_bytes_last"),
            resultSet.getDouble("live_threads_avg"),
            resultSet.getInt("live_threads_max"),
            resultSet.getInt("live_threads_last"),
            resultSet.getInt("daemon_threads_last"),
            resultSet.getInt("peak_threads_max"),
            resultSet.getInt("deadlocked_threads_max"),
            resultSet.getInt("deadlocked_threads_last"),
            resultSet.getLong("gc_count_delta"),
            resultSet.getLong("gc_pause_ms_delta")
        );
    }

    private static SystemSample mapSample(ResultSet resultSet) throws SQLException {
        return new SystemSample(
            resultSet.getLong("sampled_at_ms"), resultSet.getLong("uptime_ms"),
            resultSet.getDouble("process_cpu_load"), resultSet.getDouble("system_cpu_load"),
            resultSet.getDouble("system_load_average"), resultSet.getLong("heap_used_bytes"),
            resultSet.getLong("heap_max_bytes"), resultSet.getLong("non_heap_used_bytes"),
            resultSet.getLong("system_memory_used_bytes"),
            resultSet.getLong("system_memory_total_bytes"),
            resultSet.getLong("disk_used_bytes"), resultSet.getLong("disk_total_bytes"),
            resultSet.getInt("live_threads"), resultSet.getInt("daemon_threads"),
            resultSet.getInt("peak_threads"), resultSet.getInt("deadlocked_threads"),
            resultSet.getLong("gc_count"), resultSet.getLong("gc_pause_ms")
        );
    }
}
