package com.xhhao.insight.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xhhao.insight.storage.aggregation.SystemMetricMinuteAggregator;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.model.SystemSample;
import com.xhhao.insight.storage.repository.SystemMetricMinuteRepository;
import com.xhhao.insight.storage.repository.SystemMetricStorage;
import com.xhhao.insight.storage.repository.SystemSampleRepository;
import com.xhhao.insight.storage.retention.RetentionPolicy;
import com.xhhao.insight.storage.retention.RetentionService;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemMetricAggregationTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteStorageLifecycle storage;

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    void computesGaugeSemanticsAndGcDeltas() {
        SystemSample previous = sample(59_000, 59_000, 10, 20, 100, 5);
        List<SystemSample> samples = List.of(
            sample(60_000, 60_000, 20, 30, 102, 7),
            sample(75_000, 75_000, 40, 50, 105, 11),
            sample(90_000, 90_000, 60, 70, 109, 16)
        );

        var result = SystemMetricMinuteAggregator.aggregate(60_000, samples, previous);

        assertThat(result.sampleCount()).isEqualTo(3);
        assertThat(result.processCpuAverage()).isEqualTo(40);
        assertThat(result.processCpuMax()).isEqualTo(60);
        assertThat(result.processCpuLast()).isEqualTo(60);
        assertThat(result.heapUsedAverageBytes()).isEqualTo(50);
        assertThat(result.heapUsedMaxBytes()).isEqualTo(70);
        assertThat(result.heapUsedLastBytes()).isEqualTo(70);
        assertThat(result.lastSampledAtMs()).isEqualTo(90_000);
        assertThat(result.gcCountDelta()).isEqualTo(9);
        assertThat(result.gcPauseMsDelta()).isEqualTo(11);
    }

    @Test
    void doesNotTreatJvmRestartCountersAsActivity() {
        SystemSample beforeRestart = sample(59_000, 59_000, 10, 20, 100, 100);
        List<SystemSample> samples = List.of(
            sample(60_000, 1_000, 20, 30, 2, 3),
            sample(75_000, 16_000, 30, 40, 4, 8)
        );

        var result = SystemMetricMinuteAggregator.aggregate(
            60_000, samples, beforeRestart);

        assertThat(result.gcCountDelta()).isEqualTo(2);
        assertThat(result.gcPauseMsDelta()).isEqualTo(5);
    }

    @Test
    void savesRawAndMinuteProjectionIdempotentlyInOneTransaction() {
        initialize();
        var raw = new SystemSampleRepository(storage.connectionManager());
        var minutes = new SystemMetricMinuteRepository(storage.connectionManager());
        var metrics = new SystemMetricStorage(storage.connectionManager(), raw, minutes);
        SystemSample first = sample(60_000, 60_000, 20, 30, 2, 3);
        SystemSample second = sample(75_000, 75_000, 40, 50, 5, 8);

        metrics.saveBatch(List.of(second, first));
        metrics.saveBatch(List.of(first, second));

        assertThat(raw.findRange(0, 120_000, 10)).containsExactly(first, second);
        assertThat(minutes.findRange(0, 120_000)).singleElement().satisfies(minute -> {
            assertThat(minute.sampleCount()).isEqualTo(2);
            assertThat(minute.processCpuAverage()).isEqualTo(30);
            assertThat(minute.gcCountDelta()).isEqualTo(3);
            assertThat(minute.gcPauseMsDelta()).isEqualTo(5);
        });
    }

    @Test
    void rollsBackRawSampleWhenMinuteProjectionFails() {
        initialize();
        var raw = new SystemSampleRepository(storage.connectionManager());
        var minutes = new SystemMetricMinuteRepository(storage.connectionManager());
        var metrics = new SystemMetricStorage(storage.connectionManager(), raw, minutes);
        storage.connectionManager().write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                    CREATE TRIGGER reject_system_minute
                    BEFORE INSERT ON system_metric_minute
                    BEGIN
                        SELECT RAISE(ABORT, 'forced minute failure');
                    END
                    """);
            }
            return null;
        });

        assertThatThrownBy(() -> metrics.save(sample(60_000, 60_000, 20, 30, 2, 3)))
            .isInstanceOf(StorageException.class);

        assertThat(raw.findRange(0, 120_000, 10)).isEmpty();
        assertThat(minutes.findRange(0, 120_000)).isEmpty();
    }

    @Test
    void rawAndMinuteHistoryUseIndependentRetentionCutoffs() {
        initialize();
        var raw = new SystemSampleRepository(storage.connectionManager());
        var minutes = new SystemMetricMinuteRepository(storage.connectionManager());
        var metrics = new SystemMetricStorage(storage.connectionManager(), raw, minutes);
        var retention = new RetentionService(storage.connectionManager());
        metrics.saveBatch(List.of(
            sample(60_000, 60_000, 20, 30, 2, 3),
            sample(120_000, 120_000, 40, 50, 5, 8)
        ));

        var rawOnly = retention.deleteExpiredBatch(
            new RetentionPolicy(100_000, 60_000, 0, 0, 0, 0), 10);

        assertThat(rawOnly.systemSamples()).isEqualTo(1);
        assertThat(rawOnly.systemMetrics()).isZero();
        assertThat(raw.findRange(0, 180_000, 10))
            .extracting(SystemSample::sampledAtMs).containsExactly(120_000L);
        assertThat(minutes.findRange(0, 180_000)).hasSize(2);

        var aggregate = retention.deleteExpiredBatch(
            new RetentionPolicy(100_000, 100_000, 0, 0, 0, 0), 10);
        assertThat(aggregate.systemMetrics()).isEqualTo(1);
        assertThat(minutes.findRange(0, 180_000)).singleElement()
            .satisfies(metric -> assertThat(metric.bucketStartMs()).isEqualTo(120_000));
    }

    @Test
    void systemClearRemovesBothGranularities() {
        initialize();
        var raw = new SystemSampleRepository(storage.connectionManager());
        var minutes = new SystemMetricMinuteRepository(storage.connectionManager());
        new SystemMetricStorage(storage.connectionManager(), raw, minutes)
            .save(sample(60_000, 60_000, 20, 30, 2, 3));

        int deleted = new RetentionService(storage.connectionManager()).clearSystemData();

        assertThat(deleted).isEqualTo(2);
        assertThat(raw.findRange(0, 120_000, 10)).isEmpty();
        assertThat(minutes.findRange(0, 120_000)).isEmpty();
    }

    private void initialize() {
        storage = new SqliteStorageLifecycle(temporaryDirectory.resolve("insight.sqlite"));
        assertThat(storage.initialize()).isTrue();
    }

    private static SystemSample sample(
        long timestamp,
        long uptime,
        long processCpu,
        long heapUsed,
        long gcCount,
        long gcPause
    ) {
        return new SystemSample(
            timestamp, uptime, processCpu, processCpu + 1, 1.5,
            heapUsed, 1_000, heapUsed / 2, 200 + heapUsed, 2_000,
            500 + heapUsed, 4_000, (int) processCpu, 5, (int) processCpu + 2,
            0, gcCount, gcPause
        );
    }
}
