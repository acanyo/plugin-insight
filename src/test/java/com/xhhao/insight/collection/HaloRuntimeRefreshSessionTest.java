package com.xhhao.insight.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.event.EventRecorder;
import com.xhhao.insight.halo.HaloRuntimeService;
import com.xhhao.insight.halo.HaloRuntimeSnapshot;
import com.xhhao.insight.runtime.MonitoringState;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

class HaloRuntimeRefreshSessionTest {

    @Test
    void concurrentRefreshRequestsShareOneInFlightSubscription() throws Exception {
        HaloRuntimeService runtime = mock(HaloRuntimeService.class);
        when(runtime.snapshot()).thenReturn(Mono.<HaloRuntimeSnapshot>never());
        HaloRuntimeRefreshSession session = session(runtime, mock(MonitoringState.class));
        session.start();

        int callers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int index = 0; index < callers; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    release.await();
                    return session.refresh();
                }));
            }
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            int started = 0;
            for (Future<Boolean> result : results) {
                if (result.get(2, TimeUnit.SECONDS)) {
                    started++;
                }
            }
            assertThat(started).isEqualTo(1);
            verify(runtime).snapshot();
        } finally {
            session.stop();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void stopCancelsRefreshAndRejectsResultsFromThePreviousLifecycle() {
        HaloRuntimeService runtime = mock(HaloRuntimeService.class);
        MonitoringState state = mock(MonitoringState.class);
        Sinks.One<HaloRuntimeSnapshot> first = Sinks.one();
        Sinks.One<HaloRuntimeSnapshot> second = Sinks.one();
        doReturn(first.asMono()).doReturn(second.asMono()).when(runtime).snapshot();
        HaloRuntimeRefreshSession session = session(runtime, state);
        HaloRuntimeSnapshot stale = snapshot("stale");
        HaloRuntimeSnapshot current = snapshot("current");

        session.start();
        assertThat(session.refresh()).isTrue();
        assertThat(first.currentSubscriberCount()).isEqualTo(1);
        session.stop();
        assertThat(first.currentSubscriberCount()).isZero();
        first.tryEmitValue(stale);
        verify(state, never()).updateHalo(any());

        session.start();
        assertThat(session.refresh()).isTrue();
        second.emitValue(current, Sinks.EmitFailureHandler.FAIL_FAST);

        verify(state).updateHalo(current);
        verify(state).haloRefreshSucceeded(anyLong());
        session.stop();
    }

    @Test
    void hungRefreshTimesOutAndAllowsTheNextRefresh() throws Exception {
        HaloRuntimeService runtime = mock(HaloRuntimeService.class);
        when(runtime.snapshot()).thenReturn(Mono.<HaloRuntimeSnapshot>never());
        MonitoringState state = mock(MonitoringState.class);
        CountDownLatch failed = new CountDownLatch(1);
        doAnswer(invocation -> {
            failed.countDown();
            return true;
        }).when(state).haloRefreshFailed(anyLong(), any());
        Scheduler timeoutScheduler = Schedulers.newSingle("halo-refresh-timeout-test");
        HaloRuntimeRefreshSession session = new HaloRuntimeRefreshSession(
            runtime, state, mock(EventRecorder.class), Clock.systemUTC(),
            Duration.ofMillis(25), timeoutScheduler);
        try {
            session.start();
            assertThat(session.refresh()).isTrue();
            assertThat(failed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(session.refresh()).isTrue();
            verify(runtime, times(2)).snapshot();
        } finally {
            session.stop();
            timeoutScheduler.dispose();
        }
    }

    @Test
    void failedRefreshRecordsTypedDiagnostics() {
        HaloRuntimeService runtime = mock(HaloRuntimeService.class);
        IllegalStateException failure = new IllegalStateException("private backend detail");
        when(runtime.snapshot()).thenReturn(Mono.error(failure));
        MonitoringState state = mock(MonitoringState.class);
        when(state.haloRefreshFailed(anyLong(), any())).thenReturn(true);
        EventRecorder events = mock(EventRecorder.class);
        HaloRuntimeRefreshSession session = new HaloRuntimeRefreshSession(
            runtime, state, events, Clock.systemUTC(),
            Duration.ofSeconds(5), Schedulers.parallel());

        session.start();
        assertThat(session.refresh()).isTrue();

        verify(events).recordFailure(
            "runtime", "warning", "halo", "Halo 运行状态刷新异常",
            "插件、数据库、搜索和附件存储状态本轮未完成刷新",
            "halo_runtime_refresh", failure);
        session.stop();
    }

    private static HaloRuntimeRefreshSession session(
        HaloRuntimeService runtime,
        MonitoringState state
    ) {
        return new HaloRuntimeRefreshSession(
            runtime, state, mock(EventRecorder.class), Clock.systemUTC(),
            Duration.ofSeconds(5), Schedulers.parallel());
    }

    private static HaloRuntimeSnapshot snapshot(String version) {
        return new HaloRuntimeSnapshot(version, null, List.of(), List.of());
    }
}
