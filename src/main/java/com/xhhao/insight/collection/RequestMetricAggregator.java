package com.xhhao.insight.collection;

import com.xhhao.insight.metrics.LatencyBuckets;
import com.xhhao.insight.metrics.RequestMetric;
import com.xhhao.insight.metrics.RequestWindowSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RequestMetricAggregator {

    private RequestMetricAggregator() {}

    public static RequestWindowSnapshot aggregate(List<RequestMetric> metrics, long nowMs) {
        if (metrics.isEmpty()) {
            return RequestWindowSnapshot.empty(nowMs);
        }
        long requestCount = 0;
        long errorCount = 0;
        long durationTotalMs = 0;
        long durationMaxMs = 0;
        long fromMs = Long.MAX_VALUE;
        long[] histogram = new long[LatencyBuckets.size()];
        Map<Integer, Long> statuses = new LinkedHashMap<>();
        for (RequestMetric metric : metrics) {
            requestCount += metric.requestCount();
            errorCount += metric.errorCount();
            durationTotalMs += metric.durationTotalMs();
            durationMaxMs = Math.max(durationMaxMs, metric.durationMaxMs());
            fromMs = Math.min(fromMs, metric.bucketStartMs());
            statuses.merge(metric.statusGroup(), metric.requestCount(), Long::sum);
            long[] counts = metric.latencyBuckets();
            for (int index = 0; index < Math.min(histogram.length, counts.length); index++) {
                histogram[index] += counts[index];
            }
        }
        return new RequestWindowSnapshot(fromMs, nowMs, requestCount, errorCount,
            durationTotalMs, durationMaxMs,
            LatencyBuckets.percentile(histogram, 0.95, durationMaxMs),
            LatencyBuckets.percentile(histogram, 0.99, durationMaxMs), statuses);
    }
}
