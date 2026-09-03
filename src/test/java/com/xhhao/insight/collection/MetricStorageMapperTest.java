package com.xhhao.insight.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.xhhao.insight.metrics.LatencyBuckets;
import com.xhhao.insight.metrics.RequestMetric;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricStorageMapperTest {

    @Test
    void shouldPreserveHistogramAndConvertMillisecondsToMicroseconds() {
        long[] histogram = new long[LatencyBuckets.size()];
        histogram[LatencyBuckets.index(125)] = 2;
        RequestMetric source = new RequestMetric(
            60_000, "links", "/links/{name}", "GET", 5,
            2, 2, 250, 150, histogram
        );

        var result = MetricStorageMapper.toRequestMetrics(List.of(source)).getFirst();

        assertThat(result.statusGroup()).isEqualTo(5);
        assertThat(result.durationSumUs()).isEqualTo(250_000);
        assertThat(result.durationMaxUs()).isEqualTo(150_000);
        assertThat(result.histogram().totalCount()).isEqualTo(2);
    }

    @Test
    void shouldCreateEstimatedPluginRequestMetricsOnlyForPlugins() {
        long[] histogram = new long[LatencyBuckets.size()];
        RequestMetric plugin = new RequestMetric(
            60_000, "links", "/links", "GET", 2, 0, 0, 0, 0, histogram);
        RequestMetric core = new RequestMetric(
            60_000, "halo-core", "/api", "GET", 2, 0, 0, 0, 0, histogram);
        RequestMetric unmatched = new RequestMetric(
            60_000, "_unmatched", "/{route}", "GET", 4, 1, 0, 10, 10, histogram);
        RequestMetric overflow = new RequestMetric(
            60_000, "_overflow", "/{overflow}", "OTHER", 2, 1, 0, 10, 10, histogram);

        assertThat(MetricStorageMapper.toPluginMetrics(List.of(plugin, core, unmatched, overflow)))
            .singleElement()
            .satisfies(metric -> assertThat(metric.pluginId()).isEqualTo("links"));
    }
}
