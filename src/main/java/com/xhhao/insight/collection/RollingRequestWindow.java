package com.xhhao.insight.collection;

import com.xhhao.insight.metrics.RequestMetric;
import com.xhhao.insight.metrics.RequestWindowSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RollingRequestWindow {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final Clock clock;
    private final Map<WindowKey, RequestMetric> completed = new LinkedHashMap<>();

    public RollingRequestWindow() {
        this(Clock.systemUTC());
    }

    RollingRequestWindow(Clock clock) {
        this.clock = clock;
    }

    public synchronized void addCompleted(List<RequestMetric> metrics) {
        for (RequestMetric metric : metrics) {
            WindowKey key = new WindowKey(metric.bucketStartMs(), metric.statusGroup());
            completed.merge(key, compact(metric), RollingRequestWindow::merge);
        }
        evictExpired(clock.millis());
    }

    public synchronized RequestWindowSnapshot snapshot(List<RequestMetric> current) {
        long nowMs = clock.millis();
        evictExpired(nowMs);
        List<RequestMetric> all = new ArrayList<>(completed.size() + current.size());
        all.addAll(completed.values());
        all.addAll(current);
        return RequestMetricAggregator.aggregate(all, nowMs);
    }

    int retainedMetricCount() {
        return completed.size();
    }

    private void evictExpired(long nowMs) {
        long cutoff = nowMs - WINDOW.toMillis();
        Iterator<WindowKey> keys = completed.keySet().iterator();
        while (keys.hasNext()) {
            if (keys.next().bucketStartMs() < cutoff) {
                keys.remove();
            }
        }
    }

    private static RequestMetric compact(RequestMetric metric) {
        return new RequestMetric(metric.bucketStartMs(), "_window", "/{aggregate}", "ALL",
            metric.statusGroup(), metric.requestCount(), metric.errorCount(),
            metric.durationTotalMs(), metric.durationMaxMs(), metric.latencyBuckets());
    }

    private static RequestMetric merge(RequestMetric left, RequestMetric right) {
        long[] leftBuckets = left.latencyBuckets();
        long[] rightBuckets = right.latencyBuckets();
        int length = Math.max(leftBuckets.length, rightBuckets.length);
        long[] mergedBuckets = new long[length];
        for (int index = 0; index < length; index++) {
            long leftValue = index < leftBuckets.length ? leftBuckets[index] : 0;
            long rightValue = index < rightBuckets.length ? rightBuckets[index] : 0;
            mergedBuckets[index] = saturatedAdd(leftValue, rightValue);
        }
        return new RequestMetric(left.bucketStartMs(), left.scope(), left.route(), left.method(),
            left.statusGroup(), saturatedAdd(left.requestCount(), right.requestCount()),
            saturatedAdd(left.errorCount(), right.errorCount()),
            saturatedAdd(left.durationTotalMs(), right.durationTotalMs()),
            Math.max(left.durationMaxMs(), right.durationMaxMs()), mergedBuckets);
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private record WindowKey(long bucketStartMs, int statusGroup) {
    }
}
