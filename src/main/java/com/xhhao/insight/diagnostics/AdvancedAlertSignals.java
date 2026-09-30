package com.xhhao.insight.diagnostics;

import com.xhhao.insight.alert.AlertEvaluationInput;
import com.xhhao.insight.consumer.PluginConsumerService;
import com.xhhao.insight.halo.DependencyStatus;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.tasks.HaloTaskMonitor;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdvancedAlertSignals {
    private final JvmPressureWindow gc;
    private final GcMemoryObserver memory;
    private final HaloTaskMonitor tasks;
    private final MonitoringState state;
    private final PluginConsumerService consumers;
    private volatile double sustained = Double.NaN;
    private long checkedAt;

    public AdvancedAlertSignals(JvmPressureWindow gc, GcMemoryObserver memory, HaloTaskMonitor tasks,
        MonitoringState state, PluginConsumerService consumers) {
        this.gc = gc; this.memory = memory; this.tasks = tasks; this.state = state; this.consumers = consumers;
    }

    public synchronized AlertEvaluationInput enrich(AlertEvaluationInput base) {
        long now = System.currentTimeMillis();
        gc.add(state.system());
        if (now - checkedAt > 60_000) {
            try { sustained = consumers.find(now - 900_000, now).stream().filter(p -> p.enabled() && p.sustained()).count(); }
            catch (RuntimeException ignored) { sustained = Double.NaN; }
            checkedAt = now;
        }
        var window = gc.windows(now).get(1);
        var heap = memory.snapshot(now);
        var task = tasks.snapshot();
        double failedTasks = !task.available() || now - task.atMs() > 180_000 ? Double.NaN
            : task.tasks().stream().filter(t -> ("FAILED".equals(t.state()) && now - t.completedAtMs() < 3_600_000)
                || ("RUNNING".equals(t.state()) && t.durationMs() > 30 * 60_000L)).count()
                + task.pools().stream().filter(p -> p.readyQueued() >= 100).count();
        boolean dependencyStateUnavailable = state.halo().dependencies().isEmpty()
            || state.haloRefreshStatus().failed();
        List<DependencyStatus> failedDependencies = dependencyStateUnavailable
            ? List.of()
            : state.halo().dependencies().stream()
                .filter(d -> "critical".equals(d.status()) || "warning".equals(d.status()))
                .toList();
        double dependencies = dependencyStateUnavailable
            ? Double.NaN
            : failedDependencies.size();
        return new AlertEvaluationInput(base.systemCpuPercent(), base.heapUsedPercent(), base.diskUsedPercent(),
            base.requestErrorRatePercent(), base.requestP95Millis(), base.deadlockedThreadCount(),
            window.sufficient() ? window.overheadPercent() : Double.NaN, sustained, dependencies, failedTasks,
            heap.sufficient() ? (heap.sustainedGrowth() ? 1 : 0) : Double.NaN,
            failedDependencies);
    }
}
