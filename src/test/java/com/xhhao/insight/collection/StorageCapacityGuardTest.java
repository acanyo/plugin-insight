package com.xhhao.insight.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.capacity.StorageCapacityGuard;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageCapacityGuardTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteStorageLifecycle storage;
    private StorageCapacityGuard guard;

    @BeforeEach
    void setUp() {
        storage = new SqliteStorageLifecycle(temporaryDirectory.resolve("insight.sqlite"));
        assertThat(storage.initialize()).isTrue();
        guard = new StorageCapacityGuard(storage.connectionManager());
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void deletedPagesDoNotKeepLogicalCapacityPaused() {
        storage.connectionManager().write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO monitor_event(
                    id, occurred_at_ms, type, severity, source, title, summary, fingerprint,
                    first_seen_at_ms, last_seen_at_ms, occurrence_count, details_json
                ) VALUES (?, ?, 'test', 'info', 'test', 'test', 'test', ?, ?, ?, 1, ?)
                """)) {
                String payload = "x".repeat(4_096);
                for (int index = 0; index < 400; index++) {
                    statement.setString(1, "event-" + index);
                    statement.setLong(2, index + 1);
                    statement.setString(3, "fingerprint-" + index);
                    statement.setLong(4, index + 1);
                    statement.setLong(5, index + 1);
                    statement.setString(6, payload);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
        assertThat(guard.exceedsMegabytes(1)).isTrue();

        storage.connectionManager().write(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM monitor_event");
            }
            return null;
        });

        assertThat(guard.currentBytes()).isGreaterThanOrEqualTo(1_024L * 1_024L);
        assertThat(guard.logicalBytes()).isLessThan(1_024L * 1_024L);
        assertThat(guard.exceedsMegabytes(1)).isFalse();
    }
}
