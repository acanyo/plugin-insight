package com.xhhao.insight.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.xhhao.insight.metrics.LatencyBuckets;
import com.xhhao.insight.metrics.RequestMetric;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestMetricAggregatorTest {

    @Test
    void usesObservedMaximumForPercentilesInTheOpenEndedTail() {
        long[] buckets = new long[LatencyBuckets.size()];
        buckets[buckets.length - 1] = 100;
        RequestMetric metric = new RequestMetric(
            60_000, "halo-core", "/slow", "GET", 2,
            100, 0, 6_000_000, 60_000, buckets);

        var snapshot = RequestMetricAggregator.aggregate(List.of(metric), 120_000);

        assertThat(snapshot.p95Ms()).isEqualTo(60_000);
        assertThat(snapshot.p99Ms()).isEqualTo(60_000);
    }
}
