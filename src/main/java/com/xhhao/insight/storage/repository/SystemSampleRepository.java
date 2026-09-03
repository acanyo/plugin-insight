package com.xhhao.insight.storage.repository;

import com.xhhao.insight.storage.database.SqliteConnectionManager;
import com.xhhao.insight.storage.model.SystemSample;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SystemSampleRepository {

    private static final String UPSERT_SQL = """
        INSERT INTO system_sample_15s(
            sampled_at_ms, uptime_ms, process_cpu_load, system_cpu_load,
            system_load_average, heap_used_bytes, heap_max_bytes, non_heap_used_bytes,
            system_memory_used_bytes, system_memory_total_bytes, disk_used_bytes,
            disk_total_bytes, live_threads, daemon_threads, peak_threads,
            deadlocked_threads, gc_count, gc_pause_ms
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(sampled_at_ms) DO UPDATE SET
            uptime_ms=excluded.uptime_ms,
            process_cpu_load=excluded.process_cpu_load,
            system_cpu_load=excluded.system_cpu_load,
            system_load_average=excluded.system_load_average,
            heap_used_bytes=excluded.heap_used_bytes,
            heap_max_bytes=excluded.heap_max_bytes,
            non_heap_used_bytes=excluded.non_heap_used_bytes,
            system_memory_used_bytes=excluded.system_memory_used_bytes,
            system_memory_total_bytes=excluded.system_memory_total_bytes,
            disk_used_bytes=excluded.disk_used_bytes,
            disk_total_bytes=excluded.disk_total_bytes,
            live_threads=excluded.live_threads,
            daemon_threads=excluded.daemon_threads,
            peak_threads=excluded.peak_threads,
            deadlocked_threads=excluded.deadlocked_threads,
            gc_count=excluded.gc_count,
            gc_pause_ms=excluded.gc_pause_ms
        """;

    private final SqliteConnectionManager connectionManager;

    public SystemSampleRepository(SqliteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void save(SystemSample sample) {
        saveBatch(List.of(sample));
    }

    public void saveBatch(List<SystemSample> samples) {
        if (samples.isEmpty()) {
            return;
        }
        connectionManager.write(connection -> {
            saveBatch(connection, samples);
            return null;
        });
    }

    void saveBatch(Connection connection, List<SystemSample> samples) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            for (SystemSample sample : samples) {
                bind(statement, sample);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public Optional<SystemSample> findLatest() {
        return connectionManager.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM system_sample_15s ORDER BY sampled_at_ms DESC LIMIT 1");
                 ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        });
    }

    public List<SystemSample> findRange(long fromInclusiveMs, long toExclusiveMs, int limit) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs, limit);
        return connectionManager.read(connection -> {
            List<SystemSample> samples = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM (
                    SELECT * FROM system_sample_15s
                    WHERE sampled_at_ms >= ? AND sampled_at_ms < ?
                    ORDER BY sampled_at_ms DESC
                    LIMIT ?
                ) recent_samples
                ORDER BY sampled_at_ms ASC
                """)) {
                statement.setLong(1, fromInclusiveMs);
                statement.setLong(2, toExclusiveMs);
                statement.setInt(3, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        samples.add(map(resultSet));
                    }
                }
            }
            return samples;
        });
    }

    public int deleteAll() {
        return connectionManager.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM system_sample_15s")) {
                return statement.executeUpdate();
            }
        });
    }

    private static void bind(PreparedStatement statement, SystemSample sample) throws SQLException {
        statement.setLong(1, sample.sampledAtMs());
        statement.setLong(2, sample.uptimeMs());
        statement.setDouble(3, sample.processCpuLoad());
        statement.setDouble(4, sample.systemCpuLoad());
        statement.setDouble(5, sample.systemLoadAverage());
        statement.setLong(6, sample.heapUsedBytes());
        statement.setLong(7, sample.heapMaxBytes());
        statement.setLong(8, sample.nonHeapUsedBytes());
        statement.setLong(9, sample.systemMemoryUsedBytes());
        statement.setLong(10, sample.systemMemoryTotalBytes());
        statement.setLong(11, sample.diskUsedBytes());
        statement.setLong(12, sample.diskTotalBytes());
        statement.setInt(13, sample.liveThreads());
        statement.setInt(14, sample.daemonThreads());
        statement.setInt(15, sample.peakThreads());
        statement.setInt(16, sample.deadlockedThreads());
        statement.setLong(17, sample.gcCount());
        statement.setLong(18, sample.gcPauseMs());
    }

    private static SystemSample map(ResultSet resultSet) throws SQLException {
        return new SystemSample(
            resultSet.getLong("sampled_at_ms"),
            resultSet.getLong("uptime_ms"),
            resultSet.getDouble("process_cpu_load"),
            resultSet.getDouble("system_cpu_load"),
            resultSet.getDouble("system_load_average"),
            resultSet.getLong("heap_used_bytes"),
            resultSet.getLong("heap_max_bytes"),
            resultSet.getLong("non_heap_used_bytes"),
            resultSet.getLong("system_memory_used_bytes"),
            resultSet.getLong("system_memory_total_bytes"),
            resultSet.getLong("disk_used_bytes"),
            resultSet.getLong("disk_total_bytes"),
            resultSet.getInt("live_threads"),
            resultSet.getInt("daemon_threads"),
            resultSet.getInt("peak_threads"),
            resultSet.getInt("deadlocked_threads"),
            resultSet.getLong("gc_count"),
            resultSet.getLong("gc_pause_ms")
        );
    }

}
