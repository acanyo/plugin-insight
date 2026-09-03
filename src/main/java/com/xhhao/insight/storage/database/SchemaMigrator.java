package com.xhhao.insight.storage.database;

import com.xhhao.insight.storage.StorageException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SchemaMigrator {

    public static final int SUPPORTED_VERSION = 3;

    private static final Map<String, Set<String>> REQUIRED_TABLE_COLUMNS = Map.of(
        "schema_history", Set.of("version", "description", "checksum", "installed_at_ms"),
        "system_sample_15s", Set.of(
            "sampled_at_ms", "uptime_ms", "process_cpu_load", "system_cpu_load",
            "system_load_average", "heap_used_bytes", "heap_max_bytes", "non_heap_used_bytes",
            "system_memory_used_bytes", "system_memory_total_bytes", "disk_used_bytes",
            "disk_total_bytes", "live_threads", "daemon_threads", "peak_threads",
            "deadlocked_threads", "gc_count", "gc_pause_ms"),
        "system_metric_minute", Set.of(
            "bucket_start_ms", "last_sampled_at_ms", "sample_count", "uptime_last_ms",
            "process_cpu_load_avg", "process_cpu_load_max", "process_cpu_load_last",
            "system_cpu_load_avg", "system_cpu_load_max", "system_cpu_load_last",
            "system_load_average_avg", "system_load_average_max",
            "system_load_average_last", "heap_used_bytes_avg", "heap_used_bytes_max",
            "heap_used_bytes_last", "heap_max_bytes_last", "non_heap_used_bytes_avg",
            "non_heap_used_bytes_max", "non_heap_used_bytes_last",
            "system_memory_used_bytes_avg", "system_memory_used_bytes_max",
            "system_memory_used_bytes_last", "system_memory_total_bytes_last",
            "disk_used_bytes_last", "disk_total_bytes_last", "live_threads_avg",
            "live_threads_max", "live_threads_last", "daemon_threads_last",
            "peak_threads_max", "deadlocked_threads_max", "deadlocked_threads_last",
            "gc_count_delta", "gc_pause_ms_delta"),
        "plugin_metric_minute", Set.of(
            "bucket_start_ms", "plugin_id", "sample_count", "cpu_active_samples",
            "active_thread_samples", "live_threads_max", "request_count",
            "request_error_count", "request_duration_sum_us", "request_duration_max_us",
            "task_count", "task_error_count", "task_duration_sum_us", "task_duration_max_us"),
        "plugin_resource_minute", Set.of(
            "bucket_start_ms", "plugin_id", "sample_count", "cpu_measurement_samples",
            "allocation_measurement_samples", "cpu_active_samples",
            "active_thread_samples", "live_threads_max", "cpu_time_ns", "allocated_bytes"),
        "resource_attribution_minute", Set.of(
            "bucket_start_ms", "sample_count", "cpu_capable_samples",
            "allocation_capable_samples", "truncated_samples", "inspected_thread_samples",
            "attributed_thread_samples", "total_cpu_time_ns", "attributed_cpu_time_ns",
            "total_allocated_bytes", "attributed_allocated_bytes"),
        "request_metric_minute", Set.of(
            "bucket_start_ms", "scope", "route", "method", "status_group", "request_count",
            "error_count", "duration_sum_us", "duration_max_us", "latency_le_5ms",
            "latency_le_10ms", "latency_le_25ms", "latency_le_50ms", "latency_le_100ms",
            "latency_le_250ms", "latency_le_500ms", "latency_le_1000ms",
            "latency_le_2500ms", "latency_le_5000ms", "latency_gt_5000ms"),
        "monitor_event", Set.of(
            "id", "occurred_at_ms", "type", "severity", "source", "title", "summary",
            "fingerprint", "first_seen_at_ms", "last_seen_at_ms", "occurrence_count",
            "details_json"),
        "alert_incident", Set.of(
            "id", "rule_id", "status", "severity", "title", "summary", "fingerprint",
            "triggered_at_ms", "updated_at_ms", "resolved_at_ms", "occurrence_count",
            "last_value", "threshold_value", "details_json")
    );

    private static final Map<String, List<String>> REQUIRED_INDEX_COLUMNS = Map.of(
        "idx_plugin_metric_plugin_time", List.of("plugin_id", "bucket_start_ms"),
        "idx_plugin_resource_plugin_time", List.of("plugin_id", "bucket_start_ms"),
        "idx_request_metric_route_time", List.of("scope", "route", "method", "bucket_start_ms"),
        "idx_monitor_event_time_id", List.of("occurred_at_ms", "id"),
        "idx_monitor_event_fingerprint", List.of("fingerprint", "last_seen_at_ms"),
        "idx_alert_incident_status_time", List.of("status", "updated_at_ms"),
        "idx_alert_incident_fingerprint", List.of("fingerprint", "updated_at_ms")
    );

    private final SqliteConnectionManager connectionManager;
    private final Clock clock;
    private final Map<Integer, Migration> migrations;

    @Autowired
    public SchemaMigrator(SqliteConnectionManager connectionManager) {
        this(connectionManager, Clock.systemUTC());
    }

    SchemaMigrator(SqliteConnectionManager connectionManager, Clock clock) {
        this.connectionManager = connectionManager;
        this.clock = clock;
        this.migrations = Map.of(
            1, new Migration(
                1,
                "Create Insight monitoring schema",
                "insight-schema-v1",
                this::migrateToV1
            ),
            2, new Migration(
                2,
                "Add system metrics minute aggregation",
                "insight-schema-v2-system-metric-minute",
                this::migrateToV2
            ),
            3, new Migration(
                3,
                "Add bounded plugin resource attribution",
                "insight-schema-v3-plugin-resource-attribution",
                this::migrateToV3
            )
        );
    }

    public int migrate() {
        return connectionManager.write(connection -> {
            int currentVersion = readUserVersion(connection);
            if (currentVersion > SUPPORTED_VERSION) {
                throw new SchemaVersionException(currentVersion, SUPPORTED_VERSION);
            }
            validateExistingTableShapes(connection);
            if (currentVersion > 0) {
                for (int existingVersion = 1;
                     existingVersion <= currentVersion;
                     existingVersion++) {
                    Migration migration = migrations.get(existingVersion);
                    if (migration == null) {
                        throw new StorageException(
                            "Missing migration for schema version " + existingVersion);
                    }
                    migration.action().apply(connection);
                    recordMigration(connection, migration);
                }
            }
            for (int nextVersion = currentVersion + 1;
                 nextVersion <= SUPPORTED_VERSION;
                 nextVersion++) {
                Migration migration = migrations.get(nextVersion);
                if (migration == null) {
                    throw new StorageException("Missing migration for schema version " + nextVersion);
                }
                migration.action().apply(connection);
                recordMigration(connection, migration);
                setUserVersion(connection, nextVersion);
            }
            validateSchema(connection);
            return SUPPORTED_VERSION;
        });
    }

    public int currentVersion() {
        return connectionManager.read(this::readUserVersion);
    }

    public void validate() {
        connectionManager.read(connection -> {
            validateSchema(connection);
            return null;
        });
    }

    private void migrateToV1(Connection connection) throws SQLException {
        List<String> statements = List.of(
            """
                CREATE TABLE IF NOT EXISTS schema_history (
                    version INTEGER PRIMARY KEY,
                    description TEXT NOT NULL,
                    checksum TEXT NOT NULL,
                    installed_at_ms INTEGER NOT NULL
                )
                """,
            """
                CREATE TABLE IF NOT EXISTS system_sample_15s (
                    sampled_at_ms INTEGER PRIMARY KEY,
                    uptime_ms INTEGER NOT NULL,
                    process_cpu_load REAL NOT NULL,
                    system_cpu_load REAL NOT NULL,
                    system_load_average REAL NOT NULL,
                    heap_used_bytes INTEGER NOT NULL,
                    heap_max_bytes INTEGER NOT NULL,
                    non_heap_used_bytes INTEGER NOT NULL,
                    system_memory_used_bytes INTEGER NOT NULL,
                    system_memory_total_bytes INTEGER NOT NULL,
                    disk_used_bytes INTEGER NOT NULL,
                    disk_total_bytes INTEGER NOT NULL,
                    live_threads INTEGER NOT NULL,
                    daemon_threads INTEGER NOT NULL,
                    peak_threads INTEGER NOT NULL,
                    deadlocked_threads INTEGER NOT NULL,
                    gc_count INTEGER NOT NULL,
                    gc_pause_ms INTEGER NOT NULL
                )
                """,
            """
                CREATE TABLE IF NOT EXISTS plugin_metric_minute (
                    bucket_start_ms INTEGER NOT NULL,
                    plugin_id TEXT NOT NULL,
                    sample_count INTEGER NOT NULL,
                    cpu_active_samples INTEGER NOT NULL,
                    active_thread_samples INTEGER NOT NULL,
                    live_threads_max INTEGER NOT NULL,
                    request_count INTEGER NOT NULL,
                    request_error_count INTEGER NOT NULL,
                    request_duration_sum_us INTEGER NOT NULL,
                    request_duration_max_us INTEGER NOT NULL,
                    task_count INTEGER NOT NULL,
                    task_error_count INTEGER NOT NULL,
                    task_duration_sum_us INTEGER NOT NULL,
                    task_duration_max_us INTEGER NOT NULL,
                    PRIMARY KEY (bucket_start_ms, plugin_id)
                )
                """,
            "CREATE INDEX IF NOT EXISTS idx_plugin_metric_plugin_time "
                + "ON plugin_metric_minute(plugin_id, bucket_start_ms)",
            """
                CREATE TABLE IF NOT EXISTS request_metric_minute (
                    bucket_start_ms INTEGER NOT NULL,
                    scope TEXT NOT NULL,
                    route TEXT NOT NULL,
                    method TEXT NOT NULL,
                    status_group INTEGER NOT NULL CHECK(status_group BETWEEN 1 AND 5),
                    request_count INTEGER NOT NULL,
                    error_count INTEGER NOT NULL,
                    duration_sum_us INTEGER NOT NULL,
                    duration_max_us INTEGER NOT NULL,
                    latency_le_5ms INTEGER NOT NULL,
                    latency_le_10ms INTEGER NOT NULL,
                    latency_le_25ms INTEGER NOT NULL,
                    latency_le_50ms INTEGER NOT NULL,
                    latency_le_100ms INTEGER NOT NULL,
                    latency_le_250ms INTEGER NOT NULL,
                    latency_le_500ms INTEGER NOT NULL,
                    latency_le_1000ms INTEGER NOT NULL,
                    latency_le_2500ms INTEGER NOT NULL,
                    latency_le_5000ms INTEGER NOT NULL,
                    latency_gt_5000ms INTEGER NOT NULL,
                    PRIMARY KEY (bucket_start_ms, scope, route, method, status_group)
                )
                """,
            "CREATE INDEX IF NOT EXISTS idx_request_metric_route_time "
                + "ON request_metric_minute(scope, route, method, bucket_start_ms)",
            """
                CREATE TABLE IF NOT EXISTS monitor_event (
                    id TEXT PRIMARY KEY,
                    occurred_at_ms INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    severity TEXT NOT NULL,
                    source TEXT NOT NULL,
                    title TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    fingerprint TEXT NOT NULL,
                    first_seen_at_ms INTEGER NOT NULL,
                    last_seen_at_ms INTEGER NOT NULL,
                    occurrence_count INTEGER NOT NULL,
                    details_json TEXT
                )
                """,
            "CREATE INDEX IF NOT EXISTS idx_monitor_event_time_id "
                + "ON monitor_event(occurred_at_ms DESC, id DESC)",
            "CREATE INDEX IF NOT EXISTS idx_monitor_event_fingerprint "
                + "ON monitor_event(fingerprint, last_seen_at_ms DESC)",
            """
                CREATE TABLE IF NOT EXISTS alert_incident (
                    id TEXT PRIMARY KEY,
                    rule_id TEXT NOT NULL,
                    status TEXT NOT NULL,
                    severity TEXT NOT NULL,
                    title TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    fingerprint TEXT NOT NULL,
                    triggered_at_ms INTEGER NOT NULL,
                    updated_at_ms INTEGER NOT NULL,
                    resolved_at_ms INTEGER,
                    occurrence_count INTEGER NOT NULL,
                    last_value REAL,
                    threshold_value REAL,
                    details_json TEXT
                )
                """,
            "CREATE INDEX IF NOT EXISTS idx_alert_incident_status_time "
                + "ON alert_incident(status, updated_at_ms DESC)",
            "CREATE INDEX IF NOT EXISTS idx_alert_incident_fingerprint "
                + "ON alert_incident(fingerprint, updated_at_ms DESC)"
        );

        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private void migrateToV2(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS system_metric_minute (
                    bucket_start_ms INTEGER PRIMARY KEY,
                    last_sampled_at_ms INTEGER NOT NULL,
                    sample_count INTEGER NOT NULL CHECK(sample_count > 0),
                    uptime_last_ms INTEGER NOT NULL,
                    process_cpu_load_avg REAL NOT NULL,
                    process_cpu_load_max REAL NOT NULL,
                    process_cpu_load_last REAL NOT NULL,
                    system_cpu_load_avg REAL NOT NULL,
                    system_cpu_load_max REAL NOT NULL,
                    system_cpu_load_last REAL NOT NULL,
                    system_load_average_avg REAL NOT NULL,
                    system_load_average_max REAL NOT NULL,
                    system_load_average_last REAL NOT NULL,
                    heap_used_bytes_avg INTEGER NOT NULL,
                    heap_used_bytes_max INTEGER NOT NULL,
                    heap_used_bytes_last INTEGER NOT NULL,
                    heap_max_bytes_last INTEGER NOT NULL,
                    non_heap_used_bytes_avg INTEGER NOT NULL,
                    non_heap_used_bytes_max INTEGER NOT NULL,
                    non_heap_used_bytes_last INTEGER NOT NULL,
                    system_memory_used_bytes_avg INTEGER NOT NULL,
                    system_memory_used_bytes_max INTEGER NOT NULL,
                    system_memory_used_bytes_last INTEGER NOT NULL,
                    system_memory_total_bytes_last INTEGER NOT NULL,
                    disk_used_bytes_last INTEGER NOT NULL,
                    disk_total_bytes_last INTEGER NOT NULL,
                    live_threads_avg REAL NOT NULL,
                    live_threads_max INTEGER NOT NULL,
                    live_threads_last INTEGER NOT NULL,
                    daemon_threads_last INTEGER NOT NULL,
                    peak_threads_max INTEGER NOT NULL,
                    deadlocked_threads_max INTEGER NOT NULL,
                    deadlocked_threads_last INTEGER NOT NULL,
                    gc_count_delta INTEGER NOT NULL,
                    gc_pause_ms_delta INTEGER NOT NULL
                )
                """);
            backfillSystemMetricMinutes(statement);
        }
    }

    private void migrateToV3(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS plugin_resource_minute (
                    bucket_start_ms INTEGER NOT NULL,
                    plugin_id TEXT NOT NULL,
                    sample_count INTEGER NOT NULL,
                    cpu_measurement_samples INTEGER NOT NULL,
                    allocation_measurement_samples INTEGER NOT NULL,
                    cpu_active_samples INTEGER NOT NULL,
                    active_thread_samples INTEGER NOT NULL,
                    live_threads_max INTEGER NOT NULL,
                    cpu_time_ns INTEGER NOT NULL,
                    allocated_bytes INTEGER NOT NULL,
                    PRIMARY KEY (bucket_start_ms, plugin_id)
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_plugin_resource_plugin_time "
                + "ON plugin_resource_minute(plugin_id, bucket_start_ms)");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS resource_attribution_minute (
                    bucket_start_ms INTEGER PRIMARY KEY,
                    sample_count INTEGER NOT NULL,
                    cpu_capable_samples INTEGER NOT NULL,
                    allocation_capable_samples INTEGER NOT NULL,
                    truncated_samples INTEGER NOT NULL,
                    inspected_thread_samples INTEGER NOT NULL,
                    attributed_thread_samples INTEGER NOT NULL,
                    total_cpu_time_ns INTEGER NOT NULL,
                    attributed_cpu_time_ns INTEGER NOT NULL,
                    total_allocated_bytes INTEGER NOT NULL,
                    attributed_allocated_bytes INTEGER NOT NULL
                )
                """);
        }
    }

    private static void backfillSystemMetricMinutes(Statement statement) throws SQLException {
        statement.executeUpdate("""
            WITH bucketed AS (
                SELECT *, (sampled_at_ms / 60000) * 60000 AS bucket_start_ms
                FROM system_sample_15s
            ), annotated AS (
                SELECT *,
                    LAG(uptime_ms) OVER (ORDER BY sampled_at_ms) AS previous_uptime_ms,
                    LAG(gc_count) OVER (ORDER BY sampled_at_ms) AS previous_gc_count,
                    LAG(gc_pause_ms) OVER (ORDER BY sampled_at_ms) AS previous_gc_pause_ms,
                    ROW_NUMBER() OVER (
                        PARTITION BY bucket_start_ms ORDER BY sampled_at_ms DESC
                    ) AS newest_in_bucket
                FROM bucketed
            )
            INSERT OR IGNORE INTO system_metric_minute(
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
            )
            SELECT
                bucket_start_ms,
                MAX(sampled_at_ms),
                COUNT(*),
                MAX(CASE WHEN newest_in_bucket = 1 THEN uptime_ms END),
                AVG(process_cpu_load), MAX(process_cpu_load),
                MAX(CASE WHEN newest_in_bucket = 1 THEN process_cpu_load END),
                AVG(system_cpu_load), MAX(system_cpu_load),
                MAX(CASE WHEN newest_in_bucket = 1 THEN system_cpu_load END),
                AVG(system_load_average), MAX(system_load_average),
                MAX(CASE WHEN newest_in_bucket = 1 THEN system_load_average END),
                CAST(ROUND(AVG(heap_used_bytes)) AS INTEGER), MAX(heap_used_bytes),
                MAX(CASE WHEN newest_in_bucket = 1 THEN heap_used_bytes END),
                MAX(CASE WHEN newest_in_bucket = 1 THEN heap_max_bytes END),
                CAST(ROUND(AVG(non_heap_used_bytes)) AS INTEGER), MAX(non_heap_used_bytes),
                MAX(CASE WHEN newest_in_bucket = 1 THEN non_heap_used_bytes END),
                CAST(ROUND(AVG(system_memory_used_bytes)) AS INTEGER),
                MAX(system_memory_used_bytes),
                MAX(CASE WHEN newest_in_bucket = 1 THEN system_memory_used_bytes END),
                MAX(CASE WHEN newest_in_bucket = 1 THEN system_memory_total_bytes END),
                MAX(CASE WHEN newest_in_bucket = 1 THEN disk_used_bytes END),
                MAX(CASE WHEN newest_in_bucket = 1 THEN disk_total_bytes END),
                AVG(live_threads), MAX(live_threads),
                MAX(CASE WHEN newest_in_bucket = 1 THEN live_threads END),
                MAX(CASE WHEN newest_in_bucket = 1 THEN daemon_threads END),
                MAX(peak_threads), MAX(deadlocked_threads),
                MAX(CASE WHEN newest_in_bucket = 1 THEN deadlocked_threads END),
                SUM(CASE
                    WHEN previous_uptime_ms IS NOT NULL
                        AND uptime_ms >= previous_uptime_ms
                        AND gc_count >= previous_gc_count
                    THEN gc_count - previous_gc_count ELSE 0 END),
                SUM(CASE
                    WHEN previous_uptime_ms IS NOT NULL
                        AND uptime_ms >= previous_uptime_ms
                        AND gc_pause_ms >= previous_gc_pause_ms
                    THEN gc_pause_ms - previous_gc_pause_ms ELSE 0 END)
            FROM annotated
            GROUP BY bucket_start_ms
            """);
    }

    private void recordMigration(Connection connection, Migration migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT OR IGNORE INTO schema_history(version, description, checksum, installed_at_ms)
            VALUES (?, ?, ?, ?)
            """)) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.description());
            statement.setString(3, migration.checksum());
            statement.setLong(4, clock.millis());
            statement.executeUpdate();
        }
    }

    private int readUserVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            if (!resultSet.next()) {
                throw new SQLException("PRAGMA user_version returned no row");
            }
            return resultSet.getInt(1);
        }
    }

    private static void validateExistingTableShapes(Connection connection) throws SQLException {
        Set<String> existingTables = objectNames(connection, "table");
        for (Map.Entry<String, Set<String>> required : REQUIRED_TABLE_COLUMNS.entrySet()) {
            if (!existingTables.contains(required.getKey())) {
                continue;
            }
            Set<String> actualColumns = tableColumns(connection, required.getKey());
            if (!actualColumns.containsAll(required.getValue())) {
                Set<String> missing = new LinkedHashSet<>(required.getValue());
                missing.removeAll(actualColumns);
                throw new SchemaValidationException(
                    "SQLite table " + required.getKey() + " is missing columns " + missing);
            }
        }
    }

    private static void validateSchema(Connection connection) throws SQLException {
        int version = readUserVersionStatic(connection);
        if (version != SUPPORTED_VERSION) {
            throw new SchemaValidationException(
                "SQLite schema version changed during validation: " + version);
        }
        Set<String> tables = objectNames(connection, "table");
        Set<String> missingTables = new LinkedHashSet<>(REQUIRED_TABLE_COLUMNS.keySet());
        missingTables.removeAll(tables);
        if (!missingTables.isEmpty()) {
            throw new SchemaValidationException("SQLite schema is missing tables " + missingTables);
        }
        validateExistingTableShapes(connection);

        Set<String> indexes = objectNames(connection, "index");
        Set<String> missingIndexes = new LinkedHashSet<>(REQUIRED_INDEX_COLUMNS.keySet());
        missingIndexes.removeAll(indexes);
        if (!missingIndexes.isEmpty()) {
            throw new SchemaValidationException("SQLite schema is missing indexes " + missingIndexes);
        }
        for (Map.Entry<String, List<String>> required : REQUIRED_INDEX_COLUMNS.entrySet()) {
            List<String> actualColumns = indexColumns(connection, required.getKey());
            if (!actualColumns.equals(required.getValue())) {
                throw new SchemaValidationException(
                    "SQLite index " + required.getKey() + " has columns " + actualColumns
                        + ", expected " + required.getValue());
            }
        }
    }

    private static Set<String> objectNames(Connection connection, String type) throws SQLException {
        Set<String> names = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type=?")) {
            statement.setString(1, type);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    names.add(resultSet.getString(1));
                }
            }
        }
        return names;
    }

    private static Set<String> tableColumns(Connection connection, String table) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        return columns;
    }

    private static List<String> indexColumns(Connection connection, String index) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA index_info(" + index + ")")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        return columns;
    }

    private static int readUserVersionStatic(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            if (!resultSet.next()) {
                throw new SQLException("PRAGMA user_version returned no row");
            }
            return resultSet.getInt(1);
        }
    }

    private static void setUserVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=" + version);
        }
    }

    private record Migration(
        int version,
        String description,
        String checksum,
        MigrationAction action
    ) {
    }

    @FunctionalInterface
    private interface MigrationAction {

        void apply(Connection connection) throws SQLException;
    }
}
