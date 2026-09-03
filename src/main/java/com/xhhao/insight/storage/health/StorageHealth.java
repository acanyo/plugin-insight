package com.xhhao.insight.storage.health;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class StorageHealth {

    private final Clock clock;
    private final AtomicReference<StorageHealthSnapshot> snapshot;

    public StorageHealth() {
        this(Clock.systemUTC());
    }

    StorageHealth(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.snapshot = new AtomicReference<>(
            new StorageHealthSnapshot(StorageState.NEW, "Storage has not been initialized",
                clock.millis(), 0)
        );
    }

    public StorageHealthSnapshot snapshot() {
        return snapshot.get();
    }

    public void markInitializing() {
        update(StorageState.INITIALIZING, "Storage initialization is in progress", false);
    }

    public void markAvailable(String message) {
        update(StorageState.AVAILABLE, message, false);
    }

    public void markDegraded(String message) {
        update(StorageState.DEGRADED, message, true);
    }

    public void markIncompatible(String message) {
        update(StorageState.INCOMPATIBLE, message, true);
    }

    public void markClosed() {
        update(StorageState.CLOSED, "Storage is closed", false);
    }

    private void update(StorageState state, String message, boolean failure) {
        Objects.requireNonNull(message, "message must not be null");
        snapshot.updateAndGet(current -> new StorageHealthSnapshot(
            state,
            message,
            clock.millis(),
            current.failureCount() + (failure ? 1 : 0)
        ));
    }
}
