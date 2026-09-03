package com.xhhao.insight.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RequestMetricsRegistryTest {

    @Test
    void shouldAggregateByRouteMethodAndStatusGroup() {
        MutableClock clock = new MutableClock(120_000);
        RequestMetricsRegistry registry = new RequestMetricsRegistry(clock);

        registry.record("links", "/links/{name}", "GET", 200, 10);
        registry.record("links", "/links/{name}", "GET", 503, 250);

        assertThat(registry.currentSnapshot()).hasSize(2);
        assertThat(registry.currentSnapshot())
            .extracting(RequestMetric::statusGroup)
            .containsExactlyInAnyOrder(2, 5);
        assertThat(registry.currentSnapshot()).filteredOn(metric -> metric.statusGroup() == 5)
            .singleElement().satisfies(metric -> {
                assertThat(metric.errorCount()).isEqualTo(1);
                assertThat(metric.durationMaxMs()).isEqualTo(250);
            });
    }

    @Test
    void shouldRotateAndDrainCompletedMinute() {
        MutableClock clock = new MutableClock(120_000);
        RequestMetricsRegistry registry = new RequestMetricsRegistry(clock);
        registry.record("halo-core", "/", "GET", 200, 5);

        clock.setMillis(180_000);
        assertThat(registry.drainCompleted()).singleElement()
            .satisfies(metric -> assertThat(metric.bucketStartMs()).isEqualTo(120_000));
        assertThat(registry.drainCompleted()).isEmpty();
    }

    @Test
    void recordSelectedBeforeRolloverIsRetriedIntoAReachableBucket() throws Exception {
        MutableClock clock = new MutableClock(120_000);
        CountDownLatch selectedOldBucket = new CountDownLatch(1);
        CountDownLatch continueRecord = new CountDownLatch(1);
        AtomicBoolean firstSelection = new AtomicBoolean(true);
        RequestMetricsRegistry registry = new RequestMetricsRegistry(clock, bucketStart -> {
            if (firstSelection.compareAndSet(true, false)) {
                selectedOldBucket.countDown();
                await(continueRecord);
            }
        });

        Thread recorder = Thread.ofPlatform().start(() ->
            registry.record("halo-core", "/boundary", "GET", 200, 5));
        assertThat(selectedOldBucket.await(5, TimeUnit.SECONDS)).isTrue();

        clock.setMillis(180_000);
        assertThat(registry.drainCompleted()).isEmpty();
        continueRecord.countDown();
        recorder.join(5_000);

        assertThat(recorder.isAlive()).isFalse();
        assertThat(registry.currentSnapshot())
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.bucketStartMs()).isEqualTo(180_000);
                assertThat(metric.requestCount()).isEqualTo(1);
            });
    }

    @Test
    void shutdownDrainReturnsCompletedAndCurrentMetricsOnlyOnce() {
        MutableClock clock = new MutableClock(120_000);
        RequestMetricsRegistry registry = new RequestMetricsRegistry(clock);
        registry.record("halo-core", "/old", "GET", 200, 5);
        clock.setMillis(180_000);
        registry.record("halo-core", "/current", "GET", 200, 5);

        assertThat(registry.drainForShutdown())
            .extracting(RequestMetric::route)
            .containsExactlyInAnyOrder("/old", "/current");
        assertThat(registry.drainForShutdown()).isEmpty();
    }

    @Test
    void shouldBoundConcurrentCardinalityAndAggregateOverflow() throws Exception {
        MutableClock clock = new MutableClock(120_000);
        RequestMetricsRegistry registry = new RequestMetricsRegistry(clock);
        int threadCount = 16;
        int routesPerThread = 180;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threadCount);

        IntStream.range(0, threadCount).forEach(thread -> Thread.ofPlatform().start(() -> {
            await(start);
            for (int route = 0; route < routesPerThread; route++) {
                registry.record("plugin-" + thread, "/route-" + thread + "-" + route,
                    "GET", 200, 5);
            }
            finished.countDown();
        }));
        start.countDown();
        assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();

        for (int index = 0; index < 50; index++) {
            registry.record("plugin-0", "/route-0-0", "GET", 200, 5);
        }
        var snapshot = registry.currentSnapshot();
        assertThat(snapshot).hasSizeLessThanOrEqualTo(
            RequestMetricsRegistry.MAX_UNIQUE_KEYS_PER_MINUTE);
        assertThat(snapshot).filteredOn(metric -> metric.route().equals("/{overflow}"))
            .singleElement()
            .satisfies(metric -> assertThat(metric.requestCount()).isPositive());
        assertThat(snapshot).filteredOn(metric -> metric.route().equals("/route-0-0"))
            .singleElement()
            .satisfies(metric -> assertThat(metric.requestCount()).isEqualTo(51));
        assertThat(snapshot.stream().mapToLong(RequestMetric::requestCount).sum())
            .isEqualTo((long) threadCount * routesPerThread + 50);
    }

    @Test
    void shouldBoundEveryPersistedDimension() {
        RequestMetricsRegistry registry = new RequestMetricsRegistry(new MutableClock(120_000));
        String oversized = "x".repeat(10_000);

        registry.record(oversized, oversized, oversized, 200, 1);

        assertThat(registry.currentSnapshot()).singleElement().satisfies(metric -> {
            assertThat(metric.scope()).hasSizeLessThanOrEqualTo(
                RequestMetricDimensions.MAX_SCOPE_LENGTH);
            assertThat(metric.route()).startsWith("/")
                .hasSizeLessThanOrEqualTo(RequestMetricDimensions.MAX_ROUTE_LENGTH);
            assertThat(metric.method()).hasSizeLessThanOrEqualTo(
                RequestMetricDimensions.MAX_METHOD_LENGTH);
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for rollover test");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static final class MutableClock extends Clock {

        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        void setMillis(long millis) {
            this.millis = millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }
}
