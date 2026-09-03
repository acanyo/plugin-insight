package com.xhhao.insight.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.config.AlertSettings;
import com.xhhao.insight.notification.AlertNotification;
import com.xhhao.insight.notification.NotificationDispatcher;
import com.xhhao.insight.notification.NotificationResult;
import com.xhhao.insight.storage.capacity.HistoryWriteGate;
import com.xhhao.insight.storage.model.AlertIncident;
import com.xhhao.insight.storage.model.MonitorEvent;
import com.xhhao.insight.storage.repository.AlertIncidentRepository;
import com.xhhao.insight.storage.repository.MonitorEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertEngineTest {

    @Mock
    AlertIncidentRepository incidents;

    @Mock
    MonitorEventRepository events;

    @Mock
    NotificationDispatcher notifications;

    @Mock
    HistoryWriteGate writeGate;

    private MutableClock clock;
    private AlertEngine engine;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.ofEpochMilli(1_000));
        engine = new AlertEngine(incidents, events, notifications, writeGate, clock);
        when(writeGate.allowsWrite()).thenReturn(true);
        when(incidents.findByStatus(AlertStatus.OPEN.name(), 1_000)).thenReturn(List.of());
        when(incidents.findByStatus(AlertStatus.ACKNOWLEDGED.name(), 1_000))
            .thenReturn(List.of());
        lenient().when(notifications.dispatch(any()))
            .thenReturn(CompletableFuture.completedFuture(List.<NotificationResult>of()));
    }

    @Test
    void observesDurationThenOpensRepeatsAcknowledgesAndRecovers() {
        AlertSettings settings = settings(60, 30);

        engine.evaluate(cpu(90), settings);
        clock.advance(Duration.ofSeconds(59));
        engine.evaluate(cpu(91), settings);
        verify(incidents, times(0)).save(any());

        clock.advance(Duration.ofSeconds(1));
        engine.evaluate(cpu(92), settings);

        ArgumentCaptor<AlertIncident> incidentCaptor =
            ArgumentCaptor.forClass(AlertIncident.class);
        verify(incidents).save(incidentCaptor.capture());
        AlertIncident opened = incidentCaptor.getValue();
        assertThat(opened.status()).isEqualTo(AlertStatus.OPEN.name());
        assertThat(opened.triggeredAtMs()).isEqualTo(1_000);
        assertThat(opened.lastValue()).isEqualTo(92);
        assertThat(engine.activeIncidents()).containsExactly(opened);
        verify(notifications).dispatch(any());

        clock.advance(Duration.ofSeconds(10));
        engine.evaluate(cpu(93), settings);
        verify(notifications).dispatch(any());

        clock.advance(Duration.ofMinutes(30));
        engine.evaluate(cpu(94), settings);
        verify(notifications, times(2)).dispatch(any());

        AlertIncident acknowledged = engine.acknowledge(opened.id());
        assertThat(acknowledged.status()).isEqualTo(AlertStatus.ACKNOWLEDGED.name());
        assertThat(engine.activeIncidents()).containsExactly(acknowledged);

        clock.advance(Duration.ofMinutes(30));
        engine.evaluate(cpu(95), settings);
        verify(notifications, times(2)).dispatch(any());

        clock.advance(Duration.ofSeconds(1));
        engine.evaluate(cpu(20), settings);

        assertThat(engine.activeIncidents()).isEmpty();
        ArgumentCaptor<AlertNotification> notificationCaptor =
            ArgumentCaptor.forClass(AlertNotification.class);
        verify(notifications, times(3)).dispatch(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
            .extracting(AlertNotification::recovered)
            .containsExactly(false, false, true);

        ArgumentCaptor<AlertIncident> allIncidents =
            ArgumentCaptor.forClass(AlertIncident.class);
        verify(incidents, times(6)).save(allIncidents.capture());
        AlertIncident resolved = allIncidents.getAllValues().get(5);
        assertThat(resolved.status()).isEqualTo(AlertStatus.RESOLVED.name());
        assertThat(resolved.resolvedAtMs()).isEqualTo(clock.millis());
        assertThat(resolved.occurrenceCount()).isEqualTo(4);

        ArgumentCaptor<MonitorEvent> eventCaptor = ArgumentCaptor.forClass(MonitorEvent.class);
        verify(events, times(4)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
            .extracting(MonitorEvent::severity)
            .containsExactly("warning", "warning", "info", "info");
    }

    @Test
    void restoresAndRecoversAnIncidentPersistedBeforeRestart() {
        AlertIncident persisted = new AlertIncident(
            "persisted", AlertRule.SYSTEM_CPU.ruleId(), AlertStatus.OPEN.name(), "warning",
            AlertRule.SYSTEM_CPU.title(), "still active", "insight-alert:system.cpu",
            1_000, 1_000, null, 4, 91d, 85d, "{}");
        when(incidents.findByStatus(AlertStatus.OPEN.name(), 1_000))
            .thenReturn(List.of(persisted));

        assertThat(engine.activeIncidents()).containsExactly(persisted);
        engine.evaluate(cpu(20), settings(0, 30));

        ArgumentCaptor<AlertIncident> captor = ArgumentCaptor.forClass(AlertIncident.class);
        verify(incidents).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AlertStatus.RESOLVED.name());
        assertThat(engine.activeIncidents()).isEmpty();
    }

    @Test
    void sideEffectFailuresDoNotEscapeEvaluation() {
        doThrow(new RuntimeException("sqlite unavailable")).when(incidents).save(any());
        doThrow(new RuntimeException("timeline unavailable")).when(events).save(any());
        when(notifications.dispatch(any())).thenThrow(new RuntimeException("executor stopped"));
        AlertSettings settings = settings(0, 30);

        assertThatCode(() -> engine.evaluate(cpu(99), settings)).doesNotThrowAnyException();
        assertThat(engine.activeIncidents()).hasSize(1);
        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> engine.evaluate(cpu(1), settings)).doesNotThrowAnyException();
        assertThat(engine.activeIncidents()).hasSize(1);
    }

    @Test
    void capacityRejectionSkipsHistoryWithoutStoppingRealtimeAlertTransitions() {
        when(writeGate.allowsWrite()).thenReturn(false);
        AlertSettings settings = settings(0, 30);

        engine.evaluate(cpu(99), settings);

        assertThat(engine.activeIncidents())
            .singleElement()
            .extracting(AlertIncident::status)
            .isEqualTo(AlertStatus.OPEN.name());
        verify(incidents, times(0)).save(any());
        verify(events, times(0)).save(any());
        verify(notifications).dispatch(any());

        clock.advance(Duration.ofSeconds(1));
        engine.evaluate(cpu(1), settings);

        assertThat(engine.activeIncidents()).isEmpty();
        verify(incidents, times(0)).save(any());
        verify(events, times(0)).save(any());
        ArgumentCaptor<AlertNotification> notificationsCaptor =
            ArgumentCaptor.forClass(AlertNotification.class);
        verify(notifications, times(2)).dispatch(notificationsCaptor.capture());
        assertThat(notificationsCaptor.getAllValues())
            .extracting(AlertNotification::recovered)
            .containsExactly(false, true);
    }

    @Test
    void acknowledgementStillRequiresDurableHistoryAdmission() {
        AlertSettings settings = settings(0, 30);
        engine.evaluate(cpu(99), settings);
        AlertIncident opened = engine.activeIncidents().getFirst();
        when(writeGate.allowsWrite()).thenReturn(false);

        assertThatThrownBy(() -> engine.acknowledge(opened.id()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to persist acknowledgement");

        assertThat(engine.activeIncidents().getFirst().status()).isEqualTo(AlertStatus.OPEN.name());
        verify(incidents).save(any());
    }

    @Test
    void failedResolutionRemainsActiveAndRetriesUntilDurable() {
        doNothing()
            .doThrow(new RuntimeException("temporary SQLite failure"))
            .doNothing()
            .when(incidents).save(any());
        AlertSettings settings = settings(0, 30);

        engine.evaluate(cpu(99), settings);
        engine.evaluate(cpu(1), settings);
        assertThat(engine.activeIncidents()).hasSize(1);

        engine.evaluate(cpu(1), settings);
        assertThat(engine.activeIncidents()).isEmpty();

        ArgumentCaptor<AlertIncident> captor = ArgumentCaptor.forClass(AlertIncident.class);
        verify(incidents, times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(AlertIncident::status)
            .containsExactly("OPEN", "RESOLVED", "RESOLVED");
    }

    @Test
    void disablingAlertsDurablyResolvesActiveIncidents() {
        AlertSettings settings = settings(0, 30);
        engine.evaluate(cpu(99), settings);

        settings.setEnabled(false);
        engine.evaluate(cpu(1), settings);

        assertThat(engine.activeIncidents()).isEmpty();
        ArgumentCaptor<AlertIncident> captor = ArgumentCaptor.forClass(AlertIncident.class);
        verify(incidents, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().getLast().status()).isEqualTo("RESOLVED");
    }

    @Test
    void acknowledgementCanLoadIncidentByIdAndIsIdempotent() {
        AlertIncident persisted = new AlertIncident(
            "external", AlertRule.REQUEST_P95.ruleId(), AlertStatus.OPEN.name(), "warning",
            AlertRule.REQUEST_P95.title(), "slow", "insight-alert:request.p95",
            500, 500, null, 1, 2_000d, 1_500d, "{}");
        when(incidents.findById("external")).thenReturn(Optional.of(persisted));

        AlertIncident acknowledged = engine.acknowledge("external");
        AlertIncident repeated = engine.acknowledge("external");

        assertThat(acknowledged.status()).isEqualTo(AlertStatus.ACKNOWLEDGED.name());
        assertThat(repeated).isEqualTo(acknowledged);
        verify(incidents).save(acknowledged);
        verify(events).save(any());
    }

    @Test
    void clearingIncidentStatePreventsDeletedAlertsFromReappearing() {
        engine.evaluate(cpu(99), settings(0, 30));
        assertThat(engine.activeIncidents()).hasSize(1);

        engine.clearIncidentState();

        assertThat(engine.activeIncidents()).isEmpty();
        // The lazy restore query must not rehydrate rows that the data command just deleted.
        verify(incidents, times(1)).findByStatus(AlertStatus.OPEN.name(), 1_000);
        verify(incidents, times(1)).findByStatus(AlertStatus.ACKNOWLEDGED.name(), 1_000);
    }

    @Test
    void clearIncidentHistoryDeletesDurableRowsBeforeResettingLiveState() {
        engine.evaluate(cpu(99), settings(0, 30));
        when(incidents.deleteAll()).thenReturn(1);

        assertThat(engine.clearIncidentHistory()).isEqualTo(1);
        assertThat(engine.activeIncidents()).isEmpty();
        verify(incidents).deleteAll();
        verify(incidents, times(1)).findByStatus(AlertStatus.OPEN.name(), 1_000);
        verify(incidents, times(1)).findByStatus(AlertStatus.ACKNOWLEDGED.name(), 1_000);
    }

    private static AlertSettings settings(int durationSeconds, int cooldownMinutes) {
        AlertSettings settings = new AlertSettings();
        settings.setDurationSeconds(durationSeconds);
        settings.setCooldownMinutes(cooldownMinutes);
        settings.setCpuWarningPercent(85);
        settings.setHeapWarningPercent(85);
        settings.setDiskWarningPercent(90);
        settings.setErrorRatePercent(5);
        settings.setP95WarningMillis(1_500);
        return settings;
    }

    private static AlertEvaluationInput cpu(double value) {
        return new AlertEvaluationInput(value, 20, 30, 0, 100, 0);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
