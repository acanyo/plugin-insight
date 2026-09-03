package com.xhhao.insight.collection;

import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.health.StorageState;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Rate-limits runtime recovery checks for degraded SQLite storage.
 */
@Component
final class StorageRecoveryMonitor {

    private static final long RECHECK_INTERVAL_MS = Duration.ofMinutes(1).toMillis();

    private final SqliteStorageLifecycle storage;
    private long lastAttemptMs = Long.MIN_VALUE;

    @Autowired
    public StorageRecoveryMonitor(SqliteStorageLifecycle storage) {
        this.storage = storage;
    }

    synchronized boolean recheckIfDue(long nowMs) {
        if (storage.health().state() != StorageState.DEGRADED) {
            return false;
        }
        if (lastAttemptMs != Long.MIN_VALUE
            && nowMs >= lastAttemptMs
            && nowMs - lastAttemptMs < RECHECK_INTERVAL_MS) {
            return false;
        }
        lastAttemptMs = nowMs;
        return storage.recheckAndRecover();
    }
}
