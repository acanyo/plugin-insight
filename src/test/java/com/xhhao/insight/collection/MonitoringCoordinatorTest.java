package com.xhhao.insight.collection;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.alert.AlertEngine;
import com.xhhao.insight.config.CollectionSettings;
import com.xhhao.insight.config.InsightSettingsService;
import com.xhhao.insight.event.EventRecorder;
import com.xhhao.insight.event.RequestFailureEventBuffer;
import com.xhhao.insight.metrics.JvmSystemMetricsCollector;
import com.xhhao.insight.metrics.RequestMetricsRegistry;
import com.xhhao.insight.resource.PluginResourceSampler;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.storage.capacity.HistoryWriteGate;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.health.StorageHealthSnapshot;
import com.xhhao.insight.storage.health.StorageState;
import com.xhhao.insight.storage.repository.SystemMetricStorage;
import com.xhhao.insight.storage.repository.PluginResourceRepository;
import com.xhhao.insight.storage.retention.RetentionService;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MonitoringCoordinatorTest {

    @Test
    void sameInstanceCreatesANewSchedulerForEachStartStopLifecycle() throws Exception {
        JvmSystemMetricsCollector systemCollector = mock(JvmSystemMetricsCollector.class);
        RequestMetricsRegistry requestRegistry = mock(RequestMetricsRegistry.class);
        RollingRequestWindow requestWindow = mock(RollingRequestWindow.class);
        SystemMetricStorage systemMetrics = mock(SystemMetricStorage.class);
        PluginResourceSampler resourceSampler = mock(PluginResourceSampler.class);
        PluginResourceRepository pluginResources = mock(PluginResourceRepository.class);
        CompletedMetricBuffer metricBuffer = mock(CompletedMetricBuffer.class);
        RetentionService retention = mock(RetentionService.class);
        SqliteStorageLifecycle storage = mock(SqliteStorageLifecycle.class);
        HistoryWriteGate writeGate = mock(HistoryWriteGate.class);
        InsightSettingsService settings = mock(InsightSettingsService.class);
        HaloRuntimeRefreshSession haloRefresh = mock(HaloRuntimeRefreshSession.class);
        MonitoringState state = mock(MonitoringState.class);
        EventRecorder events = mock(EventRecorder.class);
        RequestFailureEventBuffer requestFailureEvents = mock(RequestFailureEventBuffer.class);
        AlertEngine alerts = mock(AlertEngine.class);
        StorageRecoveryMonitor recovery = mock(StorageRecoveryMonitor.class);
        MonitoringSchedulerFactory schedulers = mock(MonitoringSchedulerFactory.class);
        ScheduledExecutorService firstExecutor = mock(ScheduledExecutorService.class);
        ScheduledExecutorService secondExecutor = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> firstTask = mock(ScheduledFuture.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> secondTask = mock(ScheduledFuture.class);

        when(schedulers.create()).thenReturn(firstExecutor, secondExecutor);
        doReturn(firstTask).when(firstExecutor).scheduleWithFixedDelay(
            org.mockito.ArgumentMatchers.any(Runnable.class),
            eq(0L), eq(5L), eq(TimeUnit.SECONDS));
        doReturn(secondTask).when(secondExecutor).scheduleWithFixedDelay(
            org.mockito.ArgumentMatchers.any(Runnable.class),
            eq(0L), eq(5L), eq(TimeUnit.SECONDS));
        when(firstExecutor.awaitTermination(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(secondExecutor.awaitTermination(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(requestRegistry.drainForShutdown()).thenReturn(List.of());
        when(settings.collection()).thenReturn(new CollectionSettings());
        when(storage.health()).thenReturn(new StorageHealthSnapshot(
            StorageState.AVAILABLE, "available", 1, 0));

        MonitoringCoordinator coordinator = new MonitoringCoordinator(
            systemCollector, requestRegistry, requestWindow, systemMetrics,
            resourceSampler, pluginResources, metricBuffer,
            retention, storage, writeGate, settings, haloRefresh, state, events,
            requestFailureEvents, alerts,
            recovery, schedulers, Clock.systemUTC());

        coordinator.start();
        coordinator.stop();
        coordinator.start();
        coordinator.stop();

        InOrder lifecycle = inOrder(schedulers, firstExecutor, secondExecutor);
        lifecycle.verify(schedulers).create();
        lifecycle.verify(firstExecutor).scheduleWithFixedDelay(
            org.mockito.ArgumentMatchers.any(Runnable.class),
            eq(0L), eq(5L), eq(TimeUnit.SECONDS));
        lifecycle.verify(firstExecutor).shutdown();
        lifecycle.verify(schedulers).create();
        lifecycle.verify(secondExecutor).scheduleWithFixedDelay(
            org.mockito.ArgumentMatchers.any(Runnable.class),
            eq(0L), eq(5L), eq(TimeUnit.SECONDS));
        lifecycle.verify(secondExecutor).shutdown();
        verify(firstTask).cancel(false);
        verify(secondTask).cancel(false);
        verify(haloRefresh, times(2)).start();
        verify(haloRefresh, times(2)).stop();
        verify(resourceSampler, times(2)).start(anyLong());
        verify(resourceSampler, times(2)).stop(anyLong());
        verify(state, times(2)).setRunning(true);
        verify(state, times(2)).setRunning(false);
    }
}
