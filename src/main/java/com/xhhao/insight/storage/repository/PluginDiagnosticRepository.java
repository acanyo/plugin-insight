package com.xhhao.insight.storage.repository;

import com.xhhao.insight.storage.database.SqliteConnectionManager;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/** Queries one plugin and aggregates the full selected range into at most 122 points. */
@Repository
public class PluginDiagnosticRepository {
    private final SqliteConnectionManager database;

    public PluginDiagnosticRepository(SqliteConnectionManager database) {
        this.database = database;
    }

    public List<Point> timeline(String pluginId, long from, long to) {
        QueryRangeValidator.validate(from, to);
        long step = Math.max(60_000, ((to - from + 119 * 60_000) / (120 * 60_000)) * 60_000);
        return database.read(
                connection -> {
                    var result = new ArrayList<Point>();
                    try (var statement =
                            connection.prepareStatement(
                                    """
WITH resources AS (
    SELECT bucket_start_ms / ? * ? AS at_ms, SUM(cpu_time_ns) AS cpu_ns,
        SUM(allocated_bytes) AS allocated, MAX(live_threads_max) AS threads,
        COUNT(*) AS resource_minutes, SUM(cpu_measurement_samples) AS cpu_samples,
        SUM(allocation_measurement_samples) AS allocation_samples, SUM(sample_count) AS samples,
        SUM(CASE WHEN cpu_measurement_samples > 0 THEN 1 ELSE 0 END) AS cpu_minutes,
        SUM(CASE WHEN allocation_measurement_samples > 0 THEN 1 ELSE 0 END) AS allocation_minutes
    FROM plugin_resource_minute WHERE plugin_id=? AND bucket_start_ms >= ? AND bucket_start_ms < ?
    GROUP BY at_ms
), requests AS (
    SELECT bucket_start_ms / ? * ? AS at_ms, SUM(request_count) AS requests,
        SUM(request_error_count) AS errors, SUM(request_duration_sum_us) AS duration_us,
        MAX(request_duration_max_us) AS maximum_us, COUNT(*) AS request_minutes
    FROM plugin_metric_minute WHERE plugin_id=? AND bucket_start_ms >= ? AND bucket_start_ms < ?
    GROUP BY at_ms
), times AS (SELECT at_ms FROM resources UNION SELECT at_ms FROM requests)
SELECT times.at_ms, COALESCE(cpu_ns,0) AS cpu_ns, COALESCE(allocated,0) AS allocated,
    COALESCE(threads,0) AS threads, COALESCE(resource_minutes,0) AS resource_minutes,
    COALESCE(cpu_samples,0) AS cpu_samples, COALESCE(allocation_samples,0) AS allocation_samples,
    COALESCE(samples,0) AS samples, COALESCE(cpu_minutes,0) AS cpu_minutes,
    COALESCE(allocation_minutes,0) AS allocation_minutes,
    COALESCE(requests,0) AS requests, COALESCE(errors,0) AS errors,
    COALESCE(duration_us,0) AS duration_us, COALESCE(maximum_us,0) AS maximum_us,
    COALESCE(request_minutes,0) AS request_minutes
FROM times LEFT JOIN resources USING(at_ms) LEFT JOIN requests USING(at_ms)
ORDER BY times.at_ms LIMIT 122
""")) {
                        int parameter = 1;
                        for (int i = 0; i < 2; i++) {
                            statement.setLong(parameter++, step);
                            statement.setLong(parameter++, step);
                            statement.setString(parameter++, pluginId);
                            statement.setLong(parameter++, from);
                            statement.setLong(parameter++, to);
                        }
                        try (var rows = statement.executeQuery()) {
                            while (rows.next())
                                result.add(
                                        new Point(
                                                rows.getLong("at_ms"),
                                                step,
                                                rows.getLong("cpu_ns") / 1_000_000d,
                                                rows.getLong("allocated"),
                                                rows.getInt("threads"),
                                                rows.getInt("resource_minutes"),
                                                rows.getLong("cpu_samples"),
                                                rows.getLong("allocation_samples"),
                                                rows.getLong("samples"),
                                                rows.getInt("cpu_minutes"),
                                                rows.getInt("allocation_minutes"),
                                                rows.getLong("requests"),
                                                rows.getLong("errors"),
                                                rows.getLong("duration_us") / 1_000d,
                                                rows.getLong("maximum_us") / 1_000d,
                                                rows.getInt("request_minutes")));
                        }
                    }
                    return List.copyOf(result);
                });
    }

    public List<Event> events(String pluginId, long from, long to) {
        return database.read(
                connection -> {
                    var result = new ArrayList<Event>();
                    try (var statement =
                            connection.prepareStatement(
                                    """
                                    SELECT occurred_at_ms,type,title,summary FROM monitor_event
                                    WHERE source=? AND occurred_at_ms >= ? AND occurred_at_ms < ?
                                    ORDER BY occurred_at_ms DESC,id DESC LIMIT 20
                                    """)) {
                        statement.setString(1, pluginId);
                        statement.setLong(2, from);
                        statement.setLong(3, to);
                        try (var rows = statement.executeQuery()) {
                            while (rows.next())
                                result.add(
                                        new Event(
                                                rows.getLong(1),
                                                rows.getString(2),
                                                rows.getString(3),
                                                rows.getString(4)));
                        }
                    }
                    return List.copyOf(result);
                });
    }

    public long latestUpgrade(String pluginId, long from, long to) {
        return database.read(
                connection -> {
                    try (var statement =
                            connection.prepareStatement(
                                    """
SELECT occurred_at_ms FROM monitor_event WHERE source=? AND type='plugin'
AND occurred_at_ms >= ? AND occurred_at_ms < ? AND json_valid(details_json)
AND json_extract(details_json,'$.kind')='plugin_version_change'
ORDER BY occurred_at_ms DESC,id DESC LIMIT 1
""")) {
                        statement.setString(1, pluginId);
                        statement.setLong(2, from);
                        statement.setLong(3, to);
                        try (var rows = statement.executeQuery()) {
                            return rows.next() ? rows.getLong(1) : 0;
                        }
                    }
                });
    }

    public record Point(
            long atMs,
            long intervalMs,
            double cpuTimeMs,
            long allocatedBytes,
            int maximumThreads,
            int resourceMinutes,
            long cpuSamples,
            long allocationSamples,
            long samples,
            int cpuObservedMinutes,
            int allocationObservedMinutes,
            long requests,
            long errors,
            double requestDurationMs,
            double maximumRequestMs,
            int requestMinutes) {}

    public record Event(long atMs, String type, String title, String summary) {}
}
