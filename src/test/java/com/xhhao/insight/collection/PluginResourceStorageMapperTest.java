package com.xhhao.insight.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.xhhao.insight.resource.PluginResourceDelta;
import com.xhhao.insight.resource.ResourceAttributionSample;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginResourceStorageMapperTest {

    @Test
    void mapsSamplesIntoMinuteBucketsWithoutDiscardingCoverage() {
        var sample = new ResourceAttributionSample(
            119_999, true, false, true, 100, 3, 500, 200, 0, 0,
            List.of(new PluginResourceDelta("plugin-a", 2, 0, 1, 2, 3, 200, 0)));

        assertThat(PluginResourceStorageMapper.toPluginMetrics(sample)).singleElement()
            .satisfies(metric -> {
                assertThat(metric.bucketStartMs()).isEqualTo(60_000);
                assertThat(metric.cpuTimeNs()).isEqualTo(200);
            });
        assertThat(PluginResourceStorageMapper.toCoverage(sample)).satisfies(coverage -> {
            assertThat(coverage.bucketStartMs()).isEqualTo(60_000);
            assertThat(coverage.cpuCapableSamples()).isEqualTo(1);
            assertThat(coverage.allocationCapableSamples()).isZero();
            assertThat(coverage.truncatedSamples()).isEqualTo(1);
        });
    }
}
