package com.xhhao.insight.overhead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

class InsightOverheadMetricsTest {
    @Test
    void measuresTaskCountersAndDoesNotCountNestedSqlCpuTwice() {
        var now = new AtomicLong(1_800_000_000_000L);
        var nanos = new AtomicLong();
        var cpu = new AtomicLong(100);
        var allocation = new AtomicLong(100);
        var clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(ignored -> now.get());
        var metrics =
                new InsightOverheadMetrics(
                        clock,
                        nanos::get,
                        new InsightOverheadMetrics.Counters() {
                            public long cpu() {
                                return cpu.get();
                            }

                            public long allocated() {
                                return allocation.get();
                            }
                        });
        var collection = metrics.begin(InsightOverheadMetrics.Operation.COLLECTION);
        try (var sql = metrics.begin(InsightOverheadMetrics.Operation.SQL_READ)) {
            nanos.set(10_000_000);
            cpu.addAndGet(2_000_000);
            allocation.addAndGet(4096);
        }
        now.addAndGet(1000);
        collection.close();
        collection.close();
        var snapshot = metrics.snapshot();
        assertThat(snapshot.databaseReads()).isEqualTo(1);
        assertThat(snapshot.cpuCorePercent()).isEqualTo(0.2);
        assertThat(snapshot.allocatedBytesPerMinute()).isEqualTo(4096 * 60);
        assertThat(snapshot.operations())
                .anySatisfy(
                        operation -> {
                            assertThat(operation.id()).isEqualTo("collection");
                            assertThat(operation.calls()).isEqualTo(1);
                            assertThat(operation.cpuTimeMs()).isEqualTo(2);
                        });
        assertThat(snapshot.points()).hasSize(1);
        now.addAndGet(61 * 60_000);
        assertThat(metrics.snapshot().operations()).isEmpty();
    }

    @Test
    void handlesUnsupportedCountersAndCountsFailures() {
        var metrics =
                new InsightOverheadMetrics(
                        Clock.systemUTC(),
                        System::nanoTime,
                        new InsightOverheadMetrics.Counters() {
                            public long cpu() {
                                return -1;
                            }

                            public long allocated() {
                                return -1;
                            }
                        });
        assertThatThrownBy(
                        () ->
                                metrics.run(
                                        InsightOverheadMetrics.Operation.COLLECTION,
                                        () -> {
                                            throw new IllegalStateException("failed");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        var snapshot = metrics.snapshot();
        assertThat(snapshot.cpuAvailable()).isFalse();
        assertThat(snapshot.allocationAvailable()).isFalse();
        assertThat(snapshot.operations().getFirst().failures()).isEqualTo(1);
        metrics.reset();
        assertThat(metrics.snapshot().operations()).isEmpty();
    }
}
