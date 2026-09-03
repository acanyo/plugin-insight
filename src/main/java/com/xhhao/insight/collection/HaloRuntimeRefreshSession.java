package com.xhhao.insight.collection;

import com.xhhao.insight.event.EventRecorder;
import com.xhhao.insight.halo.HaloRuntimeService;
import com.xhhao.insight.halo.HaloRuntimeSnapshot;
import com.xhhao.insight.runtime.MonitoringState;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Owns at most one cancellable Halo runtime refresh for an active plugin lifecycle. */
@Slf4j
@Component
public final class HaloRuntimeRefreshSession {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HaloRuntimeService runtimeService;
    private final MonitoringState state;
    private final EventRecorder events;
    private final Clock clock;
    private final Duration timeout;
    private final Scheduler timeoutScheduler;

    private boolean active;
    private long generation;
    private Object activeRefresh;
    private Disposable subscription;

    @Autowired
    public HaloRuntimeRefreshSession(
        HaloRuntimeService runtimeService,
        MonitoringState state,
        EventRecorder events
    ) {
        this(runtimeService, state, events, Clock.systemUTC(), DEFAULT_TIMEOUT,
            Schedulers.parallel());
    }

    HaloRuntimeRefreshSession(
        HaloRuntimeService runtimeService,
        MonitoringState state,
        EventRecorder events,
        Clock clock,
        Duration timeout,
        Scheduler timeoutScheduler
    ) {
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.timeoutScheduler = Objects.requireNonNull(
            timeoutScheduler, "timeoutScheduler must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public synchronized void start() {
        if (active) {
            return;
        }
        generation++;
        active = true;
    }

    /**
     * Starts a bounded refresh unless this lifecycle already has one in flight.
     *
     * @return {@code true} when a refresh was started
     */
    public boolean refresh() {
        long refreshGeneration;
        Object refreshToken;
        synchronized (this) {
            if (!active || activeRefresh != null) {
                return false;
            }
            refreshGeneration = generation;
            refreshToken = new Object();
            activeRefresh = refreshToken;
        }

        Disposable started;
        try {
            started = runtimeService.snapshot()
                .timeout(timeout, timeoutScheduler)
                .subscribe(
                    snapshot -> completeSuccessfully(
                        refreshGeneration, refreshToken, snapshot),
                    failure -> completeWithFailure(
                        refreshGeneration, refreshToken, failure)
                );
        } catch (RuntimeException failure) {
            completeWithFailure(refreshGeneration, refreshToken, failure);
            return true;
        }

        synchronized (this) {
            if (!accepts(refreshGeneration, refreshToken)) {
                started.dispose();
            } else {
                subscription = started;
            }
        }
        return true;
    }

    public synchronized void stop() {
        if (!active && activeRefresh == null && subscription == null) {
            return;
        }
        active = false;
        generation++;
        activeRefresh = null;
        if (subscription != null) {
            subscription.dispose();
            subscription = null;
        }
    }

    private void completeSuccessfully(
        long refreshGeneration,
        Object refreshToken,
        HaloRuntimeSnapshot snapshot
    ) {
        boolean recovered;
        synchronized (this) {
            if (!accepts(refreshGeneration, refreshToken)) {
                return;
            }
            clearCompletedRefresh();
            state.updateHalo(snapshot);
            recovered = state.haloRefreshSucceeded(clock.millis());
        }
        if (recovered) {
            events.record("runtime", "info", "halo", "Halo 运行状态刷新已恢复",
                "插件、数据库、搜索和附件存储状态刷新恢复正常");
        }
    }

    private void completeWithFailure(
        long refreshGeneration,
        Object refreshToken,
        Throwable failure
    ) {
        boolean newlyFailed;
        synchronized (this) {
            if (!accepts(refreshGeneration, refreshToken)) {
                return;
            }
            clearCompletedRefresh();
            newlyFailed = state.haloRefreshFailed(clock.millis(), failure);
        }
        if (newlyFailed) {
            events.recordFailure("runtime", "warning", "halo", "Halo 运行状态刷新异常",
                "插件、数据库、搜索和附件存储状态本轮未完成刷新",
                "halo_runtime_refresh", failure);
        }
        log.warn("Failed to refresh Halo runtime status", failure);
    }

    private boolean accepts(long refreshGeneration, Object refreshToken) {
        return active && generation == refreshGeneration && activeRefresh == refreshToken;
    }

    private void clearCompletedRefresh() {
        activeRefresh = null;
        subscription = null;
    }
}
