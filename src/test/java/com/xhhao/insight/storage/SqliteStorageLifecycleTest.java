package com.xhhao.insight.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xhhao.insight.storage.database.SchemaMigrator;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.health.StorageState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteStorageLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteStorageLifecycle storage;

    @AfterEach
    void closeStorage() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    void initializesVersionedWalDatabase() {
        storage = new SqliteStorageLifecycle(temporaryDirectory.resolve("insight.sqlite"));

        assertThat(storage.initialize()).isTrue();
        assertThat(storage.health().state()).isEqualTo(StorageState.AVAILABLE);

        storage.connectionManager().read(connection -> {
            assertThat(readTextPragma(connection, "journal_mode")).isEqualToIgnoringCase("wal");
            assertThat(readIntPragma(connection, "synchronous")).isEqualTo(1);
            assertThat(readIntPragma(connection, "busy_timeout")).isEqualTo(5_000);
            assertThat(readIntPragma(connection, "user_version"))
                .isEqualTo(SchemaMigrator.SUPPORTED_VERSION);

            Set<String> tables = new HashSet<>();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("""
                     SELECT name FROM sqlite_master WHERE type='table'
                     """)) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString(1));
                }
            }
            assertThat(tables).contains(
                "schema_history",
                "system_sample_15s",
                "system_metric_minute",
                "plugin_metric_minute",
                "plugin_resource_minute",
                "resource_attribution_minute",
                "request_metric_minute",
                "monitor_event",
                "alert_incident"
            );
            return null;
        });
    }

    @Test
    void newerSchemaIsNotQuarantinedOrRecreated() {
        Path database = temporaryDirectory.resolve("insight.sqlite");
        storage = new SqliteStorageLifecycle(database);
        assertThat(storage.initialize()).isTrue();
        storage.connectionManager().write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA user_version="
                    + (SchemaMigrator.SUPPORTED_VERSION + 1));
            }
            return null;
        });
        storage.close();

        storage = new SqliteStorageLifecycle(database);
        assertThat(storage.initialize()).isFalse();
        assertThat(storage.health().state()).isEqualTo(StorageState.INCOMPATIBLE);
        assertThat(database).exists();
        assertThat(temporaryDirectory.resolve("corrupt")).doesNotExist();
    }

    @Test
    void corruptDatabaseIsQuarantinedBeforeCreatingFreshDatabase() throws Exception {
        Path database = temporaryDirectory.resolve("insight.sqlite");
        Files.writeString(database, "this is not sqlite");
        storage = new SqliteStorageLifecycle(database);

        assertThat(storage.initialize()).isTrue();
        assertThat(storage.health().state()).isEqualTo(StorageState.AVAILABLE);
        assertThat(storage.health().message()).doesNotContain(temporaryDirectory.toString());
        assertThat(database).exists();
        try (var quarantined = Files.list(temporaryDirectory.resolve("corrupt"))) {
            assertThat(quarantined.filter(path -> path.getFileName().toString().endsWith(".sqlite")))
                .hasSize(1);
        }
        int version = storage.connectionManager().read(connection ->
            readIntPragma(connection, "user_version"));
        assertThat(version).isEqualTo(SchemaMigrator.SUPPORTED_VERSION);
    }

    @Test
    void repairsMissingCurrentVersionTablesAndIndexesWithoutDiscardingData() {
        Path database = temporaryDirectory.resolve("insight.sqlite");
        storage = new SqliteStorageLifecycle(database);
        assertThat(storage.initialize()).isTrue();
        storage.connectionManager().write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                    INSERT INTO system_sample_15s VALUES (
                        1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                    )
                    """);
                statement.execute("DROP TABLE monitor_event");
                statement.execute("DROP INDEX idx_alert_incident_fingerprint");
            }
            return null;
        });
        storage.close();

        storage = new SqliteStorageLifecycle(database);
        assertThat(storage.initialize()).isTrue();
        storage.connectionManager().read(connection -> {
            assertThat(objectExists(connection, "table", "monitor_event")).isTrue();
            assertThat(objectExists(connection, "index", "idx_monitor_event_time_id")).isTrue();
            assertThat(objectExists(connection, "index", "idx_alert_incident_fingerprint")).isTrue();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                     "SELECT count(*) FROM system_sample_15s WHERE sampled_at_ms=1")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
            return null;
        });
        assertThat(temporaryDirectory.resolve("invalid")).doesNotExist();
    }

    @Test
    void migratesVersionOneAndBackfillsSystemMinuteHistory() {
        Path database = temporaryDirectory.resolve("insight.sqlite");
        storage = new SqliteStorageLifecycle(database);
        assertThat(storage.initialize()).isTrue();
        storage.connectionManager().write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE system_metric_minute");
                statement.execute("DELETE FROM schema_history WHERE version=2");
                statement.execute("PRAGMA user_version=1");
                statement.execute("""
                    INSERT INTO system_sample_15s VALUES
                    (59000, 59000, 10, 20, 1, 10, 100, 5, 200, 500,
                        50, 1000, 10, 5, 12, 0, 5, 7),
                    (60000, 60000, 20, 30, 2, 20, 100, 6, 220, 500,
                        60, 1000, 11, 5, 13, 0, 7, 10),
                    (75000, 75000, 40, 50, 3, 40, 100, 8, 240, 500,
                        70, 1000, 13, 6, 15, 0, 10, 16)
                    """);
            }
            return null;
        });
        storage.close();

        storage = new SqliteStorageLifecycle(database);

        assertThat(storage.initialize()).isTrue();
        storage.connectionManager().read(connection -> {
            assertThat(readIntPragma(connection, "user_version"))
                .isEqualTo(SchemaMigrator.SUPPORTED_VERSION);
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("""
                     SELECT sample_count, process_cpu_load_avg, process_cpu_load_max,
                            heap_used_bytes_last, gc_count_delta, gc_pause_ms_delta
                     FROM system_metric_minute WHERE bucket_start_ms=60000
                     """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt("sample_count")).isEqualTo(2);
                assertThat(resultSet.getDouble("process_cpu_load_avg")).isEqualTo(30);
                assertThat(resultSet.getDouble("process_cpu_load_max")).isEqualTo(40);
                assertThat(resultSet.getLong("heap_used_bytes_last")).isEqualTo(40);
                assertThat(resultSet.getLong("gc_count_delta")).isEqualTo(5);
                assertThat(resultSet.getLong("gc_pause_ms_delta")).isEqualTo(9);
            }
            return null;
        });
    }

    @Test
    void migratesVersionTwoToPluginResourceSchemaWithoutChangingExistingMetrics() {
        Path database = temporaryDirectory.resolve("insight.sqlite");
        storage = new SqliteStorageLifecycle(database);
        assertThat(storage.initialize()).isTrue();
        storage.connectionManager().write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE plugin_resource_minute");
                statement.execute("DROP TABLE resource_attribution_minute");
                statement.execute("DELETE FROM schema_history WHERE version=3");
                statement.execute("PRAGMA user_version=2");
                statement.execute("""
                    INSERT INTO plugin_metric_minute VALUES (
                        60000, 'plugin-a', 0, 0, 0, 0, 2, 0, 1000, 500, 0, 0, 0, 0
                    )
                    """);
            }
            return null;
        });
        storage.close();

        storage = new SqliteStorageLifecycle(database);

        assertThat(storage.initialize()).isTrue();
        storage.connectionManager().read(connection -> {
            assertThat(readIntPragma(connection, "user_version"))
                .isEqualTo(SchemaMigrator.SUPPORTED_VERSION);
            assertThat(objectExists(connection, "table", "plugin_resource_minute")).isTrue();
            assertThat(objectExists(connection, "table", "resource_attribution_minute")).isTrue();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("""
                     SELECT request_count FROM plugin_metric_minute
                     WHERE bucket_start_ms=60000 AND plugin_id='plugin-a'
                     """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong(1)).isEqualTo(2);
            }
            return null;
        });
    }

    @Test
    void malformedCurrentVersionTableIsQuarantinedBeforeSafeRebuild() throws Exception {
        Path database = temporaryDirectory.resolve("insight.sqlite");
        storage = new SqliteStorageLifecycle(database);
        assertThat(storage.initialize()).isTrue();
        storage.connectionManager().write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE alert_incident");
                statement.execute("CREATE TABLE alert_incident(id TEXT PRIMARY KEY)");
            }
            return null;
        });
        storage.close();

        storage = new SqliteStorageLifecycle(database);
        assertThat(storage.initialize()).isTrue();
        assertThat(storage.health().state()).isEqualTo(StorageState.AVAILABLE);
        try (var quarantined = Files.list(temporaryDirectory.resolve("invalid"))) {
            assertThat(quarantined.filter(path -> path.getFileName().toString().endsWith(".sqlite")))
                .hasSize(1);
        }
        storage.connectionManager().read(connection -> {
            assertThat(objectExists(connection, "table", "alert_incident")).isTrue();
            return null;
        });
    }

    @Test
    void transientRuntimeDegradationCanReopenAndBecomeAvailable() {
        storage = new SqliteStorageLifecycle(temporaryDirectory.resolve("insight.sqlite"));
        assertThat(storage.initialize()).isTrue();
        storage.connectionManager().health().markDegraded("transient test failure");
        storage.connectionManager().closeWriteConnection();

        assertThat(storage.recheckAndRecover()).isTrue();
        assertThat(storage.health().state()).isEqualTo(StorageState.AVAILABLE);
        assertThat(storage.connectionManager().isOpen()).isTrue();
    }

    @Test
    void unexpectedClosedConnectionMarksAvailableStorageAsDegraded() {
        storage = new SqliteStorageLifecycle(temporaryDirectory.resolve("insight.sqlite"));
        assertThat(storage.initialize()).isTrue();

        storage.connectionManager().closeWriteConnection();

        assertThatThrownBy(() -> storage.connectionManager().read(connection -> 1))
            .isInstanceOf(StorageException.class);
        assertThat(storage.health().state()).isEqualTo(StorageState.DEGRADED);
    }

    @Test
    void readTransactionKeepsOneWalSnapshotAcrossStatements() {
        storage = new SqliteStorageLifecycle(temporaryDirectory.resolve("insight.sqlite"));
        assertThat(storage.initialize()).isTrue();
        insertEvent("first");

        List<Integer> counts = storage.connectionManager().readTransaction(connection -> {
            int before = countEvents(connection);
            insertEvent("second");
            int afterConcurrentCommit = countEvents(connection);
            return List.of(before, afterConcurrentCommit);
        });

        assertThat(counts).containsExactly(1, 1);
        assertThat(storage.connectionManager().read(
            SqliteStorageLifecycleTest::countEvents)).isEqualTo(2);
    }

    private void insertEvent(String id) {
        storage.connectionManager().write(connection -> {
            try (var statement = connection.prepareStatement("""
                INSERT INTO monitor_event(
                    id, occurred_at_ms, type, severity, source, title, summary, fingerprint,
                    first_seen_at_ms, last_seen_at_ms, occurrence_count, details_json
                ) VALUES (?, 1, 'test', 'info', 'test', 'test', 'test', ?, 1, 1, 1, NULL)
                """)) {
                statement.setString(1, id);
                statement.setString(2, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static int countEvents(java.sql.Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM monitor_event")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private static boolean objectExists(
        java.sql.Connection connection,
        String type,
        String name
    ) throws SQLException {
        try (var statement = connection.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type=? AND name=?")) {
            statement.setString(1, type);
            statement.setString(2, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static int readIntPragma(java.sql.Connection connection, String pragma)
        throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA " + pragma)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private static String readTextPragma(java.sql.Connection connection, String pragma)
        throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA " + pragma)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
