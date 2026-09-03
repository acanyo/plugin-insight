package com.xhhao.insight.storage.aggregation;

import com.xhhao.insight.storage.model.SystemMetricMinute;
import com.xhhao.insight.storage.model.SystemSample;
import java.util.Comparator;
import java.util.List;

/** Builds an idempotent one-minute projection from its raw samples. */
public final class SystemMetricMinuteAggregator {

    public static final long BUCKET_MILLIS = 60_000;

    private SystemMetricMinuteAggregator() {}

    public static SystemMetricMinute aggregate(
        long bucketStartMs,
        List<SystemSample> unorderedSamples,
        SystemSample previousSample
    ) {
        List<SystemSample> samples = unorderedSamples.stream()
            .sorted(Comparator.comparingLong(SystemSample::sampledAtMs))
            .toList();
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("A minute aggregate requires at least one sample");
        }
        long bucketEndMs = bucketStartMs + BUCKET_MILLIS;
        if (samples.stream().anyMatch(sample -> sample.sampledAtMs() < bucketStartMs
            || sample.sampledAtMs() >= bucketEndMs)) {
            throw new IllegalArgumentException("All samples must belong to the requested minute");
        }

        SystemSample last = samples.getLast();
        double processCpuSum = 0;
        double systemCpuSum = 0;
        double systemLoadSum = 0;
        long heapUsedSum = 0;
        long nonHeapUsedSum = 0;
        long memoryUsedSum = 0;
        long liveThreadsSum = 0;
        double processCpuMax = Double.NEGATIVE_INFINITY;
        double systemCpuMax = Double.NEGATIVE_INFINITY;
        double systemLoadMax = Double.NEGATIVE_INFINITY;
        long heapUsedMax = 0;
        long nonHeapUsedMax = 0;
        long memoryUsedMax = 0;
        int liveThreadsMax = 0;
        int peakThreadsMax = 0;
        int deadlockedThreadsMax = 0;
        long gcCountDelta = 0;
        long gcPauseMsDelta = 0;
        SystemSample baseline = previousSample;

        for (SystemSample sample : samples) {
            processCpuSum += sample.processCpuLoad();
            systemCpuSum += sample.systemCpuLoad();
            systemLoadSum += sample.systemLoadAverage();
            heapUsedSum = Math.addExact(heapUsedSum, sample.heapUsedBytes());
            nonHeapUsedSum = Math.addExact(nonHeapUsedSum, sample.nonHeapUsedBytes());
            memoryUsedSum = Math.addExact(memoryUsedSum, sample.systemMemoryUsedBytes());
            liveThreadsSum = Math.addExact(liveThreadsSum, sample.liveThreads());
            processCpuMax = Math.max(processCpuMax, sample.processCpuLoad());
            systemCpuMax = Math.max(systemCpuMax, sample.systemCpuLoad());
            systemLoadMax = Math.max(systemLoadMax, sample.systemLoadAverage());
            heapUsedMax = Math.max(heapUsedMax, sample.heapUsedBytes());
            nonHeapUsedMax = Math.max(nonHeapUsedMax, sample.nonHeapUsedBytes());
            memoryUsedMax = Math.max(memoryUsedMax, sample.systemMemoryUsedBytes());
            liveThreadsMax = Math.max(liveThreadsMax, sample.liveThreads());
            peakThreadsMax = Math.max(peakThreadsMax, sample.peakThreads());
            deadlockedThreadsMax = Math.max(deadlockedThreadsMax, sample.deadlockedThreads());
            if (baseline != null && sample.uptimeMs() >= baseline.uptimeMs()) {
                gcCountDelta = saturatingAdd(gcCountDelta,
                    nonNegativeDelta(sample.gcCount(), baseline.gcCount()));
                gcPauseMsDelta = saturatingAdd(gcPauseMsDelta,
                    nonNegativeDelta(sample.gcPauseMs(), baseline.gcPauseMs()));
            }
            baseline = sample;
        }

        int count = samples.size();
        return new SystemMetricMinute(
            bucketStartMs, last.sampledAtMs(), count, last.uptimeMs(),
            processCpuSum / count, processCpuMax, last.processCpuLoad(),
            systemCpuSum / count, systemCpuMax, last.systemCpuLoad(),
            systemLoadSum / count, systemLoadMax, last.systemLoadAverage(),
            roundedAverage(heapUsedSum, count), heapUsedMax, last.heapUsedBytes(),
            last.heapMaxBytes(), roundedAverage(nonHeapUsedSum, count), nonHeapUsedMax,
            last.nonHeapUsedBytes(), roundedAverage(memoryUsedSum, count), memoryUsedMax,
            last.systemMemoryUsedBytes(), last.systemMemoryTotalBytes(), last.diskUsedBytes(),
            last.diskTotalBytes(), (double) liveThreadsSum / count, liveThreadsMax,
            last.liveThreads(), last.daemonThreads(), peakThreadsMax, deadlockedThreadsMax,
            last.deadlockedThreads(), gcCountDelta, gcPauseMsDelta
        );
    }

    public static long bucketStart(long timestampMs) {
        return timestampMs - timestampMs % BUCKET_MILLIS;
    }

    private static long roundedAverage(long sum, int count) {
        return Math.round((double) sum / count);
    }

    private static long nonNegativeDelta(long current, long previous) {
        return current >= previous ? current - previous : 0;
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
