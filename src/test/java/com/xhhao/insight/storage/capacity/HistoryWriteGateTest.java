package com.xhhao.insight.storage.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.config.CollectionSettings;
import com.xhhao.insight.config.InsightSettingsService;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.health.StorageHealthSnapshot;
import com.xhhao.insight.storage.health.StorageState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HistoryWriteGateTest {

    private final SqliteStorageLifecycle storage = mock(SqliteStorageLifecycle.class);
    private final StorageCapacityGuard capacity = mock(StorageCapacityGuard.class);
    private final InsightSettingsService settings = mock(InsightSettingsService.class);
    private final MonitoringState state = mock(MonitoringState.class);
    private HistoryWriteGate gate;

    @BeforeEach
    void setUp() {
        CollectionSettings collection = new CollectionSettings();
        collection.setMaxDatabaseMegabytes(256);
        when(settings.collection()).thenReturn(collection);
        gate = new HistoryWriteGate(storage, capacity, settings, state);
    }

    @Test
    void unavailableStorageRejectsWithoutRunningCapacityQuery() {
        when(storage.health()).thenReturn(health(StorageState.DEGRADED));

        assertThat(gate.allowsWrite()).isFalse();

        verify(capacity, never()).exceedsMegabytes(256);
    }

    @Test
    void capacityDecisionUpdatesSharedPauseState() {
        when(storage.health()).thenReturn(health(StorageState.AVAILABLE));
        when(capacity.exceedsMegabytes(256)).thenReturn(true, false);

        assertThat(gate.allowsWrite()).isFalse();
        verify(state).setStorageWritePaused(true);

        assertThat(gate.allowsWrite()).isTrue();
        verify(state).setStorageWritePaused(false);
    }

    @Test
    void capacityCheckFailureRejectsSafelyAndDoesNotEscape() {
        when(storage.health()).thenReturn(health(StorageState.AVAILABLE));
        when(capacity.exceedsMegabytes(256)).thenThrow(new IllegalStateException("failed"));

        assertThatCode(gate::allowsWrite).doesNotThrowAnyException();
        assertThat(gate.allowsWrite()).isFalse();
        verify(state, times(2)).setStorageWritePaused(true);
    }

    private static StorageHealthSnapshot health(StorageState storageState) {
        return new StorageHealthSnapshot(storageState, storageState.name(), 1, 0);
    }
}
