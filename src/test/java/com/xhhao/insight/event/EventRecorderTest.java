package com.xhhao.insight.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.storage.capacity.HistoryWriteGate;
import com.xhhao.insight.storage.model.MonitorEvent;
import com.xhhao.insight.storage.repository.MonitorEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventRecorderTest {

    @Mock
    MonitorEventRepository repository;

    @Mock
    HistoryWriteGate writeGate;

    @Test
    void rejectedEventsAreDroppedWithoutTouchingTheRepository() {
        when(writeGate.allowsWrite()).thenReturn(false);
        EventRecorder recorder = recorder();

        for (int index = 0; index < 100; index++) {
            recorder.record("storage", "warning", "sqlite", "paused", "capacity reached");
        }

        verify(repository, never()).saveBatch(anyList());
    }

    @Test
    void admittedEventIsPersistedWithTheProvidedClock() {
        when(writeGate.allowsWrite()).thenReturn(true);
        EventRecorder recorder = recorder();

        recorder.record("plugin", "info", "PluginLinks", "started", "ready");

        ArgumentCaptor<java.util.List<MonitorEvent>> captor = ArgumentCaptor.forClass(
            java.util.List.class);
        verify(repository).saveBatch(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(event -> {
            assertThat(event.occurredAtMs()).isEqualTo(5_000);
            assertThat(event.fingerprint()).isEqualTo("plugin:PluginLinks:started");
        });
    }

    @Test
    void failureEventPersistsStructuredDiagnosticsWithoutTheExceptionMessage() {
        when(writeGate.allowsWrite()).thenReturn(true);
        EventRecorder recorder = recorder();

        recorder.recordFailure("runtime", "warning", "halo", "refresh failed",
            "will retry", "halo_runtime_refresh",
            new IllegalStateException("private service url"));

        ArgumentCaptor<java.util.List<MonitorEvent>> captor = ArgumentCaptor.forClass(
            java.util.List.class);
        verify(repository).saveBatch(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(event ->
            assertThat(event.detailsJson())
                .contains("\"kind\":\"monitoring_failure\"")
                .contains("\"operation\":\"halo_runtime_refresh\"")
                .contains("java.lang.IllegalStateException")
                .doesNotContain("private service url"));
    }

    private EventRecorder recorder() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC);
        return new EventRecorder(repository, writeGate, clock);
    }
}
