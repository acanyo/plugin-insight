package com.xhhao.insight.storage.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.model.LatencyHistogram;
import com.xhhao.insight.storage.model.PluginMetricMinute;
import com.xhhao.insight.storage.model.RequestMetricMinute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class PluginDiagnosticRepositoryTest {
    @TempDir Path temporary;

    @Test
    void aggregatesOnePluginAcrossTheWholeRangeWithoutLosingPointsToALimit() {
        try (var storage = new SqliteStorageLifecycle(temporary.resolve("insight.sqlite"))) {
            assertThat(storage.initialize()).isTrue();
            var database = storage.connectionManager();
            database.write(
                    connection -> {
                        try (var statement =
                                connection.prepareStatement(
                                        """
INSERT INTO plugin_resource_minute(bucket_start_ms,plugin_id,sample_count,cpu_measurement_samples,
  allocation_measurement_samples,cpu_active_samples,active_thread_samples,live_threads_max,cpu_time_ns,allocated_bytes)
VALUES(?,?,2,2,2,1,4,4,1000000,2048)
""")) {
                            for (int i = 0; i < 1000; i++) {
                                for (String id : List.of("alpha", "beta")) {
                                    statement.setLong(1, i * 60_000L);
                                    statement.setString(2, id);
                                    statement.addBatch();
                                }
                            }
                            statement.executeBatch();
                        }
                        return null;
                    });
            new PluginMetricRepository(database)
                    .saveBatch(
                            List.of(
                                    new PluginMetricMinute(
                                            0, "alpha", 0, 0, 0, 0, 10, 1, 100_000, 20_000, 0, 0, 0,
                                            0),
                                    new PluginMetricMinute(
                                            0, "beta", 0, 0, 0, 0, 500, 20, 900_000, 90_000, 0, 0,
                                            0, 0)));
            var points = new PluginDiagnosticRepository(database).timeline("alpha", 0, 60_000_000);
            assertThat(points.size()).isLessThanOrEqualTo(122);
            assertThat(
                            points.stream()
                                    .mapToInt(PluginDiagnosticRepository.Point::resourceMinutes)
                                    .sum())
                    .isEqualTo(1000);
            assertThat(
                            points.stream()
                                    .mapToDouble(PluginDiagnosticRepository.Point::cpuTimeMs)
                                    .sum())
                    .isEqualTo(1000);
            assertThat(points.stream().mapToLong(PluginDiagnosticRepository.Point::requests).sum())
                    .isEqualTo(10);
            assertThat(points.stream().mapToLong(PluginDiagnosticRepository.Point::errors).sum())
                    .isEqualTo(1);
        }
    }

    @Test
    void filtersPluginRoutesBeforeRankingAndPreservesGlobalQueries() {
        try (var storage = new SqliteStorageLifecycle(temporary.resolve("routes.sqlite"))) {
            assertThat(storage.initialize()).isTrue();
            var requests = new RequestMetricRepository(storage.connectionManager());
            var histogram = new LatencyHistogram(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
            requests.saveBatch(
                    List.of(
                            new RequestMetricMinute(
                                    0,
                                    "alpha",
                                    "/alpha/{id}",
                                    "GET",
                                    2,
                                    1,
                                    0,
                                    8_000_000,
                                    8_000_000,
                                    histogram),
                            new RequestMetricMinute(
                                    0,
                                    "beta",
                                    "/beta/{id}",
                                    "GET",
                                    2,
                                    1,
                                    0,
                                    9_000_000,
                                    9_000_000,
                                    histogram)));
            assertThat(requests.findPluginRoutes("alpha", 0, 60_000, 10))
                    .hasSize(1)
                    .allSatisfy(route -> assertThat(route.scope()).isEqualTo("alpha"));
            assertThat(requests.findSlowRoutes(0, 60_000, 10)).hasSize(2);
        }
    }
}
