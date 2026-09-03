package com.xhhao.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.alert.AlertEngine;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.health.StorageHealthSnapshot;
import com.xhhao.insight.storage.health.StorageState;
import com.xhhao.insight.storage.model.AlertIncident;
import com.xhhao.insight.storage.model.AlertIncidentCursor;
import com.xhhao.insight.storage.model.AlertIncidentPage;
import com.xhhao.insight.storage.repository.AlertIncidentRepository;
import com.xhhao.insight.storage.repository.MonitorEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentQueryServiceTest {

    @Test
    void allStatusRetainsInMemoryActiveAlertsWhenStorageIsUnavailable() {
        SqliteStorageLifecycle storage = mock(SqliteStorageLifecycle.class);
        MonitorEventRepository events = mock(MonitorEventRepository.class);
        AlertIncidentRepository alerts = mock(AlertIncidentRepository.class);
        AlertEngine engine = mock(AlertEngine.class);
        when(storage.health()).thenReturn(new StorageHealthSnapshot(
            StorageState.DEGRADED, "SQLite unavailable", 10, 2));
        AlertIncident active = incident("active", "OPEN", 2_000);
        when(engine.activeIncidents()).thenReturn(List.of(active));

        IncidentQueryService service = new IncidentQueryService(
            storage, events, alerts, engine, new EventCursorCodec(),
            Clock.fixed(Instant.ofEpochMilli(3_000), ZoneOffset.UTC));

        assertThat(service.alerts("all", 50)).containsExactly(active);
        verify(alerts, never()).findByStatus("RESOLVED", 50);
    }

    @Test
    void allStatusUsesStableUpdatedTimeAndIdOrdering() {
        SqliteStorageLifecycle storage = mock(SqliteStorageLifecycle.class);
        MonitorEventRepository events = mock(MonitorEventRepository.class);
        AlertIncidentRepository alerts = mock(AlertIncidentRepository.class);
        AlertEngine engine = mock(AlertEngine.class);
        when(storage.health()).thenReturn(new StorageHealthSnapshot(
            StorageState.AVAILABLE, "ok", 10, 0));
        when(engine.activeIncidents()).thenReturn(List.of(incident("a", "OPEN", 2_000)));
        when(alerts.findByStatus("RESOLVED", 10)).thenReturn(List.of(
            incident("b", "RESOLVED", 2_000), incident("c", "RESOLVED", 1_000)));

        IncidentQueryService service = new IncidentQueryService(
            storage, events, alerts, engine, new EventCursorCodec(),
            Clock.fixed(Instant.ofEpochMilli(3_000), ZoneOffset.UTC));

        assertThat(service.alerts("all", 10)).extracting(AlertIncident::id)
            .containsExactly("b", "a", "c");
    }

    @Test
    void preservesRepositoryContinuationWhenTheFirstPageIsExactlyFull() {
        SqliteStorageLifecycle storage = mock(SqliteStorageLifecycle.class);
        MonitorEventRepository events = mock(MonitorEventRepository.class);
        AlertIncidentRepository alerts = mock(AlertIncidentRepository.class);
        AlertEngine engine = mock(AlertEngine.class);
        when(storage.health()).thenReturn(new StorageHealthSnapshot(
            StorageState.AVAILABLE, "ok", 10, 0));
        when(engine.activeIncidents()).thenReturn(List.of());
        AlertIncident first = incident("first", "RESOLVED", 2_000);
        AlertIncidentCursor repositoryCursor = new AlertIncidentCursor(2_000, "first");
        when(alerts.findPage("RESOLVED", null, 2))
            .thenReturn(new AlertIncidentPage(List.of(first), repositoryCursor));

        IncidentQueryService service = new IncidentQueryService(
            storage, events, alerts, engine, new EventCursorCodec(),
            Clock.fixed(Instant.ofEpochMilli(3_000), ZoneOffset.UTC));

        var page = service.alertHistory("resolved", null, 1);

        assertThat(page.items()).containsExactly(first);
        assertThat(page.nextCursor()).isNotBlank();
        verify(alerts).findPage("RESOLVED", null, 2);
    }

    private static AlertIncident incident(String id, String status, long updatedAtMs) {
        return new AlertIncident(
            id, "rule-1", status, "warning", "Alert", "Summary", "fingerprint-" + id,
            1_000, updatedAtMs, "RESOLVED".equals(status) ? updatedAtMs : null,
            1, 90d, 80d, null);
    }
}
