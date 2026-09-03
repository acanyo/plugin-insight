package com.xhhao.insight.collection;

import com.xhhao.insight.metrics.RequestMetric;
import com.xhhao.insight.metrics.RequestWindowSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RollingRequestWindow {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final Clock clock;
    private final Deque<RequestMetric> completed = new ArrayDeque<>();

    public RollingRequestWindow() {
        this(Clock.systemUTC());
    }

    RollingRequestWindow(Clock clock) {
        this.clock = clock;
    }

    public synchronized void addCompleted(List<RequestMetric> metrics) {
        completed.addAll(metrics);
        evictExpired(clock.millis());
    }

    public synchronized RequestWindowSnapshot snapshot(List<RequestMetric> current) {
        long nowMs = clock.millis();
        evictExpired(nowMs);
        List<RequestMetric> all = new ArrayList<>(completed.size() + current.size());
        all.addAll(completed);
        all.addAll(current);
        return RequestMetricAggregator.aggregate(all, nowMs);
    }

    private void evictExpired(long nowMs) {
        long cutoff = nowMs - WINDOW.toMillis();
        while (!completed.isEmpty() && completed.peekFirst().bucketStartMs() < cutoff) {
            completed.removeFirst();
        }
    }
}
