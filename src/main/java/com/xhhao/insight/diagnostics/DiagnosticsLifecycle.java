package com.xhhao.insight.diagnostics;

import com.xhhao.insight.optimization.OptimizationObservationService;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.tasks.HaloTaskMonitor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    private ScheduledExecutorService executor;
    private long lastMinute;
    private volatile String historyError;

    public DiagnosticsLifecycle(MonitoringState state, JvmPressureWindow gc, GcMemoryObserver memory,
        ThreadDiagnostics threads, HaloTaskMonitor tasks, OptimizationObservationService observations,
        JfrDiagnostics jfr, DiagnosticRecords records) {
        this.state = state; this.gc = gc; this.memory = memory; this.threads = threads;
        this.tasks = tasks; this.observations = observations; this.jfr = jfr; this.records = records;
    }

    public synchronized void start() {
        if (executor != null && !executor.isShutdown()) { return; }
        memory.start();
        runSafely(() -> { observations.interruptPending(); jfr.recover(); });
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("insight-diagnostics-", 0).factory());
        executor.scheduleWithFixedDelay(this::tick, 1, 15, TimeUnit.SECONDS);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        runSafely(() -> { gc.add(state.system()); threads.sample(now); });
        runSafely(observations::tick, now);
        runSafely(jfr::tick);
        if (now - lastMinute >= 60_000) {
            runSafely(() -> {
                tasks.sample(now);
                records.save("memory", Long.toString(now / 60_000), now, memory.snapshot(now));
                historyError = null;
            });
            lastMinute = now;
        }
    }

    public String historyError() { return historyError; }

    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            try { executor.awaitTermination(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        jfr.stop(); memory.stop();
    }

    private void runSafely(Action action) {
        try { action.run(); }
        catch (Exception error) {
            historyError = "部分诊断采集或历史存储暂不可用，请检查存储和 Halo 状态";
            log.debug("Insight diagnostic operation deferred ({})", error.getClass().getSimpleName());
        }
    }
    private void runSafely(java.util.function.LongConsumer action, long now) { runSafely(() -> action.accept(now)); }
    @FunctionalInterface private interface Action { void run() throws Exception; }
}
