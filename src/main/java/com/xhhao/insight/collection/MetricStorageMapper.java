package com.xhhao.insight.collection;

import com.xhhao.insight.metrics.RequestMetric;
import com.xhhao.insight.metrics.SystemSnapshot;
import com.xhhao.insight.storage.model.LatencyHistogram;
import com.xhhao.insight.storage.model.PluginMetricMinute;
import com.xhhao.insight.storage.model.RequestMetricMinute;
import com.xhhao.insight.storage.model.SystemSample;
import java.util.List;

public final class MetricStorageMapper {

    private MetricStorageMapper() {}

    public static SystemSample toSystemSample(SystemSnapshot snapshot) {
        return new SystemSample(
            snapshot.timestampMs(), snapshot.uptimeMs(), snapshot.processCpuPercent(),
            snapshot.systemCpuPercent(), snapshot.systemLoadAverage(), snapshot.heapUsedBytes(),
            snapshot.heapMaxBytes(), snapshot.nonHeapUsedBytes(),
            snapshot.systemMemoryUsedBytes(), snapshot.systemMemoryTotalBytes(),
            Math.max(0, snapshot.diskTotalBytes() - snapshot.diskFreeBytes()),
            snapshot.diskTotalBytes(), snapshot.threadCount(), snapshot.daemonThreadCount(),
            snapshot.peakThreadCount(), snapshot.deadlockedThreadCount(), snapshot.gcCount(),
            snapshot.gcTimeMs()
        );
    }

    public static List<RequestMetricMinute> toRequestMetrics(List<RequestMetric> metrics) {
        return metrics.stream().map(MetricStorageMapper::toRequestMetric).toList();
    }

    public static List<PluginMetricMinute> toPluginMetrics(List<RequestMetric> metrics) {
        return metrics.stream()
            .filter(metric -> !"halo-core".equals(metric.scope()))
            .filter(metric -> !metric.scope().startsWith("_"))
            .map(metric -> new PluginMetricMinute(
                metric.bucketStartMs(), metric.scope(), 0, 0, 0, 0,
                metric.requestCount(), metric.errorCount(), micros(metric.durationTotalMs()),
                micros(metric.durationMaxMs()), 0, 0, 0, 0
            ))
            .toList();
    }

    private static RequestMetricMinute toRequestMetric(RequestMetric metric) {
        long[] bucket = metric.latencyBuckets();
        LatencyHistogram histogram = new LatencyHistogram(
            count(bucket, 0), count(bucket, 1), count(bucket, 2), count(bucket, 3),
            count(bucket, 4), count(bucket, 5), count(bucket, 6), count(bucket, 7),
            count(bucket, 8), count(bucket, 9), count(bucket, 10)
        );
        return new RequestMetricMinute(
            metric.bucketStartMs(), metric.scope(), metric.route(), metric.method(),
            metric.statusGroup(), metric.requestCount(), metric.errorCount(),
            micros(metric.durationTotalMs()), micros(metric.durationMaxMs()), histogram
        );
    }

    private static long count(long[] values, int index) {
        return index < values.length ? values[index] : 0;
    }

    private static long micros(long millis) {
        return millis > Long.MAX_VALUE / 1_000 ? Long.MAX_VALUE : millis * 1_000;
    }
}
