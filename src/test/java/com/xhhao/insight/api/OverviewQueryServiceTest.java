package com.xhhao.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.insight.alert.AlertEngine;
import com.xhhao.insight.config.CollectionSettings;
import com.xhhao.insight.config.InsightSettingsService;
import com.xhhao.insight.halo.DependencyStatus;
import com.xhhao.insight.halo.HaloRuntimeSnapshot;
import com.xhhao.insight.metrics.SystemSnapshot;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.storage.capacity.StorageCapacityGuard;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.health.StorageHealthSnapshot;
import com.xhhao.insight.storage.health.StorageState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverviewQueryServiceTest {

    @Test
    void shouldPropagateCriticalDependencyToOverallHealth() {
        long now = 1_800_000_000_000L;
        MonitoringState state = healthyMonitoringState(now);
        state.updateHalo(new HaloRuntimeSnapshot("2.26.0", null, List.of(), List.of(
            DependencyStatus.critical("database", "Halo 数据库", "扩展查询失败")
        )));

        SqliteStorageLifecycle storage = mock(SqliteStorageLifecycle.class);
        when(storage.health()).thenReturn(new StorageHealthSnapshot(
            StorageState.AVAILABLE, "SQLite storage is available", now, 0));
        StorageCapacityGuard capacityGuard = mock(StorageCapacityGuard.class);
        when(capacityGuard.currentBytes()).thenReturn(0L);
        InsightSettingsService settings = mock(InsightSettingsService.class);
        when(settings.collection()).thenReturn(new CollectionSettings());
        AlertEngine alertEngine = mock(AlertEngine.class);
        when(alertEngine.activeIncidents()).thenReturn(List.of());

        var service = new OverviewQueryService(
            state, storage, capacityGuard, settings, alertEngine,
            Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC));

        assertThat(service.overview().health()).isEqualTo("critical");
    }

    private static MonitoringState healthyMonitoringState(long now) {
        MonitoringState state = new MonitoringState();
        state.setRunning(true);
        state.updateSystem(new SystemSnapshot(
            now, 60_000, 1, 2, 3,
            4, 8, 1, 4, 8,
            0, 0, 1, 1, 1, 0,
            100, 50));
        state.haloRefreshSucceeded(now);
        state.retentionSucceeded(now);
        return state;
    }
}
