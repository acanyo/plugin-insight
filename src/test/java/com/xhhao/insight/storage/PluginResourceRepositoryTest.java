package com.xhhao.insight.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.model.PluginResourceMinute;
import com.xhhao.insight.storage.model.PluginResourceSort;
import com.xhhao.insight.storage.model.ResourceAttributionMinute;
import com.xhhao.insight.storage.repository.PluginResourceRepository;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginResourceRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void upsertsResourceDeltasAndPreservesCoverageMetadata() {
        try (var storage = new SqliteStorageLifecycle(
            temporaryDirectory.resolve("insight.sqlite"))) {
            assertThat(storage.initialize()).isTrue();
            var repository = new PluginResourceRepository(storage.connectionManager());
            repository.save(List.of(metric("plugin-a", 100, 1_000)), coverage(1_000, 500));
            repository.save(List.of(metric("plugin-a", 200, 2_000)), coverage(2_000, 1_000));

            assertThat(repository.findRanking(
                0, 120_000, PluginResourceSort.CPU_TIME, 10))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.pluginId()).isEqualTo("plugin-a");
                    assertThat(summary.cpuTimeNs()).isEqualTo(300);
                    assertThat(summary.allocatedBytes()).isEqualTo(3_000);
                    assertThat(summary.liveThreadsMax()).isEqualTo(2);
                });
            assertThat(repository.summarizeCoverage(0, 120_000))
                .satisfies(summary -> {
                    assertThat(summary.sampleCount()).isEqualTo(2);
                    assertThat(summary.totalCpuTimeNs()).isEqualTo(3_000);
                    assertThat(summary.attributedCpuTimeNs()).isEqualTo(1_500);
                    assertThat(summary.cpuCoveragePercent()).isEqualTo(50);
                });
        }
    }

    private static PluginResourceMinute metric(String pluginId, long cpu, long bytes) {
        return new PluginResourceMinute(
            60_000, pluginId, 1, 2, 2, 1, 2, 2, cpu, bytes);
    }

    private static ResourceAttributionMinute coverage(long totalCpu, long attributedCpu) {
        return new ResourceAttributionMinute(
            60_000, 1, 1, 1, 0, 10, 2, totalCpu, attributedCpu,
            totalCpu * 10, attributedCpu * 10);
    }
}
