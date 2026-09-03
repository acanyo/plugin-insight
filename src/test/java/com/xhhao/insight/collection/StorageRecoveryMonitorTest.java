package com.xhhao.insight.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.health.StorageState;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageRecoveryMonitorTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteStorageLifecycle storage;

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    void retriesDegradedStorageAtMostOncePerMinuteAndRecoversIt() {
        storage = new SqliteStorageLifecycle(temporaryDirectory.resolve("insight.sqlite"));
        assertThat(storage.initialize()).isTrue();
        StorageRecoveryMonitor monitor = new StorageRecoveryMonitor(storage);

        storage.connectionManager().health().markDegraded("first transient failure");
        storage.connectionManager().closeWriteConnection();
        assertThat(monitor.recheckIfDue(1_000)).isTrue();
        assertThat(storage.health().state()).isEqualTo(StorageState.AVAILABLE);

        storage.connectionManager().health().markDegraded("second transient failure");
        storage.connectionManager().closeWriteConnection();
        assertThat(monitor.recheckIfDue(30_000)).isFalse();
        assertThat(storage.health().state()).isEqualTo(StorageState.DEGRADED);

        assertThat(monitor.recheckIfDue(61_000)).isTrue();
        assertThat(storage.health().state()).isEqualTo(StorageState.AVAILABLE);
    }
}
