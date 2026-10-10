package com.xhhao.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.health.StorageHealthSnapshot;
import com.xhhao.insight.storage.repository.PluginDiagnosticRepository;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

class PluginBaselineQueryServiceTest {
    private final long now = 1_800_000_000_000L;
    private final PluginDiagnosticRepository repository = mock(PluginDiagnosticRepository.class);

    private PluginBaselineQueryService service() {
        var storage = mock(SqliteStorageLifecycle.class);
        var health = mock(StorageHealthSnapshot.class);
        when(storage.health()).thenReturn(health);
        when(health.available()).thenReturn(true);
        return new PluginBaselineQueryService(
                mock(PluginDiagnosticQueryService.class),
                repository,
                storage,
                Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC));
    }

    @Test
    void comparesCompletedNonOverlappingWindowsAndFlagsSamplingAndTrafficDifferences() {
        var before = point(now - 1_800_000, 15, 60, 100);
        var after = point(now - 900_000, 15, 30, 200);
        when(repository.timeline("alpha", now - 1_800_000, now - 900_000))
                .thenReturn(List.of(before));
        when(repository.timeline("alpha", now - 900_000, now)).thenReturn(List.of(after));
        var result = service().compare("alpha", "15m", "previous");
        assertThat(result.before().toMs()).isEqualTo(result.after().fromMs());
        assertThat(result.before().coveragePercent()).isEqualTo(100);
        assertThat(result.samplingComparable()).isFalse();
        assertThat(result.notices()).anySatisfy(message -> assertThat(message).contains("请求量差异"));
        assertThat(result.notices()).anySatisfy(message -> assertThat(message).contains("采样密度"));
        assertThat(service().compare("alpha", "7d", "yesterday").notices().getFirst())
                .contains("不能重叠");
    }

    @Test
    void averagesOnlyMinutesWithAvailableResourceCounters() {
        var point =
                new PluginDiagnosticRepository.Point(
                        0, 180_000, 600, 1024, 4, 3, 2, 1, 6, 2, 1, 0, 0, 0, 0, 0);
        var summary = PluginBaselineQueryService.summarize(List.of(point), 0, 180_000);
        assertThat(summary.cpuCorePercent()).isEqualTo(0.5);
        assertThat(summary.allocatedBytesPerMinute()).isEqualTo(1024);
        assertThat(summary.cpuObservedMinutes()).isEqualTo(2);
        assertThat(summary.allocationObservedMinutes()).isEqualTo(1);
    }

    @Test
    void doesNotFabricateAnUpgradeBaselineOrZeroCpuForMissingMeasurements() {
        when(repository.latestUpgrade(eq("alpha"), anyLong(), eq(now))).thenReturn(0L);
        assertThat(service().compare("alpha", "15m", "upgrade").available()).isFalse();
        when(repository.latestUpgrade(eq("alpha"), anyLong(), eq(now))).thenReturn(now - 60_000);
        assertThat(service().compare("alpha", "15m", "upgrade").notices().getFirst())
                .contains("尚未积累");
        var summary = PluginBaselineQueryService.summarize(List.of(point(0, 1, 0, 0)), 0, 900_000);
        assertThat(summary.sufficient()).isFalse();
        assertThat(summary.cpuCorePercent()).isNull();
        assertThat(summary.averageRequestMs()).isNull();
    }

    private static PluginDiagnosticRepository.Point point(
            long at, int minutes, long samples, long requests) {
        return new PluginDiagnosticRepository.Point(
                at,
                900_000,
                6000,
                4096,
                4,
                minutes,
                samples,
                samples,
                samples,
                samples == 0 ? 0 : minutes,
                samples == 0 ? 0 : minutes,
                requests,
                0,
                requests * 100,
                200,
                minutes);
    }
}
