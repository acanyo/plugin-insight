package com.xhhao.insight.diagnostics;

import com.xhhao.insight.config.InsightSettingsService;
import com.xhhao.insight.optimization.OptimizationObservationService;
import com.xhhao.insight.optimization.OptimizationRuntimePolicy;
import com.xhhao.insight.overhead.InsightOverheadMetrics;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.tasks.HaloTaskMonitor;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DiagnosticsLifecycle {
    private final MonitoringState state;
    private final JvmPressureWindow gc;
    private final GcMemoryObserver memory;
    private final ThreadDiagnostics threads;
    private final HaloTaskMonitor tasks;
    private final OptimizationObservationService observations;
    private final JfrDiagnostics jfr;
    private final DiagnosticRecords records;
    private final OptimizationRuntimePolicy policy;
    private final InsightSettingsService settings;
    private ScheduledExecutorService executor;
    private long lastMinute;
    private long lastThreadSampleMs;
    private volatile String historyError;
    private InsightOverheadMetrics overhead;

    @Autowired
    void measureOverhead(InsightOverheadMetrics overhead) {
        this.overhead = overhead;
    }

    public DiagnosticsLifecycle(
            MonitoringState state,
            JvmPressureWindow gc,
            GcMemoryObserver memory,
            ThreadDiagnostics threads,
            HaloTaskMonitor tasks,
            OptimizationObservationService observations,
            JfrDiagnostics jfr,
            DiagnosticRecords records,
            OptimizationRuntimePolicy policy,
            InsightSettingsService settings) {
        this.state = state;
        this.gc = gc;
        this.memory = memory;
        this.threads = threads;
        this.tasks = tasks;
        this.observations = observations;
        this.jfr = jfr;
        this.records = records;
        this.policy = policy;
        this.settings = settings;
    }

    public synchronized void start() {
        if (executor != null && !executor.isShutdown()) {
            return;
        }
        memory.start();
        threads.reset();
        lastThreadSampleMs = 0;
        lastMinute = 0;
        runSafely(
                () -> {
                    observations.interruptPending();
                    jfr.recover();
                });
        executor =
                Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().name("insight-diagnostics-", 0).daemon(true).factory());
        executor.scheduleWithFixedDelay(this::tick, 1, 15, TimeUnit.SECONDS);
    }

    private void tick() {
        tick(System.currentTimeMillis());
    }

    void tick(long now) {
        try (var scope =
                overhead == null
                        ? null
                        : overhead.begin(InsightOverheadMetrics.Operation.DIAGNOSTICS)) {
            boolean successful = tickSafely(now);
            if (!successful && scope != null) scope.failed();
        }
    }

    private boolean tickSafely(long now) {
        boolean samplingSucceeded =
                runSafely(
                        () -> {
                            gc.add(state.system());
                            var configured = settings.collection();
                            if (lastThreadSampleMs == 0
                                    || now - lastThreadSampleMs
                                            >= policy.sampleIntervalMs(configured)) {
                                threads.sample(now, policy.pluginResourceMaxThreads(configured));
                                lastThreadSampleMs = now;
                            }
                        });
        boolean observationsSucceeded = runSafely(observations::tick, now);
        boolean jfrSucceeded = runSafely(jfr::tick);
        boolean historySucceeded = true;
        if (now - lastMinute >= 60_000) {
            historySucceeded =
                    runSafely(
                            () -> {
                                tasks.sample(now);
                                records.save(
                                        "memory",
                                        Long.toString(now / 60_000),
                                        now,
                                        memory.snapshot(now));
                                historyError = null;
                            });
            lastMinute = now;
        }
        return samplingSucceeded && observationsSucceeded && jfrSucceeded && historySucceeded;
    }

    public String historyError() {
        return historyError;
    }

    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        jfr.stop();
        memory.stop();
    }

    private boolean runSafely(Action action) {
        try {
            action.run();
            return true;
        } catch (Exception error) {
            historyError = "部分诊断采集或历史存储暂不可用，请检查存储和 Halo 状态";
            log.debug(
                    "Insight diagnostic operation deferred ({})", error.getClass().getSimpleName());
            return false;
        }
    }

    private boolean runSafely(java.util.function.LongConsumer action, long now) {
        return runSafely(() -> action.accept(now));
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }
}
