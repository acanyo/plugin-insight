package com.xhhao.insight.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.stream.LongStream;

class ThreadDiagnosticsTest {
    @Test
    void readsCpuCountersInOneBoundedBatch() {
        var bean = mock(com.sun.management.ThreadMXBean.class);
        when(bean.isThreadCpuTimeSupported()).thenReturn(true);
        when(bean.isThreadCpuTimeEnabled()).thenReturn(true);
        when(bean.getAllThreadIds()).thenReturn(LongStream.rangeClosed(1, 400).toArray());
        when(bean.getThreadCpuTime(any(long[].class)))
                .thenAnswer(invocation -> new long[((long[]) invocation.getArgument(0)).length]);
        when(bean.getThreadInfo(any(long[].class), eq(32))).thenReturn(new ThreadInfo[0]);
        var diagnostics = new ThreadDiagnostics(bean);
        diagnostics.sample(1_000, 256);
        var ids = ArgumentCaptor.forClass(long[].class);
        verify(bean).getThreadCpuTime(ids.capture());
        assertThat(ids.getValue()).hasSize(256);
        verify(bean, never()).getThreadCpuTime(anyLong());
        assertThat(diagnostics.snapshot().inspected()).isEqualTo(256);
    }

    @Test
    void preservesCpuDeltasAndClearsBaselineOnRestart() {
        var bean = mock(com.sun.management.ThreadMXBean.class);
        when(bean.isThreadCpuTimeSupported()).thenReturn(true);
        when(bean.isThreadCpuTimeEnabled()).thenReturn(true);
        when(bean.getAllThreadIds()).thenReturn(new long[] {1});
        when(bean.getThreadCpuTime(any(long[].class)))
                .thenReturn(new long[] {100_000}, new long[] {10_100_000}, new long[] {20_100_000});
        var info = mock(ThreadInfo.class);
        when(info.getThreadId()).thenReturn(1L);
        when(info.getThreadState()).thenReturn(Thread.State.RUNNABLE);
        when(info.getStackTrace()).thenReturn(new StackTraceElement[0]);
        when(bean.getThreadInfo(any(long[].class), eq(32))).thenReturn(new ThreadInfo[] {info});
        var diagnostics = new ThreadDiagnostics(bean);
        diagnostics.sample(1_000, 256);
        diagnostics.sample(2_000, 256);
        var snapshot = diagnostics.snapshot();
        assertThat(snapshot.observedMs()).isEqualTo(1_000);
        assertThat(snapshot.threads().getFirst().cpuCorePercent()).isEqualTo(1);
        diagnostics.reset();
        diagnostics.sample(3_000, 256);
        assertThat(diagnostics.snapshot().observedMs()).isZero();
        assertThat(diagnostics.snapshot().threads().getFirst().cpuCorePercent()).isZero();
    }

    @Test
    void supportsStandardThreadMxBeanWithoutBulkCounters() {
        var bean = mock(ThreadMXBean.class);
        when(bean.isThreadCpuTimeSupported()).thenReturn(true);
        when(bean.isThreadCpuTimeEnabled()).thenReturn(true);
        when(bean.getAllThreadIds()).thenReturn(new long[] {1, 2});
        when(bean.getThreadCpuTime(1)).thenReturn(1_000L);
        when(bean.getThreadCpuTime(2)).thenReturn(-1L);
        var diagnostics = new ThreadDiagnostics(bean);
        diagnostics.sample(1_000, 256);
        verify(bean).getThreadCpuTime(1);
        verify(bean).getThreadCpuTime(2);
    }
}
