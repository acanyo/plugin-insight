package com.xhhao.insight.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.alert.AlertEngine;
import com.xhhao.insight.event.EventRecorder;
import com.xhhao.insight.optimization.OptimizationObservationService;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.health.StorageHealthSnapshot;
import com.xhhao.insight.storage.health.StorageState;
import com.xhhao.insight.storage.repository.AlertIncidentRepository;
import com.xhhao.insight.storage.repository.MonitorEventRepository;
import com.xhhao.insight.storage.repository.PluginMetricRepository;
import com.xhhao.insight.storage.repository.PluginResourceRepository;
import com.xhhao.insight.storage.repository.RequestMetricRepository;
import com.xhhao.insight.storage.retention.RetentionResult;
import com.xhhao.insight.storage.retention.RetentionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitoringDataCommandServiceTest {
    private final SqliteStorageLifecycle storage = mock(SqliteStorageLifecycle.class);
    private final RetentionService retention = mock(RetentionService.class);
    private final AlertEngine alerts = mock(AlertEngine.class);
    private final OptimizationObservationService observations =
            mock(OptimizationObservationService.class);
    private final MonitoringDataCommandService service =
            new MonitoringDataCommandService(
                    storage,
                    retention,
                    mock(RequestMetricRepository.class),
                    mock(PluginMetricRepository.class),
                    mock(PluginResourceRepository.class),
                    mock(MonitorEventRepository.class),
                    mock(AlertIncidentRepository.class),
                    mock(EventRecorder.class),
                    alerts,
                    observations);

    @BeforeEach
    void setup() {
        when(storage.health())
                .thenReturn(new StorageHealthSnapshot(StorageState.AVAILABLE, "available", 0, 0));
    }

    @Test
    void clearsLiveObservationsOnlyAfterDurableHistoryDeletion() {
        when(retention.clearAllData()).thenReturn(new RetentionResult(0, 0, 0, 0, 0));
        service.clearData("all");
        var order = inOrder(retention, observations);
        order.verify(retention).clearAllData();
        order.verify(observations).historyCleared();
        verify(alerts).clearIncidentState();
    }

    @Test
    void retainsLiveObservationsWhenHistoryDeletionFails() {
        when(retention.clearAllData()).thenThrow(new IllegalStateException("storage unavailable"));
        assertThatThrownBy(() -> service.clearData("all"))
                .isInstanceOf(IllegalStateException.class);
        verify(observations, never()).historyCleared();
        verify(alerts, never()).clearIncidentState();
    }
}
