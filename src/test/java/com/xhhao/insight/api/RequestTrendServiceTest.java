package com.xhhao.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.storage.model.LatencyHistogram;
import com.xhhao.insight.storage.model.RequestTrendMinute;
import com.xhhao.insight.storage.repository.RequestMetricRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestTrendServiceTest {

    @Test
    void thirtyDayRangePreservesEndpointsAndAllAggregatedTotals() {
        RequestMetricRepository repository = mock(RequestMetricRepository.class);
        int sourceSize = 43_201;
        long firstTimestamp = 60_000;
        List<RequestTrendMinute> source = new ArrayList<>(sourceSize);
        for (int index = 0; index < sourceSize; index++) {
            source.add(minute(firstTimestamp + index * 60_000L));
        }
        long to = firstTimestamp + Duration.ofDays(30).toMillis() + 60_001;
        when(repository.findTrendRange(firstTimestamp, to)).thenReturn(source);

        List<RequestTrendMinute> reduced = RequestTrendService.downsample(source);
        var points = new RequestTrendService(repository)
            .find(new ApiTimeRange("30d", firstTimestamp, to));

        assertThat(reduced).hasSize(RequestTrendService.MAX_POINTS);
        assertThat(reduced.getFirst().bucketStartMs()).isEqualTo(firstTimestamp);
        assertThat(reduced.getLast().bucketStartMs())
            .isEqualTo(firstTimestamp + (sourceSize - 1L) * 60_000L);
        assertThat(reduced).extracting(RequestTrendMinute::requestCount)
            .satisfies(counts -> assertThat(counts.stream().mapToLong(Long::longValue).sum())
                .isEqualTo(sourceSize * 2L));
        assertThat(reduced).extracting(RequestTrendMinute::errorCount)
            .satisfies(counts -> assertThat(counts.stream().mapToLong(Long::longValue).sum())
                .isEqualTo(sourceSize));
        assertThat(reduced).extracting(RequestTrendMinute::durationSumUs)
            .satisfies(durations -> assertThat(
                durations.stream().mapToLong(Long::longValue).sum())
                .isEqualTo(sourceSize * 2_010_000L));
        assertThat(reduced).extracting(metric -> metric.histogram().totalCount())
            .satisfies(counts -> assertThat(counts.stream().mapToLong(Long::longValue).sum())
                .isEqualTo(sourceSize * 2L));

        assertThat(points).hasSize(RequestTrendService.MAX_POINTS);
        assertThat(points.getFirst().timestampMs()).isEqualTo(firstTimestamp);
        assertThat(points.getLast().timestampMs())
            .isEqualTo(firstTimestamp + (sourceSize - 1L) * 60_000L);
        assertThat(points).extracting(point -> point.averageLatencyMs())
            .allMatch(value -> value == 1_005d);
        assertThat(points).extracting(point -> point.p95Ms())
            .allMatch(value -> value == 2_500d);
        assertThat(points).extracting(point -> point.p99Ms())
            .allMatch(value -> value == 2_500d);
        verify(repository).findTrendRange(firstTimestamp, to);
    }

    private static RequestTrendMinute minute(long timestamp) {
        return new RequestTrendMinute(
            timestamp,
            2,
            1,
            2_010_000,
            2_000_000,
            new LatencyHistogram(0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0)
        );
    }
}
