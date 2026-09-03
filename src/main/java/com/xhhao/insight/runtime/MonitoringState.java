package com.xhhao.insight.runtime;

import com.xhhao.insight.halo.HaloRuntimeSnapshot;
import com.xhhao.insight.metrics.RequestWindowSnapshot;
import com.xhhao.insight.metrics.SystemSnapshot;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class MonitoringState {

    private final AtomicReference<SystemSnapshot> system = new AtomicReference<>();
    private final AtomicReference<RequestWindowSnapshot> requests = new AtomicReference<>(
        RequestWindowSnapshot.empty(System.currentTimeMillis()));
    private final AtomicReference<HaloRuntimeSnapshot> halo = new AtomicReference<>(
        new HaloRuntimeSnapshot("unknown", null, List.of(), List.of()));
    private final AtomicReference<BackgroundTaskStatus> haloRefresh = new AtomicReference<>(
        BackgroundTaskStatus.pending());
    private final AtomicReference<BackgroundTaskStatus> retention = new AtomicReference<>(
        BackgroundTaskStatus.pending());
    private volatile boolean running;
    private volatile boolean storageWritePaused;
    private volatile long lastCollectedAtMs;
    private volatile String lastCollectionError;

    public SystemSnapshot system() {
        return system.get();
    }

    public boolean updateSystem(SystemSnapshot snapshot) {
        boolean recovered = lastCollectionError != null;
        system.set(snapshot);
        lastCollectedAtMs = snapshot.timestampMs();
        lastCollectionError = null;
        return recovered;
    }

    public RequestWindowSnapshot requests() {
        return requests.get();
    }

    public void updateRequests(RequestWindowSnapshot snapshot) {
        requests.set(snapshot);
    }

    public HaloRuntimeSnapshot halo() {
        return halo.get();
    }

    public void updateHalo(HaloRuntimeSnapshot snapshot) {
        halo.set(snapshot);
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean storageWritePaused() {
        return storageWritePaused;
    }

    public void setStorageWritePaused(boolean storageWritePaused) {
        this.storageWritePaused = storageWritePaused;
    }

    public long lastCollectedAtMs() {
        return lastCollectedAtMs;
    }

    public String lastCollectionError() {
        return lastCollectionError;
    }

    public boolean collectionFailed(Throwable error) {
        boolean firstFailure = lastCollectionError == null;
        lastCollectionError = FailureSummary.of(error);
        return firstFailure;
    }

    public BackgroundTaskStatus haloRefreshStatus() {
        return haloRefresh.get();
    }

    public boolean haloRefreshSucceeded(long timestampMs) {
        return markSucceeded(haloRefresh, timestampMs);
    }

    public boolean haloRefreshFailed(long timestampMs, Throwable failure) {
        return markFailed(haloRefresh, timestampMs, failure);
    }

    public BackgroundTaskStatus retentionStatus() {
        return retention.get();
    }

    public boolean retentionSucceeded(long timestampMs) {
        return markSucceeded(retention, timestampMs);
    }

    public boolean retentionFailed(long timestampMs, Throwable failure) {
        return markFailed(retention, timestampMs, failure);
    }

    private static boolean markSucceeded(
        AtomicReference<BackgroundTaskStatus> target,
        long timestampMs
    ) {
        while (true) {
            BackgroundTaskStatus previous = target.get();
            if (target.compareAndSet(previous, previous.succeeded(timestampMs))) {
                return previous.failed();
            }
        }
    }

    private static boolean markFailed(
        AtomicReference<BackgroundTaskStatus> target,
        long timestampMs,
        Throwable failure
    ) {
        while (true) {
            BackgroundTaskStatus previous = target.get();
            if (target.compareAndSet(previous, previous.failed(timestampMs, failure))) {
                return !previous.failed();
            }
        }
    }
}
