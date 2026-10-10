package com.xhhao.insight.diagnostics;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ThreadDiagnostics {
    private final ThreadMXBean threads;
    private Map<Long, Long> previous = Map.of();
    private Map<Long, Long> deltas = Map.of();
    private long lastAt;
    private long observedMs;
    private int inspected;

    @Autowired
    public ThreadDiagnostics() {
        this(ManagementFactory.getThreadMXBean());
    }

    ThreadDiagnostics(ThreadMXBean threads) {
        this.threads = threads;
    }

    public synchronized void sample(long now) {
        sample(now, 512);
    }

    public synchronized void sample(long now, int maximumThreads) {
        if (!threads.isThreadCpuTimeSupported() || !threads.isThreadCpuTimeEnabled()) { return; }
        var counters = new HashMap<Long, Long>();
        var changes = new HashMap<Long, Long>();
        long[] ids = threads.getAllThreadIds();
        inspected = Math.min(ids.length, Math.clamp(maximumThreads, 32, 512));
        long[] sampledIds = ids.length == inspected ? ids : Arrays.copyOf(ids, inspected);
        long[] cpuTimes = cpuTimes(sampledIds);
        for (int i = 0; i < inspected; i++) {
            long cpu = cpuTimes[i];
            if (cpu < 0) { continue; }
            counters.put(ids[i], cpu);
            var old = previous.get(ids[i]);
            if (old != null && cpu >= old) { changes.put(ids[i], cpu - old); }
        }
        observedMs = lastAt == 0 ? 0 : now - lastAt; lastAt = now;
        previous = counters; deltas = changes;
    }

    public synchronized void reset() {
        previous = Map.of(); deltas = Map.of();
        lastAt = 0; observedMs = 0; inspected = 0;
    }

    private long[] cpuTimes(long[] ids) {
        if (threads instanceof com.sun.management.ThreadMXBean extended) {
            return extended.getThreadCpuTime(ids);
        }
        long[] values = new long[ids.length];
        for (int index = 0; index < ids.length; index++) {
            values[index] = threads.getThreadCpuTime(ids[index]);
        }
        return values;
    }

    public synchronized Snapshot snapshot() {
        long[] deadlocked;
        try { deadlocked = threads.findDeadlockedThreads(); }
        catch (UnsupportedOperationException unsupported) { deadlocked = threads.findMonitorDeadlockedThreads(); }
        Set<Long> dead = deadlocked == null ? Set.of() : Arrays.stream(deadlocked).boxed().collect(Collectors.toSet());
        var ids = new java.util.LinkedHashSet<Long>();
        dead.stream().limit(100).forEach(ids::add);
        deltas.entrySet().stream().sorted(Map.Entry.<Long, Long>comparingByValue().reversed()).limit(30)
            .map(Map.Entry::getKey).forEach(ids::add);
        if (ids.isEmpty()) { Arrays.stream(threads.getAllThreadIds()).limit(30).forEach(ids::add); }
        var values = new ArrayList<ThreadView>();
        for (var info : threads.getThreadInfo(ids.stream().mapToLong(Long::longValue).toArray(), 32)) {
            if (info == null) { continue; }
            long cpu = deltas.getOrDefault(info.getThreadId(), 0L);
            values.add(new ThreadView(info.getThreadId(), info.getThreadName(), info.getThreadState().name(),
                dead.contains(info.getThreadId()), observedMs > 0 ? cpu / (observedMs * 10_000d) : 0,
                info.getLockName(), info.getLockOwnerName(),
                Arrays.stream(info.getStackTrace()).map(StackTraceElement::toString).toList()));
        }
        values.sort(Comparator.comparing(ThreadView::deadlocked).reversed().thenComparing(
            Comparator.comparingDouble(ThreadView::cpuCorePercent).reversed()));
        return new Snapshot(lastAt, observedMs, inspected, threads.getThreadCount(),
            threads.isThreadCpuTimeSupported() && threads.isThreadCpuTimeEnabled(), List.copyOf(values));
    }

    public record ThreadView(long id, String name, String state, boolean deadlocked,
        double cpuCorePercent, String lock, String lockOwner, List<String> stack) {}
    public record Snapshot(long sampledAtMs, long observedMs, int inspected, int total,
        boolean cpuAvailable, List<ThreadView> threads) {}
}
