package com.xhhao.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xhhao.insight.storage.model.SystemMetricMinute;
import com.xhhao.insight.storage.model.SystemSample;
import com.xhhao.insight.storage.repository.SystemMetricMinuteRepository;
import com.xhhao.insight.storage.repository.SystemSampleRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemTrendServiceTest {

    @Test
    void shortRangeUsesRawSamplesAndReturnsGcPauseDeltas() {
        SystemSampleRepository raw = mock(SystemSampleRepository.class);
        SystemMetricMinuteRepository minutes = mock(SystemMetricMinuteRepository.class);
        long to = Duration.ofHours(1).toMillis();
        when(raw.findRange(anyLong(), anyLong(), anyInt())).thenReturn(List.of(
            raw(1_000, 1_000, 10),
            raw(16_000, 16_000, 14)
        ));

        var points = new SystemTrendService(raw, minutes)
            .find(new ApiTimeRange("1h", 0, to));

        assertThat(points).hasSize(2);
        assertThat(points).extracting(point -> point.gcPauseMs())
            .containsExactly(0L, 4L);
        verifyNoInteractions(minutes);
    }

    @Test
    void thirtyDayRangeIsCompleteIncludingCurrentMinuteAndHasBoundedPoints() {
        SystemSampleRepository raw = mock(SystemSampleRepository.class);
        SystemMetricMinuteRepository minutes = mock(SystemMetricMinuteRepository.class);
        long to = Duration.ofDays(30).toMillis() + 30_001;
        List<SystemMetricMinute> source = new ArrayList<>(43_201);
        for (int index = 0; index < 43_201; index++) {
            source.add(minute(index * 60_000L, index * 60_000L + 30_000));
        }
        long from = 30_000;
        when(minutes.findRange(from, to)).thenReturn(source);

        var points = new SystemTrendService(raw, minutes)
            .find(new ApiTimeRange("30d", from, to));

        assertThat(points).hasSizeLessThanOrEqualTo(SystemTrendService.MAX_POINTS);
        assertThat(points.getFirst().timestampMs()).isEqualTo(270_000);
        assertThat(points.getLast().timestampMs()).isEqualTo(
            Duration.ofDays(30).toMillis() + 30_000);
        assertThat(points).extracting(point -> point.gcPauseMs())
            .allMatch(value -> value > 0);
        verifyNoInteractions(raw);
    }

    private static SystemSample raw(long timestamp, long uptime, long gcPauseMs) {
        return new SystemSample(
            timestamp, uptime, 10, 20, 1, 100, 1_000, 50,
            500, 2_000, 300, 4_000, 10, 5, 12, 0, 1, gcPauseMs
        );
    }

    private static SystemMetricMinute minute(long bucket, long sampledAt) {
        return new SystemMetricMinute(
            bucket, sampledAt, 4, sampledAt,
            10, 20, 15, 30, 40, 35, 1, 2, 1.5,
            100, 120, 110, 1_000,
            50, 60, 55, 500, 600, 550, 2_000,
            300, 4_000, 10, 12, 11, 5, 14, 0, 0, 1, 2
        );
    }
}
