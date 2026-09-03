package com.xhhao.insight.storage.repository;

import com.xhhao.insight.storage.database.SqliteConnectionManager;
import com.xhhao.insight.storage.model.LatencyHistogram;
import com.xhhao.insight.storage.model.RequestMetricMinute;
import com.xhhao.insight.storage.model.RequestRouteSort;
import com.xhhao.insight.storage.model.RequestRouteSummary;
import com.xhhao.insight.storage.model.RequestTrendMinute;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class RequestMetricRepository {

    private static final int MAX_TREND_MINUTES = 43_201;

    private static final String UPSERT_SQL = """
        INSERT INTO request_metric_minute(
            bucket_start_ms, scope, route, method, status_group,
            request_count, error_count, duration_sum_us, duration_max_us,
            latency_le_5ms, latency_le_10ms, latency_le_25ms, latency_le_50ms,
            latency_le_100ms, latency_le_250ms, latency_le_500ms,
            latency_le_1000ms, latency_le_2500ms, latency_le_5000ms,
            latency_gt_5000ms
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(bucket_start_ms, scope, route, method, status_group) DO UPDATE SET
            request_count=request_count + excluded.request_count,
            error_count=error_count + excluded.error_count,
            duration_sum_us=duration_sum_us + excluded.duration_sum_us,
            duration_max_us=MAX(duration_max_us, excluded.duration_max_us),
            latency_le_5ms=latency_le_5ms + excluded.latency_le_5ms,
            latency_le_10ms=latency_le_10ms + excluded.latency_le_10ms,
            latency_le_25ms=latency_le_25ms + excluded.latency_le_25ms,
            latency_le_50ms=latency_le_50ms + excluded.latency_le_50ms,
            latency_le_100ms=latency_le_100ms + excluded.latency_le_100ms,
            latency_le_250ms=latency_le_250ms + excluded.latency_le_250ms,
            latency_le_500ms=latency_le_500ms + excluded.latency_le_500ms,
            latency_le_1000ms=latency_le_1000ms + excluded.latency_le_1000ms,
            latency_le_2500ms=latency_le_2500ms + excluded.latency_le_2500ms,
            latency_le_5000ms=latency_le_5000ms + excluded.latency_le_5000ms,
            latency_gt_5000ms=latency_gt_5000ms + excluded.latency_gt_5000ms
        """;

    private static final String AGGREGATE_CTE = """
        WITH aggregated AS (
            SELECT scope, route, method,
                   SUM(request_count) AS request_count,
                   SUM(error_count) AS error_count,
                   SUM(duration_sum_us) AS duration_sum_us,
                   MAX(duration_max_us) AS duration_max_us,
                   SUM(latency_le_5ms) AS latency_le_5ms,
                   SUM(latency_le_10ms) AS latency_le_10ms,
                   SUM(latency_le_25ms) AS latency_le_25ms,
                   SUM(latency_le_50ms) AS latency_le_50ms,
                   SUM(latency_le_100ms) AS latency_le_100ms,
                   SUM(latency_le_250ms) AS latency_le_250ms,
                   SUM(latency_le_500ms) AS latency_le_500ms,
                   SUM(latency_le_1000ms) AS latency_le_1000ms,
                   SUM(latency_le_2500ms) AS latency_le_2500ms,
                   SUM(latency_le_5000ms) AS latency_le_5000ms,
                   SUM(latency_gt_5000ms) AS latency_gt_5000ms
            FROM request_metric_minute
            WHERE bucket_start_ms >= ? AND bucket_start_ms < ?
            GROUP BY scope, route, method
        )
        SELECT * FROM aggregated
        """;

    private final SqliteConnectionManager connectionManager;

    public RequestMetricRepository(SqliteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void saveBatch(List<RequestMetricMinute> metrics) {
        if (metrics.isEmpty()) {
            return;
        }
        connectionManager.write(connection -> {
            saveBatch(connection, metrics);
            return null;
        });
    }

    void saveBatch(Connection connection, List<RequestMetricMinute> metrics) throws SQLException {
        if (metrics.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            for (RequestMetricMinute metric : metrics) {
                bind(statement, metric);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public List<RequestMetricMinute> findRange(
        long fromInclusiveMs,
        long toExclusiveMs,
        int limit
    ) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs, limit);
        return connectionManager.read(connection -> {
            List<RequestMetricMinute> metrics = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM request_metric_minute
                WHERE bucket_start_ms >= ? AND bucket_start_ms < ?
                ORDER BY bucket_start_ms ASC, scope ASC, route ASC, method ASC, status_group ASC
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

    /**
     * Aggregates route and status-cardinality in SQLite before returning trend points.
     * A supported API range is at most 30 days, hence the explicit one-point-per-minute cap.
     */
    public List<RequestTrendMinute> findTrendRange(
        long fromInclusiveMs,
        long toExclusiveMs
    ) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs);
        return connectionManager.read(connection -> {
            List<RequestTrendMinute> metrics = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT bucket_start_ms,
                       SUM(request_count) AS request_count,
                       SUM(error_count) AS error_count,
                       SUM(duration_sum_us) AS duration_sum_us,
                       MAX(duration_max_us) AS duration_max_us,
                       SUM(latency_le_5ms) AS latency_le_5ms,
                       SUM(latency_le_10ms) AS latency_le_10ms,
                       SUM(latency_le_25ms) AS latency_le_25ms,
                       SUM(latency_le_50ms) AS latency_le_50ms,
                       SUM(latency_le_100ms) AS latency_le_100ms,
                       SUM(latency_le_250ms) AS latency_le_250ms,
                       SUM(latency_le_500ms) AS latency_le_500ms,
                       SUM(latency_le_1000ms) AS latency_le_1000ms,
                       SUM(latency_le_2500ms) AS latency_le_2500ms,
                       SUM(latency_le_5000ms) AS latency_le_5000ms,
                       SUM(latency_gt_5000ms) AS latency_gt_5000ms
                FROM request_metric_minute
                WHERE bucket_start_ms >= ? AND bucket_start_ms < ?
                GROUP BY bucket_start_ms
                ORDER BY bucket_start_ms ASC
                LIMIT ?
                """)) {
                statement.setLong(1, fromInclusiveMs);
                statement.setLong(2, toExclusiveMs);
                statement.setInt(3, MAX_TREND_MINUTES);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        metrics.add(new RequestTrendMinute(
                            resultSet.getLong("bucket_start_ms"),
                            resultSet.getLong("request_count"),
                            resultSet.getLong("error_count"),
                            resultSet.getLong("duration_sum_us"),
                            resultSet.getLong("duration_max_us"),
                            mapHistogram(resultSet)
                        ));
                    }
                }
            }
            return metrics;
        });
    }

    public List<RequestRouteSummary> findTopRoutes(
        long fromInclusiveMs,
        long toExclusiveMs,
        RequestRouteSort sort,
        int limit
    ) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs, limit);
        String sql = AGGREGATE_CTE + " ORDER BY " + orderExpression(sort)
            + " DESC, scope ASC, route ASC, method ASC LIMIT ?";
        return connectionManager.read(connection -> {
            List<RequestRouteSummary> summaries = new ArrayList<>();
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

    public List<RequestRouteSummary> findSlowRoutes(
        long fromInclusiveMs,
        long toExclusiveMs,
        int limit
    ) {
        return findTopRoutes(fromInclusiveMs, toExclusiveMs,
            RequestRouteSort.P95_DURATION, limit);
    }

    public Map<Integer, Long> findStatusDistribution(long fromInclusiveMs, long toExclusiveMs) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs);
        return connectionManager.read(connection -> {
            Map<Integer, Long> distribution = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT status_group, SUM(request_count) AS request_count
                FROM request_metric_minute
                WHERE bucket_start_ms >= ? AND bucket_start_ms < ?
                GROUP BY status_group
                ORDER BY status_group ASC
                """)) {
                statement.setLong(1, fromInclusiveMs);
                statement.setLong(2, toExclusiveMs);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        distribution.put(resultSet.getInt("status_group"),
                            resultSet.getLong("request_count"));
                    }
                }
            }
            return distribution;
        });
    }

    public int deleteAll() {
        return connectionManager.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM request_metric_minute")) {
                return statement.executeUpdate();
            }
        });
    }

    private static void bind(PreparedStatement statement, RequestMetricMinute metric)
        throws SQLException {
        statement.setLong(1, metric.bucketStartMs());
        statement.setString(2, metric.scope());
        statement.setString(3, metric.route());
        statement.setString(4, metric.method());
        statement.setInt(5, metric.statusGroup());
        statement.setLong(6, metric.requestCount());
        statement.setLong(7, metric.errorCount());
        statement.setLong(8, metric.durationSumUs());
        statement.setLong(9, metric.durationMaxUs());
        LatencyHistogram histogram = metric.histogram();
        statement.setLong(10, histogram.le5Ms());
        statement.setLong(11, histogram.le10Ms());
        statement.setLong(12, histogram.le25Ms());
        statement.setLong(13, histogram.le50Ms());
        statement.setLong(14, histogram.le100Ms());
        statement.setLong(15, histogram.le250Ms());
        statement.setLong(16, histogram.le500Ms());
        statement.setLong(17, histogram.le1000Ms());
        statement.setLong(18, histogram.le2500Ms());
        statement.setLong(19, histogram.le5000Ms());
        statement.setLong(20, histogram.gt5000Ms());
    }

    private static RequestMetricMinute mapMetric(ResultSet resultSet) throws SQLException {
        return new RequestMetricMinute(
            resultSet.getLong("bucket_start_ms"),
            resultSet.getString("scope"),
            resultSet.getString("route"),
            resultSet.getString("method"),
            resultSet.getInt("status_group"),
            resultSet.getLong("request_count"),
            resultSet.getLong("error_count"),
            resultSet.getLong("duration_sum_us"),
            resultSet.getLong("duration_max_us"),
            mapHistogram(resultSet)
        );
    }

    private static RequestRouteSummary mapSummary(ResultSet resultSet) throws SQLException {
        return new RequestRouteSummary(
            resultSet.getString("scope"),
            resultSet.getString("route"),
            resultSet.getString("method"),
            resultSet.getLong("request_count"),
            resultSet.getLong("error_count"),
            resultSet.getLong("duration_sum_us"),
            resultSet.getLong("duration_max_us"),
            mapHistogram(resultSet)
        );
    }

    private static LatencyHistogram mapHistogram(ResultSet resultSet) throws SQLException {
        return new LatencyHistogram(
            resultSet.getLong("latency_le_5ms"),
            resultSet.getLong("latency_le_10ms"),
            resultSet.getLong("latency_le_25ms"),
            resultSet.getLong("latency_le_50ms"),
            resultSet.getLong("latency_le_100ms"),
            resultSet.getLong("latency_le_250ms"),
            resultSet.getLong("latency_le_500ms"),
            resultSet.getLong("latency_le_1000ms"),
            resultSet.getLong("latency_le_2500ms"),
            resultSet.getLong("latency_le_5000ms"),
            resultSet.getLong("latency_gt_5000ms")
        );
    }

    private static String orderExpression(RequestRouteSort sort) {
        return switch (sort) {
            case REQUEST_COUNT -> "request_count";
            case ERROR_COUNT -> "error_count";
            case ERROR_RATE -> "CASE WHEN request_count=0 THEN 0 ELSE "
                + "CAST(error_count AS REAL)/request_count END";
            case AVERAGE_DURATION -> "CASE WHEN request_count=0 THEN 0 ELSE "
                + "CAST(duration_sum_us AS REAL)/request_count END";
            case MAX_DURATION -> "duration_max_us";
            case P95_DURATION -> percentileOrderExpression(95);
            case P99_DURATION -> percentileOrderExpression(99);
        };
    }

    /** Produces an allow-listed SQLite expression over the fixed, non-cumulative buckets. */
    private static String percentileOrderExpression(int percentile) {
        String target = "((request_count * " + percentile + " + 99) / 100)";
        String[] buckets = {
            "latency_le_5ms",
            "latency_le_10ms",
            "latency_le_25ms",
            "latency_le_50ms",
            "latency_le_100ms",
            "latency_le_250ms",
            "latency_le_500ms",
            "latency_le_1000ms",
            "latency_le_2500ms",
            "latency_le_5000ms"
        };
        long[] boundsUs = {
            5_000, 10_000, 25_000, 50_000, 100_000,
            250_000, 500_000, 1_000_000, 2_500_000, 5_000_000
        };
        StringBuilder expression = new StringBuilder("CASE ");
        StringBuilder cumulative = new StringBuilder();
        for (int index = 0; index < buckets.length; index++) {
            if (index > 0) {
                cumulative.append(" + ");
            }
            cumulative.append(buckets[index]);
            expression.append("WHEN ").append(cumulative).append(" >= ")
                .append(target).append(" THEN ").append(boundsUs[index]).append(' ');
        }
        return expression.append("ELSE MAX(5000001, duration_max_us) END").toString();
    }
}
