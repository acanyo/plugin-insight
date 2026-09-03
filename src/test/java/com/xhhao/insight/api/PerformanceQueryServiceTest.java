package com.xhhao.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.insight.config.CollectionSettings;
import com.xhhao.insight.config.InsightSettingsService;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.health.StorageHealthSnapshot;
import com.xhhao.insight.storage.health.StorageState;
import com.xhhao.insight.storage.model.LatencyHistogram;
import com.xhhao.insight.storage.model.RequestRouteSummary;
import com.xhhao.insight.storage.repository.RequestMetricRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PerformanceQueryServiceTest {

    @Test
    void shouldApplyConfiguredSlowRequestThreshold() {
        SqliteStorageLifecycle storage = mock(SqliteStorageLifecycle.class);
        InsightSettingsService settings = mock(InsightSettingsService.class);
        RequestMetricRepository requestMetrics = mock(RequestMetricRepository.class);
        when(storage.health()).thenReturn(new StorageHealthSnapshot(
            StorageState.AVAILABLE, "ok", 0, 0));
        CollectionSettings collection = new CollectionSettings();
        collection.setSlowRequestMillis(1_000);
        when(settings.collection()).thenReturn(collection);
        when(requestMetrics.findTopRoutes(anyLong(), anyLong(), any(), anyInt()))
            .thenReturn(List.of(
                route("/fast", histogramAt100Ms()),
                route("/slow", histogramAt2500Ms())
            ));
        PerformanceQueryService service = new PerformanceQueryService(
            storage, settings, null, null, requestMetrics, null,
            Clock.fixed(Instant.ofEpochMilli(10_000_000), ZoneOffset.UTC)
        );

        var result = service.routes("1h", "p95");

        assertThat(result).extracting(item -> item.route()).containsExactly("/slow");
    }

    private static RequestRouteSummary route(String path, LatencyHistogram histogram) {
        return new RequestRouteSummary(
            "halo-core", path, "GET", 10, 0, 1_000_000, 2_500_000, histogram);
    }

    private static LatencyHistogram histogramAt100Ms() {
        return new LatencyHistogram(0, 0, 0, 0, 10, 0, 0, 0, 0, 0, 0);
    }

    private static LatencyHistogram histogramAt2500Ms() {
        return new LatencyHistogram(0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0);
    }
}
