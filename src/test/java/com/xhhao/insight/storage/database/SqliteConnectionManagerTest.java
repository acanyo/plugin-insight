package com.xhhao.insight.storage.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.insight.storage.StorageException;
import com.xhhao.insight.storage.health.StorageHealth;
import com.xhhao.insight.storage.health.StorageState;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class SqliteConnectionManagerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void inspectionFailureMarksAvailableStorageAsDegraded() throws Exception {
        StorageHealth health = new StorageHealth();
        health.markAvailable("test");
        SqliteConnectionManager manager = new SqliteConnectionManager(
            temporaryDirectory.resolve("insight.sqlite"), health);
        Connection connection = mock(Connection.class);
        when(connection.isClosed()).thenThrow(new SQLException("connection inspection failed"));
        ReflectionTestUtils.setField(manager, "writeConnection", connection);

        assertThatThrownBy(() -> manager.read(ignored -> 1))
            .isInstanceOf(StorageException.class);
        assertThat(health.snapshot().state()).isEqualTo(StorageState.DEGRADED);
    }
}
