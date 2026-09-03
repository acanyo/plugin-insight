package com.xhhao.insight.metrics;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Component;

@Component
public class RequestMetricsRegistry {

    private static final long MINUTE_MS = 60_000;
    static final int MAX_UNIQUE_KEYS_PER_MINUTE = 2_000;
    private static final int STATUS_GROUP_COUNT = 5;
    private static final int MAX_REGULAR_KEYS_PER_MINUTE =
        MAX_UNIQUE_KEYS_PER_MINUTE - STATUS_GROUP_COUNT;
    private static final String OVERFLOW_SCOPE = "_overflow";
    private static final String OVERFLOW_ROUTE = "/{overflow}";
    private static final String OVERFLOW_METHOD = "OTHER";

    private final Clock clock;
    private final BucketSelectedHook bucketSelectedHook;
    private final ConcurrentLinkedQueue<MinuteBucket> completed = new ConcurrentLinkedQueue<>();
    private volatile MinuteBucket current;

    public RequestMetricsRegistry() {
        this(Clock.systemUTC());
    }

    RequestMetricsRegistry(Clock clock) {
        this(clock, null);
    }

    RequestMetricsRegistry(Clock clock, BucketSelectedHook bucketSelectedHook) {
        this.clock = clock;
        this.bucketSelectedHook = bucketSelectedHook;
        this.current = new MinuteBucket(minuteStart(clock.millis()));
    }

    public void record(String scope, String route, String method, int status, long durationMs) {
        RequestMetricDimensions dimensions = RequestMetricDimensions.normalize(scope, route, method);
        record(dimensions, status, durationMs);
    }

    void record(RequestMetricDimensions dimensions, int status, long durationMs) {
        int statusGroup = status <= 0 ? 5 : Math.max(1, Math.min(5, status / 100));
        RequestKey key = new RequestKey(dimensions.scope(), dimensions.route(),
            dimensions.method(), statusGroup);
        while (true) {
            MinuteBucket bucket = activeBucket();
            if (bucketSelectedHook != null) {
                bucketSelectedHook.selected(bucket.startMs);
            }
            if (bucket.record(key, status, durationMs)) {
                return;
            }
        }
    }

    public List<RequestMetric> drainCompleted() {
        activeBucket();
        List<RequestMetric> metrics = new ArrayList<>();
        MinuteBucket bucket;
        while ((bucket = completed.poll()) != null) {
            metrics.addAll(bucket.snapshot());
        }
        return metrics;
    }

    public List<RequestMetric> currentSnapshot() {
        return activeBucket().snapshot();
    }

    /**
     * Atomically drains completed buckets and snapshots the current bucket once for shutdown.
     */
    public synchronized List<RequestMetric> drainForShutdown() {
        MinuteBucket active = current;
        active.seal();
        List<RequestMetric> metrics = new ArrayList<>();
        MinuteBucket completedBucket;
        while ((completedBucket = completed.poll()) != null) {
            metrics.addAll(completedBucket.snapshot());
        }
        metrics.addAll(active.snapshot());
        current = new MinuteBucket(minuteStart(clock.millis()));
        return metrics;
    }

    private MinuteBucket activeBucket() {
        long expectedStart = minuteStart(clock.millis());
        MinuteBucket bucket = current;
        if (bucket.startMs == expectedStart) {
            return bucket;
        }
        synchronized (this) {
            bucket = current;
            if (bucket.startMs != expectedStart) {
                bucket.seal();
                completed.add(bucket);
                current = new MinuteBucket(expectedStart);
            }
            return current;
        }
    }

    private static long minuteStart(long timestampMs) {
        return timestampMs - Math.floorMod(timestampMs, MINUTE_MS);
    }

    private record RequestKey(String scope, String route, String method, int statusGroup) {}

    @FunctionalInterface
    interface BucketSelectedHook {

        void selected(long bucketStartMs);
    }

    private static final class MinuteBucket {
        private final long startMs;
        private final Map<RequestKey, Accumulator> accumulators = new ConcurrentHashMap<>();
        private final AtomicInteger regularKeyCount = new AtomicInteger();
        private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
        private boolean sealed;

        private MinuteBucket(long startMs) {
            this.startMs = startMs;
        }

        private boolean record(RequestKey key, int status, long durationMs) {
            lifecycleLock.readLock().lock();
            try {
                if (sealed) {
                    return false;
                }
                Accumulator accumulator = accumulators.get(key);
                if (accumulator == null) {
                    accumulator = accumulators.computeIfAbsent(key, ignored ->
                        reserveRegularKey() ? new Accumulator() : null);
                }
                if (accumulator == null) {
                    RequestKey overflowKey = new RequestKey(OVERFLOW_SCOPE, OVERFLOW_ROUTE,
                        OVERFLOW_METHOD, key.statusGroup());
                    accumulator = accumulators.computeIfAbsent(overflowKey,
                        ignored -> new Accumulator());
                }
                accumulator.record(status, durationMs);
                return true;
            } finally {
                lifecycleLock.readLock().unlock();
            }
        }

        private boolean reserveRegularKey() {
            while (true) {
                int count = regularKeyCount.get();
                if (count >= MAX_REGULAR_KEYS_PER_MINUTE) {
                    return false;
                }
                if (regularKeyCount.compareAndSet(count, count + 1)) {
                    return true;
                }
            }
        }

        private void seal() {
            lifecycleLock.writeLock().lock();
            try {
                sealed = true;
            } finally {
                lifecycleLock.writeLock().unlock();
            }
        }

        private List<RequestMetric> snapshot() {
            lifecycleLock.writeLock().lock();
            try {
                return accumulators.entrySet().stream()
                    .map(entry -> entry.getValue().snapshot(startMs, entry.getKey()))
                    .toList();
            } finally {
                lifecycleLock.writeLock().unlock();
            }
        }
    }

    private static final class Accumulator {
        private final LongAdder requests = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder durationTotalMs = new LongAdder();
        private final AtomicLong durationMaxMs = new AtomicLong();
        private final LongAdder[] latency = new LongAdder[LatencyBuckets.size()];

        private Accumulator() {
            for (int index = 0; index < latency.length; index++) {
                latency[index] = new LongAdder();
            }
        }

        private void record(int status, long durationMs) {
            long safeDuration = Math.max(0, durationMs);
            requests.increment();
            durationTotalMs.add(safeDuration);
            durationMaxMs.accumulateAndGet(safeDuration, Math::max);
            latency[LatencyBuckets.index(safeDuration)].increment();
            if (status >= 500 || status == 0) {
                errors.increment();
            }
        }

        private RequestMetric snapshot(long bucketStart, RequestKey key) {
            long[] histogram = new long[latency.length];
            for (int index = 0; index < latency.length; index++) {
                histogram[index] = latency[index].sum();
            }
            return new RequestMetric(bucketStart, key.scope(), key.route(), key.method(),
                key.statusGroup(), requests.sum(), errors.sum(), durationTotalMs.sum(),
                durationMaxMs.get(), histogram);
        }
    }
}
