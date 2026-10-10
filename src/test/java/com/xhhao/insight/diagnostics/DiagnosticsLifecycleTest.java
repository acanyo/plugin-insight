package com.xhhao.insight.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.config.CollectionSettings;
import com.xhhao.insight.config.InsightSettingsService;
import com.xhhao.insight.optimization.OptimizationObservationService;
import com.xhhao.insight.optimization.OptimizationRuntimePolicy;
import com.xhhao.insight.optimization.OptimizationStateRepository;
import com.xhhao.insight.overhead.InsightOverheadMetrics;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.tasks.HaloTaskMonitor;

import org.junit.jupiter.api.Test;

class DiagnosticsLifecycleTest {
    @Test
    void recordsGuardedDiagnosticFailuresAndContinuesOtherChecks() throws Exception {
        var settings = mock(InsightSettingsService.class);
        when(settings.collection()).thenReturn(new CollectionSettings());
        var gc = mock(JvmPressureWindow.class);
        doThrow(new IllegalStateException("temporarily unavailable")).when(gc).add(null);
        var observations = mock(OptimizationObservationService.class);
        var jfr = mock(JfrDiagnostics.class);
        var overhead = new InsightOverheadMetrics();
        var lifecycle =
                new DiagnosticsLifecycle(
                        new MonitoringState(),
                        gc,
                        mock(GcMemoryObserver.class),
                        mock(ThreadDiagnostics.class),
                        mock(HaloTaskMonitor.class),
                        observations,
                        jfr,
                        mock(DiagnosticRecords.class),
                        new OptimizationRuntimePolicy(mock(OptimizationStateRepository.class)),
                        settings);
        lifecycle.measureOverhead(overhead);
        lifecycle.tick(1_000_000);
        verify(observations).tick(1_000_000);
        verify(jfr).tick();
        assertThat(overhead.snapshot().operations())
                .anySatisfy(
                        operation -> {
                            assertThat(operation.id()).isEqualTo("diagnostics");
                            assertThat(operation.calls()).isEqualTo(1);
                            assertThat(operation.failures()).isEqualTo(1);
                        });
    }

    @Test
    void appliesLowOverheadPolicyToThreadDiagnosticsAndRestoresIt() {
        var configured = new CollectionSettings();
        var settings = mock(InsightSettingsService.class);
        when(settings.collection()).thenReturn(configured);
        var policy = new OptimizationRuntimePolicy(mock(OptimizationStateRepository.class));
        var threads = mock(ThreadDiagnostics.class);
        var lifecycle =
                new DiagnosticsLifecycle(
                        mock(MonitoringState.class),
                        mock(JvmPressureWindow.class),
                        mock(GcMemoryObserver.class),
                        threads,
                        mock(HaloTaskMonitor.class),
                        mock(OptimizationObservationService.class),
                        mock(JfrDiagnostics.class),
                        mock(DiagnosticRecords.class),
                        policy,
                        settings);
        policy.enable(1_000_000);
        lifecycle.tick(1_000_000);
        lifecycle.tick(1_015_000);
        lifecycle.tick(1_030_000);
        verify(threads).sample(1_000_000, 256);
        verify(threads, never()).sample(1_015_000, 256);
        verify(threads).sample(1_030_000, 256);
        policy.disable(1_030_000);
        lifecycle.tick(1_045_000);
        verify(threads).sample(1_045_000, 512);
    }
}
