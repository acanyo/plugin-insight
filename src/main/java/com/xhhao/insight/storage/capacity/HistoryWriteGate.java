package com.xhhao.insight.storage.capacity;

import com.xhhao.insight.config.InsightSettingsService;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Applies one fail-closed admission policy to every runtime historical SQLite write.
 *
 * <p>The gate deliberately has no dependency on the event recorder: rejecting a write must not
 * recursively create another history event. Schema migration and retention do not use this gate
 * because opening and shrinking the database must remain possible.</p>
 */
@Slf4j
@Component
public class HistoryWriteGate {

    private final SqliteStorageLifecycle storage;
    private final StorageCapacityGuard capacityGuard;
    private final InsightSettingsService settingsService;
    private final MonitoringState state;
    private final AtomicBoolean capacityCheckFailureLogged = new AtomicBoolean();

    public HistoryWriteGate(
        SqliteStorageLifecycle storage,
        StorageCapacityGuard capacityGuard,
        InsightSettingsService settingsService,
        MonitoringState state
    ) {
        this.storage = storage;
        this.capacityGuard = capacityGuard;
        this.settingsService = settingsService;
        this.state = state;
    }

    /**
     * Returns whether one historical write may proceed and refreshes the shared pause state.
     */
    public boolean allowsWrite() {
        if (!storage.health().available()) {
            return false;
        }
        try {
            int limitMegabytes = settingsService.collection().getMaxDatabaseMegabytes();
            boolean paused = capacityGuard.exceedsMegabytes(limitMegabytes);
            state.setStorageWritePaused(paused);
            capacityCheckFailureLogged.set(false);
            return !paused;
        } catch (RuntimeException error) {
            state.setStorageWritePaused(true);
            if (capacityCheckFailureLogged.compareAndSet(false, true)) {
                log.warn("Unable to evaluate Insight history capacity; historical writes are "
                    + "paused ({})", error.getClass().getSimpleName());
            }
            return false;
        }
    }
}
