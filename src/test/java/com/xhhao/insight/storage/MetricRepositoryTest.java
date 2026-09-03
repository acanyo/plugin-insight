package com.xhhao.insight.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xhhao.insight.storage.StorageException;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.model.LatencyHistogram;
import com.xhhao.insight.storage.model.PluginMetricMinute;
import com.xhhao.insight.storage.model.PluginMetricSort;
import com.xhhao.insight.storage.model.RequestMetricMinute;
import com.xhhao.insight.storage.model.RequestRouteSort;
import com.xhhao.insight.storage.model.SystemSample;
import com.xhhao.insight.storage.repository.PluginMetricRepository;
import com.xhhao.insight.storage.repository.MetricBatchRepository;
import com.xhhao.insight.storage.repository.RequestMetricRepository;
import com.xhhao.insight.storage.repository.SystemSampleRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetricRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteStorageLifecycle storage;
    private SystemSampleRepository systemSamples;
    private PluginMetricRepository pluginMetrics;
    private RequestMetricRepository requestMetrics;

    @BeforeEach
    void setUp() {
        storage = new SqliteStorageLifecycle(temporaryDirectory.resolve("insight.sqlite"));
        assertThat(storage.initialize()).isTrue();
        systemSamples = new SystemSampleRepository(storage.connectionManager());
        pluginMetrics = new PluginMetricRepository(storage.connectionManager());
        requestMetrics = new RequestMetricRepository(storage.connectionManager());
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void storesSystemSamplesAndReturnsLatestInTimeOrder() {
        SystemSample first = systemSample(1_000, 10);
        SystemSample second = systemSample(2_000, 20);
        SystemSample third = systemSample(3_000, 30);

        systemSamples.saveBatch(List.of(second, first, third));

        assertThat(systemSamples.findLatest()).contains(third);
        assertThat(systemSamples.findRange(500, 4_000, 10))
            .containsExactly(first, second, third);
        assertThat(systemSamples.findRange(500, 4_000, 2))
            .containsExactly(second, third);
    }

    @Test
    void aggregatesPluginMetricsWithoutPersistingRankPositions() {
        pluginMetrics.saveBatch(List.of(
            pluginMetric(60_000, "plugin-a", 2, 20, 3_000),
            pluginMetric(60_000, "plugin-a", 3, 30, 6_000),
            pluginMetric(60_000, "plugin-b", 4, 10, 2_000)
        ));

        assertThat(pluginMetrics.findRange(0, 120_000, 10)).hasSize(2);
        var ranking = pluginMetrics.findRanking(
            0, 120_000, PluginMetricSort.CPU_ACTIVE_SAMPLES, 10);
        assertThat(ranking).extracting(summary -> summary.pluginId())
            .containsExactly("plugin-a", "plugin-b");
        assertThat(ranking.getFirst().cpuActiveSamples()).isEqualTo(5);
        assertThat(ranking.getFirst().requestCount()).isEqualTo(50);
        assertThat(pluginMetrics.totalRequestDuration(0, 120_000)).isEqualTo(11_000);
    }

    @Test
    void mergesRequestBucketsAndComputesSlowRouteAndStatusDistribution() {
        requestMetrics.saveBatch(List.of(
            requestMetric(60_000, "/apis/posts/{name}", 2, 2, 20_000, 15_000,
                new LatencyHistogram(0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            requestMetric(60_000, "/apis/posts/{name}", 2, 1, 8_000, 8_000,
                new LatencyHistogram(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            requestMetric(60_000, "/apis/search", 5, 1, 6_500_000, 6_500_000,
                new LatencyHistogram(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))
        ));

        assertThat(requestMetrics.findRange(0, 120_000, 10)).hasSize(2);
        var busiest = requestMetrics.findTopRoutes(
            0, 120_000, RequestRouteSort.REQUEST_COUNT, 10);
        assertThat(busiest.getFirst().route()).isEqualTo("/apis/posts/{name}");
        assertThat(busiest.getFirst().requestCount()).isEqualTo(3);

        var slowest = requestMetrics.findSlowRoutes(0, 120_000, 10);
        assertThat(slowest.getFirst().route()).isEqualTo("/apis/search");
        assertThat(slowest.getFirst().p95DurationUs()).isEqualTo(6_500_000);
        assertThat(requestMetrics.findStatusDistribution(0, 120_000))
            .containsEntry(2, 3L)
            .containsEntry(5, 1L);
        assertThat(requestMetrics.findTrendRange(0, 120_000)).singleElement()
            .satisfies(trend -> {
                assertThat(trend.bucketStartMs()).isEqualTo(60_000);
                assertThat(trend.requestCount()).isEqualTo(4);
                assertThat(trend.errorCount()).isEqualTo(1);
                assertThat(trend.durationSumUs()).isEqualTo(6_528_000);
                assertThat(trend.durationMaxUs()).isEqualTo(6_500_000);
                assertThat(trend.histogram().totalCount()).isEqualTo(4);
            });
    }

    @Test
    void ranksOpenEndedP99InSqlAndReturnsOnlyTheRequestedLimit() {
        requestMetrics.saveBatch(List.of(
            requestMetric(60_000, "/tail", 2, 100, 120_490_000, 60_000_000,
                new LatencyHistogram(98, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2)),
            requestMetric(60_000, "/bounded", 2, 100, 250_000_000, 2_500_000,
                new LatencyHistogram(0, 0, 0, 0, 0, 0, 0, 0, 100, 0, 0))
        ));

        var p99 = requestMetrics.findTopRoutes(
            0, 120_000, RequestRouteSort.P99_DURATION, 1);

        assertThat(p99).singleElement().satisfies(route -> {
            assertThat(route.route()).isEqualTo("/tail");
            assertThat(route.p99DurationUs()).isEqualTo(60_000_000);
        });
    }

    @Test
    void boundsHighCardinalityRouteRankingAtTheSqlBoundary() {
        var metrics = IntStream.range(0, 250)
            .mapToObj(index -> requestMetric(
                60_000, "/route-" + index, 2, index + 1L,
                (index + 1L) * 5_000, 5_000,
                new LatencyHistogram(index + 1L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
            .toList();
        requestMetrics.saveBatch(metrics);

        var busiest = requestMetrics.findTopRoutes(
            0, 120_000, RequestRouteSort.REQUEST_COUNT, 7);

        assertThat(busiest).hasSize(7);
        assertThat(busiest.getFirst().route()).isEqualTo("/route-249");
        assertThat(busiest.getLast().route()).isEqualTo("/route-243");
    }

    @Test
    void requestAndPluginProjectionsRollbackTogether() {
        MetricBatchRepository batches = new MetricBatchRepository(
            storage.connectionManager(), requestMetrics, pluginMetrics);
        storage.connectionManager().write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                    CREATE TRIGGER reject_plugin_metric
                    BEFORE INSERT ON plugin_metric_minute
                    BEGIN
                        SELECT RAISE(ABORT, 'forced plugin failure');
                    END
                    """);
            }
            return null;
        });
        RequestMetricMinute request = requestMetric(
            60_000, "/atomic", 2, 1, 5_000, 5_000,
            new LatencyHistogram(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        PluginMetricMinute plugin = pluginMetric(60_000, "plugin-a", 1, 1, 5_000);

        assertThatThrownBy(() -> batches.save(List.of(request), List.of(plugin)))
            .isInstanceOf(StorageException.class);

        assertThat(requestMetrics.findRange(0, 120_000, 10)).isEmpty();
        assertThat(pluginMetrics.findRange(0, 120_000, 10)).isEmpty();
    }

    private static SystemSample systemSample(long time, long heapUsed) {
        return new SystemSample(
            time, time, 0.1, 0.2, 1.0, heapUsed, 100, 5, 200, 500,
            50, 1_000, 10, 5, 12, 0, 1, 2
        );
    }

    private static PluginMetricMinute pluginMetric(
        long bucket,
        String pluginId,
        long cpuSamples,
        long requestCount,
        long requestDuration
    ) {
        return new PluginMetricMinute(
            bucket, pluginId, 4, cpuSamples, cpuSamples, 2,
            requestCount, 1, requestDuration, requestDuration,
            2, 0, 500, 300
        );
    }

    private static RequestMetricMinute requestMetric(
        long bucket,
        String route,
        int statusGroup,
        long count,
        long sumUs,
        long maxUs,
        LatencyHistogram histogram
    ) {
        return new RequestMetricMinute(
            bucket, "HTTP", route, "get", statusGroup, count,
            statusGroup == 5 ? count : 0, sumUs, maxUs, histogram
        );
    }
}
