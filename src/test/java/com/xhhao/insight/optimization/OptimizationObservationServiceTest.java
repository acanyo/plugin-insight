package com.xhhao.insight.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xhhao.insight.diagnostics.DiagnosticRecords;
import com.xhhao.insight.event.EventRecorder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import reactor.core.publisher.Mono;

import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.ReactiveExtensionClient;

import java.util.List;

class OptimizationObservationServiceTest {
    private final DiagnosticRecords records = mock(DiagnosticRecords.class);
    private final ObservationMetrics metrics = mock(ObservationMetrics.class);
    private final ReactiveExtensionClient plugins = mock(ReactiveExtensionClient.class);
    private final EventRecorder events = mock(EventRecorder.class);
    private final Plugin plugin = mock(Plugin.class, RETURNS_DEEP_STUBS);
    private final OptimizationObservationService service =
            new OptimizationObservationService(records, metrics, plugins, events);

    @BeforeEach
    void setup() {
        when(records.list(
                        "optimization-observation",
                        OptimizationObservationService.Observation.class,
                        100))
                .thenReturn(List.of());
        when(plugins.fetch(Plugin.class, "example")).thenReturn(Mono.just(plugin));
        when(plugin.getSpec().getEnabled()).thenReturn(true);
        when(metrics.summarize(anyLong(), anyLong()))
                .thenReturn(
                        new ObservationMetrics.Summary(0, 300_000, 20, true, 1, 1, 1, 100, 10L));
    }

    @Test
    void doesNotReadHistoryDuringIdleTicks() {
        service.interruptPending();
        for (int index = 0; index < 240; index++) {
            service.tick(1_000_000L + index * 15_000L);
        }
        verify(records)
                .list(
                        "optimization-observation",
                        OptimizationObservationService.Observation.class,
                        100);
        verifyNoInteractions(metrics, events);
    }

    @Test
    void observesNewWorkAndStopsPollingAfterCompletion() {
        var prepared = service.prepare("example");
        when(plugin.getSpec().getEnabled()).thenReturn(false);
        var observing = service.confirm(prepared.id());
        service.tick(observing.toMs() + 30_000);
        service.tick(observing.toMs() + 45_000);

        var saved = ArgumentCaptor.forClass(OptimizationObservationService.Observation.class);
        verify(records, times(3))
                .save(
                        eq("optimization-observation"),
                        eq(prepared.id()),
                        anyLong(),
                        saved.capture());
        assertThat(saved.getAllValues())
                .extracting(OptimizationObservationService.Observation::state)
                .containsExactly("prepared", "observing", "completed");
        verify(metrics).summarize(observing.fromMs(), observing.toMs());
        verify(records)
                .list(
                        "optimization-observation",
                        OptimizationObservationService.Observation.class,
                        100);
    }

    @Test
    void retriesCompletionWhenDurableSaveFails() {
        var prepared = service.prepare("example");
        when(plugin.getSpec().getEnabled()).thenReturn(false);
        var observing = service.confirm(prepared.id());
        doThrow(new IllegalStateException("storage unavailable"))
                .doNothing()
                .when(records)
                .save(eq("optimization-observation"), eq(prepared.id()), anyLong(), any());
        assertThatThrownBy(() -> service.tick(observing.toMs() + 30_000))
                .isInstanceOf(IllegalStateException.class);
        service.tick(observing.toMs() + 45_000);
        service.tick(observing.toMs() + 60_000);

        verify(metrics, times(2)).summarize(observing.fromMs(), observing.toMs());
        verify(records, times(4))
                .save(eq("optimization-observation"), eq(prepared.id()), anyLong(), any());
    }

    @Test
    void cancelsPreparedWorkWithoutLoadingHistoryAgain() {
        var prepared = service.prepare("example");
        service.tick(prepared.createdAtMs() + 120_001);
        service.tick(prepared.createdAtMs() + 135_001);
        var saved = ArgumentCaptor.forClass(OptimizationObservationService.Observation.class);
        verify(records, times(2))
                .save(
                        eq("optimization-observation"),
                        eq(prepared.id()),
                        anyLong(),
                        saved.capture());
        assertThat(saved.getValue().state()).isEqualTo("cancelled");
        verify(records)
                .list(
                        "optimization-observation",
                        OptimizationObservationService.Observation.class,
                        100);
    }

    @Test
    void interruptsDurableActiveWorkOnEveryLifecycleStart() {
        var pending =
                new OptimizationObservationService.Observation(
                        "pending",
                        "example",
                        "Example",
                        "observing",
                        1,
                        60_000,
                        360_000,
                        null,
                        null,
                        "");
        when(records.list(
                        "optimization-observation",
                        OptimizationObservationService.Observation.class,
                        100))
                .thenReturn(List.of())
                .thenReturn(List.of(pending));
        service.interruptPending();
        service.interruptPending();
        service.tick(500_000);
        var saved = ArgumentCaptor.forClass(OptimizationObservationService.Observation.class);
        verify(records)
                .save(eq("optimization-observation"), eq("pending"), anyLong(), saved.capture());
        assertThat(saved.getValue().state()).isEqualTo("interrupted");
        verify(records, times(2))
                .list(
                        "optimization-observation",
                        OptimizationObservationService.Observation.class,
                        100);
        verifyNoInteractions(metrics, plugins);
    }

    @Test
    void doesNotRecreateObservationsAfterHistoryIsCleared() {
        var prepared = service.prepare("example");
        service.historyCleared();
        service.tick(prepared.createdAtMs() + 120_001);
        verify(records).save(eq("optimization-observation"), eq(prepared.id()), anyLong(), any());
    }

    @Test
    void retriesInitialReadAfterStorageRecovers() {
        when(records.list(
                        "optimization-observation",
                        OptimizationObservationService.Observation.class,
                        100))
                .thenThrow(new IllegalStateException("storage unavailable"))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.tick(1_000_000)).isInstanceOf(IllegalStateException.class);
        service.tick(1_015_000);
        service.tick(1_030_000);
        verify(records, times(2))
                .list(
                        "optimization-observation",
                        OptimizationObservationService.Observation.class,
                        100);
    }
}
