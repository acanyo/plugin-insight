package com.xhhao.insight.collection;

import com.xhhao.insight.metrics.RequestMetric;
import com.xhhao.insight.storage.repository.MetricBatchRepository;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Retains completed request metrics until both historical projections commit successfully.
 */
@Component
final class CompletedMetricBuffer {

    private static final int DEFAULT_MAX_PENDING_METRICS = 10_000;
    private static final int DEFAULT_FLUSH_BATCH_SIZE = 500;

    private final MetricBatchRepository repository;
    private final int maxPendingMetrics;
    private final int flushBatchSize;
    private final Deque<RequestMetric> pending = new ArrayDeque<>();
    private long droppedCount;

    @Autowired
    public CompletedMetricBuffer(MetricBatchRepository repository) {
        this(repository, DEFAULT_MAX_PENDING_METRICS, DEFAULT_FLUSH_BATCH_SIZE);
    }

    CompletedMetricBuffer(
        MetricBatchRepository repository,
        int maxPendingMetrics,
        int flushBatchSize
    ) {
        this.repository = repository;
        if (maxPendingMetrics <= 0 || flushBatchSize <= 0) {
            throw new IllegalArgumentException("Metric buffer limits must be positive");
        }
        this.maxPendingMetrics = maxPendingMetrics;
        this.flushBatchSize = Math.min(flushBatchSize, maxPendingMetrics);
    }

    synchronized long enqueue(List<RequestMetric> metrics) {
        long before = droppedCount;
        for (RequestMetric metric : metrics) {
            if (pending.size() == maxPendingMetrics) {
                pending.removeFirst();
                incrementDropped(1);
            }
            pending.addLast(metric);
        }
        return droppedCount - before;
    }

    synchronized long discard(int metricCount) {
        if (metricCount < 0) {
            throw new IllegalArgumentException("metricCount must not be negative");
        }
        long before = droppedCount;
        incrementDropped(metricCount);
        return droppedCount - before;
    }

    synchronized long discardPending() {
        int pendingMetrics = pending.size();
        pending.clear();
        return discard(pendingMetrics);
    }

    synchronized void flush() {
        if (pending.isEmpty()) {
            return;
        }
        int batchSize = Math.min(flushBatchSize, pending.size());
        List<RequestMetric> batch = pending.stream().limit(batchSize).toList();
        repository.save(
            MetricStorageMapper.toRequestMetrics(batch),
            MetricStorageMapper.toPluginMetrics(batch)
        );
        for (int index = 0; index < batchSize; index++) {
            pending.removeFirst();
        }
    }

    synchronized int pendingCount() {
        return pending.size();
    }

    synchronized long droppedCount() {
        return droppedCount;
    }

    private void incrementDropped(long count) {
        droppedCount = Long.MAX_VALUE - droppedCount < count
            ? Long.MAX_VALUE
            : droppedCount + count;
    }
}
