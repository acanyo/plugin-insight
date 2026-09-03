package com.xhhao.insight.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.model.AlertIncident;
import com.xhhao.insight.storage.model.LatencyHistogram;
import com.xhhao.insight.storage.model.MonitorEvent;
import com.xhhao.insight.storage.model.PluginMetricMinute;
import com.xhhao.insight.storage.model.RequestMetricMinute;
import com.xhhao.insight.storage.model.SystemSample;
import com.xhhao.insight.storage.repository.AlertIncidentRepository;
import com.xhhao.insight.storage.repository.MonitorEventRepository;
import com.xhhao.insight.storage.repository.PluginMetricRepository;
import com.xhhao.insight.storage.repository.RequestMetricRepository;
import com.xhhao.insight.storage.repository.SystemSampleRepository;
import com.xhhao.insight.storage.retention.RetentionPolicy;
import com.xhhao.insight.storage.retention.RetentionService;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventAlertRetentionTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteStorageLifecycle storage;
    private MonitorEventRepository events;
    private AlertIncidentRepository alerts;

    @BeforeEach
    void setUp() {
        storage = new SqliteStorageLifecycle(temporaryDirectory.resolve("insight.sqlite"));
        assertThat(storage.initialize()).isTrue();
        events = new MonitorEventRepository(storage.connectionManager());
        alerts = new AlertIncidentRepository(storage.connectionManager());
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void eventCursorIsStableWhenTimestampsAreEqual() {
        events.saveBatch(List.of(
            event("a", 2_000),
            event("b", 2_000),
            event("c", 2_000),
            event("d", 1_000)
        ));

        var first = events.findPage(1, 3_000, null, null, null, 2);
        assertThat(first.items()).extracting(MonitorEvent::id).containsExactly("c", "b");
        assertThat(first.hasMore()).isTrue();

        var second = events.findPage(1, 3_000, null, null, first.nextCursor(), 2);
        assertThat(second.items()).extracting(MonitorEvent::id).containsExactly("a", "d");
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void alertSupportsInsertUpdateAndQueries() {
        AlertIncident open = alert("alert-1", "OPEN", 1_000, null);
        alerts.insert(open);
        assertThat(alerts.findById("alert-1")).contains(open);

        AlertIncident resolved = alert("alert-1", "RESOLVED", 2_000, 2_000L);
        assertThat(alerts.update(resolved)).isTrue();
        assertThat(alerts.findByStatus("RESOLVED", 10)).containsExactly(resolved);
        assertThat(alerts.findRange(0, 3_000, 10)).containsExactly(resolved);
    }

    @Test
    void retentionDeletesOnlyOneExpiredBatchPerDomain() {
        var systemSamples = new SystemSampleRepository(storage.connectionManager());
        var pluginMetrics = new PluginMetricRepository(storage.connectionManager());
        var requestMetrics = new RequestMetricRepository(storage.connectionManager());
        var retention = new RetentionService(storage.connectionManager());

        systemSamples.saveBatch(List.of(systemSample(1_000), systemSample(2_000)));
        pluginMetrics.saveBatch(List.of(pluginMetric(1_000), pluginMetric(2_000)));
        requestMetrics.saveBatch(List.of(requestMetric(1_000), requestMetric(2_000)));
        events.saveBatch(List.of(event("old", 1_000), event("new", 2_000)));
        alerts.insert(alert("resolved", "RESOLVED", 1_000, 1_000L));
        alerts.insert(alert("open", "OPEN", 1_000, null));

        var result = retention.deleteExpiredBatch(
            new RetentionPolicy(1_500, 1_500, 1_500, 1_500, 1_500), 1);

        assertThat(result.totalDeleted()).isEqualTo(5);
        assertThat(result.mayHaveMore(1)).isTrue();
        assertThat(systemSamples.findRange(0, 3_000, 10))
            .extracting(SystemSample::sampledAtMs).containsExactly(2_000L);
        assertThat(pluginMetrics.findRange(0, 3_000, 10))
            .extracting(PluginMetricMinute::bucketStartMs).containsExactly(2_000L);
        assertThat(requestMetrics.findRange(0, 3_000, 10))
            .extracting(RequestMetricMinute::bucketStartMs).containsExactly(2_000L);
        assertThat(events.findRange(0, 3_000, 10))
            .extracting(MonitorEvent::id).containsExactly("new");
        assertThat(alerts.findByStatus("OPEN", 10))
            .extracting(AlertIncident::id).containsExactly("open");
        assertThat(alerts.findById("resolved")).isEmpty();
    }

    private static MonitorEvent event(String id, long time) {
        return new MonitorEvent(
            id, time, "PLUGIN", "WARN", "plugin-a", "Title", "Summary",
            "fingerprint-" + id, time, time, 1, null
        );
    }

    private static AlertIncident alert(String id, String status, long updatedAt, Long resolvedAt) {
        return new AlertIncident(
            id, "rule-1", status, "WARN", "Alert", "Summary", "fingerprint-" + id,
            1_000, updatedAt, resolvedAt, 1, 90.0, 80.0, null
        );
    }

    private static SystemSample systemSample(long time) {
        return new SystemSample(
            time, time, 0.1, 0.2, 1.0, 10, 100, 5, 200, 500,
            50, 1_000, 10, 5, 12, 0, 1, 2
        );
    }

    private static PluginMetricMinute pluginMetric(long bucket) {
        return new PluginMetricMinute(
            bucket, "plugin-a", 1, 1, 1, 1,
            1, 0, 100, 100, 1, 0, 100, 100
        );
    }

    private static RequestMetricMinute requestMetric(long bucket) {
        return new RequestMetricMinute(
            bucket, "HTTP", "/apis/posts/{name}", "GET", 2,
            1, 0, 1_000, 1_000,
            new LatencyHistogram(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        );
    }
}
