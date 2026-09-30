package com.xhhao.insight.api;

import com.xhhao.insight.api.model.TrendResponse;
import com.xhhao.insight.storage.model.LatencyHistogram;
import com.xhhao.insight.storage.model.RequestTrendMinute;
import com.xhhao.insight.storage.repository.RequestMetricRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds a bounded request trend while preserving aggregated request totals. */
@Component
public class RequestTrendService {

    static final int MAX_POINTS = 10_000;

    private final RequestMetricRepository requestMetrics;

    public RequestTrendService(RequestMetricRepository requestMetrics) {
        this.requestMetrics = requestMetrics;
    }

    public List<TrendResponse.RequestPoint> find(ApiTimeRange range) {
        return downsample(requestMetrics.findTrendRange(range.fromMs(), range.toMs())).stream()
            .map(RequestTrendService::toPoint)
            .toList();
    }

    static List<RequestTrendMinute> downsample(List<RequestTrendMinute> source) {
        if (source.size() <= MAX_POINTS) {
            return List.copyOf(source);
        }

        int interiorCount = source.size() - 2;
        int groupCount = MAX_POINTS - 2;
        List<RequestTrendMinute> result = new ArrayList<>(MAX_POINTS);
        result.add(source.getFirst());
        for (int group = 0; group < groupCount; group++) {
            int from = 1 + (int) ((long) group * interiorCount / groupCount);
            int to = 1 + (int) ((long) (group + 1) * interiorCount / groupCount);
            result.add(combine(source, from, to));
        }
        result.add(source.getLast());
        return List.copyOf(result);
    }

    private static RequestTrendMinute combine(
        List<RequestTrendMinute> source,
        int fromInclusive,
        int toExclusive
    ) {
        long requestCount = 0;
        long clientErrorCount = 0;
        long errorCount = 0;
        long durationSumUs = 0;
        long durationMaxUs = 0;
        long[] histogram = new long[11];
        for (int index = fromInclusive; index < toExclusive; index++) {
            RequestTrendMinute metric = source.get(index);
            requestCount = saturatingAdd(requestCount, metric.requestCount());
            clientErrorCount = saturatingAdd(clientErrorCount, metric.clientErrorCount());
            errorCount = saturatingAdd(errorCount, metric.errorCount());
            durationSumUs = saturatingAdd(durationSumUs, metric.durationSumUs());
            durationMaxUs = Math.max(durationMaxUs, metric.durationMaxUs());
            merge(histogram, metric.histogram());
        }
        RequestTrendMinute first = source.get(fromInclusive);
        RequestTrendMinute last = source.get(toExclusive - 1);
        long intervalMs = intervalBetween(first, last);
        return new RequestTrendMinute(
            last.bucketStartMs(),
            intervalMs,
            requestCount,
            clientErrorCount,
            errorCount,
            durationSumUs,
            durationMaxUs,
            histogram(histogram)
        );
    }

    private static void merge(long[] target, LatencyHistogram source) {
        target[0] = saturatingAdd(target[0], source.le5Ms());
        target[1] = saturatingAdd(target[1], source.le10Ms());
        target[2] = saturatingAdd(target[2], source.le25Ms());
        target[3] = saturatingAdd(target[3], source.le50Ms());
        target[4] = saturatingAdd(target[4], source.le100Ms());
        target[5] = saturatingAdd(target[5], source.le250Ms());
        target[6] = saturatingAdd(target[6], source.le500Ms());
        target[7] = saturatingAdd(target[7], source.le1000Ms());
        target[8] = saturatingAdd(target[8], source.le2500Ms());
        target[9] = saturatingAdd(target[9], source.le5000Ms());
        target[10] = saturatingAdd(target[10], source.gt5000Ms());
    }

    private static LatencyHistogram histogram(long[] values) {
        return new LatencyHistogram(
            values[0], values[1], values[2], values[3], values[4], values[5],
            values[6], values[7], values[8], values[9], values[10]
        );
    }

    private static TrendResponse.RequestPoint toPoint(RequestTrendMinute metric) {
        double requestsPerMinute = metric.requestCount() * 60_000d / metric.intervalMs();
        double clientErrorRate = percent(metric.clientErrorCount(), metric.requestCount());
        double serverErrorRate = percent(metric.errorCount(), metric.requestCount());
        return new TrendResponse.RequestPoint(
            metric.bucketStartMs(), metric.requestCount(), requestsPerMinute,
            metric.clientErrorCount(), clientErrorRate,
            metric.errorCount(), serverErrorRate,
            metric.errorCount(), serverErrorRate,
            metric.requestCount() == 0 ? 0
                : metric.durationSumUs() / 1_000d / metric.requestCount(),
            metric.histogram().estimatePercentileUs(0.95, metric.durationMaxUs()) / 1_000d,
            metric.histogram().estimatePercentileUs(0.99, metric.durationMaxUs()) / 1_000d
        );
    }

    private static long intervalBetween(RequestTrendMinute first, RequestTrendMinute last) {
        long endMs = saturatingAdd(last.bucketStartMs(), last.intervalMs());
        return Math.max(1, endMs - first.bucketStartMs());
    }

    private static double percent(long count, long total) {
        return total == 0 ? 0 : count * 100d / total;
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
