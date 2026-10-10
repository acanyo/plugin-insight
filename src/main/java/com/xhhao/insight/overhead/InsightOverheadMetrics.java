package com.xhhao.insight.overhead;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/** Measures existing operations; never starts a sampler or writes its own history. */
@Component
public class InsightOverheadMetrics {
    private static final long MINUTE = 60_000;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final Counters counters;
    private final TreeMap<Long, EnumMap<Operation, Totals>> minutes = new TreeMap<>();
    private long startedAt;

    public InsightOverheadMetrics() {
        this(Clock.systemUTC(), System::nanoTime, new JmxCounters());
    }

    InsightOverheadMetrics(Clock clock, LongSupplier nanoTime, Counters counters) {
        this.clock = clock;
        this.nanoTime = nanoTime;
        this.counters = counters;
        startedAt = clock.millis();
    }

    public synchronized void reset() {
        minutes.clear();
        startedAt = clock.millis();
    }

    public Scope begin(Operation operation) {
        return new Scope(
                operation,
                nanoTime.getAsLong(),
                operation.instrumentCounters ? counters.cpu() : -1,
                operation.instrumentCounters ? counters.allocated() : -1);
    }

    public void run(Operation operation, Runnable work) {
        try (var scope = begin(operation)) {
            try {
                work.run();
            } catch (RuntimeException | Error failure) {
                scope.failed = true;
                throw failure;
            }
        }
    }

    public synchronized Snapshot snapshot() {
        long now = clock.millis();
        prune(now);
        long from = Math.max(startedAt, (now / MINUTE - 59) * MINUTE);
        var aggregate = new EnumMap<Operation, Totals>(Operation.class);
        var points = new ArrayList<Point>();
        minutes.forEach(
                (at, values) -> {
                    long cpu = 0, allocated = 0, reads = 0, writes = 0;
                    for (var entry : values.entrySet()) {
                        aggregate
                                .computeIfAbsent(entry.getKey(), ignored -> new Totals())
                                .add(entry.getValue());
                        cpu += entry.getValue().cpuNs;
                        allocated += entry.getValue().allocated;
                        if (entry.getKey() == Operation.SQL_READ) reads += entry.getValue().calls;
                        if (entry.getKey() == Operation.SQL_WRITE) writes += entry.getValue().calls;
                    }
                    points.add(new Point(at, cpu / 1_000_000d, allocated, reads, writes));
                });
        var operations =
                aggregate.entrySet().stream()
                        .map(
                                entry -> {
                                    var value = entry.getValue();
                                    return new OperationView(
                                            entry.getKey()
                                                    .name()
                                                    .toLowerCase(java.util.Locale.ROOT),
                                            entry.getKey().label,
                                            value.calls,
                                            value.failures,
                                            value.elapsedNs / 1_000_000d / value.calls,
                                            value.maxElapsedNs / 1_000_000d,
                                            value.cpuNs / 1_000_000d,
                                            value.allocated,
                                            value.cpuSamples,
                                            value.allocationSamples);
                                })
                        .toList();
        long cpuNs = aggregate.values().stream().mapToLong(value -> value.cpuNs).sum();
        long bytes = aggregate.values().stream().mapToLong(value -> value.allocated).sum();
        long elapsedMs = Math.max(1, now - from);
        return new Snapshot(
                from,
                now,
                cpuNs * 100d / (elapsedMs * 1_000_000d),
                bytes * 60_000d / elapsedMs,
                aggregate.values().stream().anyMatch(value -> value.cpuSamples > 0),
                aggregate.values().stream().anyMatch(value -> value.allocationSamples > 0),
                calls(aggregate, Operation.SQL_READ),
                calls(aggregate, Operation.SQL_WRITE),
                operations,
                List.copyOf(points),
                "最近一小时内存窗口；CPU 与分配量仅覆盖采集、诊断任务执行线程，包含其同步调用，不覆盖请求线程或异步通知。"
                        + "读写次数按 SQLite 操作统计，含重试的一次操作计一次；不等于 SQL 语句数。进程重启后重新累计。");
    }

    private static long calls(EnumMap<Operation, Totals> values, Operation operation) {
        return values.containsKey(operation) ? values.get(operation).calls : 0;
    }

    private synchronized void record(
            Operation operation, long elapsed, long cpu, long allocated, boolean failed) {
        long now = clock.millis();
        prune(now);
        var value =
                minutes.computeIfAbsent(
                                now / MINUTE * MINUTE, ignored -> new EnumMap<>(Operation.class))
                        .computeIfAbsent(operation, ignored -> new Totals());
        value.calls++;
        value.failures += failed ? 1 : 0;
        value.elapsedNs += Math.max(0, elapsed);
        value.maxElapsedNs = Math.max(value.maxElapsedNs, elapsed);
        if (cpu >= 0) {
            value.cpuNs += cpu;
            value.cpuSamples++;
        }
        if (allocated >= 0) {
            value.allocated += allocated;
            value.allocationSamples++;
        }
    }

    private void prune(long now) {
        minutes.headMap((now / MINUTE - 59) * MINUTE, false).clear();
        while (minutes.size() > 60) minutes.pollFirstEntry();
    }

    public enum Operation {
        COLLECTION("监控采集", true),
        DIAGNOSTICS("诊断检查", true),
        SQL_READ("SQLite 读取", false),
        SQL_WRITE("SQLite 写入", false);
        private final String label;
        private final boolean instrumentCounters;

        Operation(String label, boolean instrumentCounters) {
            this.label = label;
            this.instrumentCounters = instrumentCounters;
        }
    }

    public final class Scope implements AutoCloseable {
        private final Operation operation;
        private final long startedNs, cpuBefore, allocatedBefore;
        private boolean failed, closed;

        private Scope(Operation operation, long startedNs, long cpuBefore, long allocatedBefore) {
            this.operation = operation;
            this.startedNs = startedNs;
            this.cpuBefore = cpuBefore;
            this.allocatedBefore = allocatedBefore;
        }

        public void failed() {
            failed = true;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            long cpuAfter = operation.instrumentCounters ? counters.cpu() : -1;
            long allocatedAfter = operation.instrumentCounters ? counters.allocated() : -1;
            record(
                    operation,
                    nanoTime.getAsLong() - startedNs,
                    delta(cpuBefore, cpuAfter),
                    delta(allocatedBefore, allocatedAfter),
                    failed);
        }
    }

    private static long delta(long before, long after) {
        return before < 0 || after < before ? -1 : after - before;
    }

    interface Counters {
        long cpu();

        long allocated();
    }

    private static final class JmxCounters implements Counters {
        private final java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();

        public long cpu() {
            try {
                return bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled()
                        ? bean.getCurrentThreadCpuTime()
                        : -1;
            } catch (RuntimeException unavailable) {
                return -1;
            }
        }

        public long allocated() {
            try {
                return bean instanceof com.sun.management.ThreadMXBean extended
                                && extended.isThreadAllocatedMemoryEnabled()
                        ? extended.getThreadAllocatedBytes(Thread.currentThread().threadId())
                        : -1;
            } catch (RuntimeException unavailable) {
                return -1;
            }
        }
    }

    private static final class Totals {
        long calls,
                failures,
                elapsedNs,
                maxElapsedNs,
                cpuNs,
                allocated,
                cpuSamples,
                allocationSamples;

        void add(Totals other) {
            calls += other.calls;
            failures += other.failures;
            elapsedNs += other.elapsedNs;
            maxElapsedNs = Math.max(maxElapsedNs, other.maxElapsedNs);
            cpuNs += other.cpuNs;
            allocated += other.allocated;
            cpuSamples += other.cpuSamples;
            allocationSamples += other.allocationSamples;
        }
    }

    public record Snapshot(
            long fromMs,
            long toMs,
            double cpuCorePercent,
            double allocatedBytesPerMinute,
            boolean cpuAvailable,
            boolean allocationAvailable,
            long databaseReads,
            long databaseWrites,
            List<OperationView> operations,
            List<Point> points,
            String coverage) {}

    public record OperationView(
            String id,
            String label,
            long calls,
            long failures,
            double averageMs,
            double maximumMs,
            double cpuTimeMs,
            long allocatedBytes,
            long cpuSamples,
            long allocationSamples) {}

    public record Point(
            long atMs,
            double cpuTimeMs,
            long allocatedBytes,
            long databaseReads,
            long databaseWrites) {}
}
