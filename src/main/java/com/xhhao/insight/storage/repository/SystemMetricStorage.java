package com.xhhao.insight.storage.repository;

import com.xhhao.insight.storage.aggregation.SystemMetricMinuteAggregator;
import com.xhhao.insight.storage.database.SqliteConnectionManager;
import com.xhhao.insight.storage.model.SystemSample;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Repository;

/** Atomically stores each raw system sample and its minute projection. */
@Repository
public class SystemMetricStorage {

    private final SqliteConnectionManager connectionManager;
    private final SystemSampleRepository samples;
    private final SystemMetricMinuteRepository minutes;

    public SystemMetricStorage(
        SqliteConnectionManager connectionManager,
        SystemSampleRepository samples,
        SystemMetricMinuteRepository minutes
    ) {
        this.connectionManager = connectionManager;
        this.samples = samples;
        this.minutes = minutes;
    }

    public void save(SystemSample sample) {
        saveBatch(List.of(sample));
    }

    public void saveBatch(List<SystemSample> batch) {
        if (batch.isEmpty()) {
            return;
        }
        connectionManager.write(connection -> {
            samples.saveBatch(connection, batch);
            Set<Long> affectedBuckets = new TreeSet<>();
            for (SystemSample sample : batch) {
                affectedBuckets.add(SystemMetricMinuteAggregator.bucketStart(sample.sampledAtMs()));
            }
            for (long bucketStartMs : affectedBuckets) {
                minutes.rebuild(connection, bucketStartMs);
            }
            return null;
        });
    }
}
