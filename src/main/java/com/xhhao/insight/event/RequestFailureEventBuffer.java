package com.xhhao.insight.event;

import com.xhhao.insight.metrics.RequestMetricDimensions;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Coalesces request failures away from the reactive request path before the collector persists
 * them as timeline events.
 */
@Component
public class RequestFailureEventBuffer {

    static final int MAX_UNIQUE_FAILURES = 64;
    private static final int MAX_EXCEPTION_TYPE_LENGTH = 160;
    private static final String OVERFLOW_SOURCE = "_overflow";

    private final Clock clock;
    private final Map<FailureKey, FailureAccumulator> failures = new LinkedHashMap<>();
    private long overflowFirstSeenAtMs;
    private long overflowLastSeenAtMs;
    private long overflowCount;

    @Autowired
    public RequestFailureEventBuffer() {
        this(Clock.systemUTC());
    }

    RequestFailureEventBuffer(Clock clock) {
        this.clock = clock;
    }

    /** Records only bounded route dimensions and an exception class name, never request values. */
    public synchronized void record(
        RequestMetricDimensions dimensions,
        int statusCode,
        String exceptionType
    ) {
        if (dimensions == null) {
            return;
        }
        int safeStatus = statusCode >= 100 && statusCode <= 599 ? statusCode : 500;
        String safeExceptionType = boundedExceptionType(exceptionType);
        FailureKey key = new FailureKey(dimensions.scope(), dimensions.route(),
            dimensions.method(), safeStatus, safeExceptionType);
        long now = clock.millis();
        FailureAccumulator existing = failures.get(key);
        if (existing != null) {
            existing.observe(now);
            return;
        }
        if (failures.size() >= MAX_UNIQUE_FAILURES) {
            if (overflowCount == 0) {
                overflowFirstSeenAtMs = now;
            }
            overflowLastSeenAtMs = now;
            overflowCount = increment(overflowCount);
            return;
        }
        failures.put(key, new FailureAccumulator(now));
    }

    /** Atomically drains the current coalesced failures into a bounded persistence batch. */
    public synchronized List<EventRecorder.EventOccurrence> drain() {
        if (failures.isEmpty() && overflowCount == 0) {
            return List.of();
        }
        List<EventRecorder.EventOccurrence> occurrences = new ArrayList<>(
            failures.size() + (overflowCount > 0 ? 1 : 0));
        failures.forEach((key, accumulator) -> occurrences.add(toOccurrence(key, accumulator)));
        failures.clear();
        if (overflowCount > 0) {
            occurrences.add(overflowOccurrence());
            overflowFirstSeenAtMs = 0;
            overflowLastSeenAtMs = 0;
            overflowCount = 0;
        }
        return List.copyOf(occurrences);
    }

    private static EventRecorder.EventOccurrence toOccurrence(
        FailureKey key,
        FailureAccumulator accumulator
    ) {
        String summary = key.method() + " " + key.route() + " 返回 HTTP " + key.statusCode();
        String fingerprint = "request:" + key.scope() + ':' + key.method() + ':' + key.route()
            + ':' + key.statusCode() + ':' + (key.exceptionType() == null
            ? "none" : key.exceptionType());
        return new EventRecorder.EventOccurrence(
            "request",
            "warning",
            key.scope(),
            "请求处理失败",
            summary,
            fingerprint,
            accumulator.firstSeenAtMs,
            accumulator.lastSeenAtMs,
            accumulator.count,
            EventDetailsEncoder.encode(new RequestFailureDetails(
                "request_failure", key.method(), key.route(), key.statusCode(),
                key.exceptionType()))
        );
    }

    private EventRecorder.EventOccurrence overflowOccurrence() {
        return new EventRecorder.EventOccurrence(
            "request",
            "warning",
            OVERFLOW_SOURCE,
            "请求异常事件已合并",
            "超出事件缓冲维度上限的请求异常已合并记录",
            "request:_overflow",
            overflowFirstSeenAtMs,
            overflowLastSeenAtMs,
            overflowCount,
            EventDetailsEncoder.encode(new OverflowDetails(
                "request_failure_overflow", MAX_UNIQUE_FAILURES))
        );
    }

    private static String boundedExceptionType(String exceptionType) {
        if (exceptionType == null || exceptionType.isBlank()) {
            return null;
        }
        String value = exceptionType.strip();
        return value.length() <= MAX_EXCEPTION_TYPE_LENGTH
            ? value : value.substring(0, MAX_EXCEPTION_TYPE_LENGTH - 1) + '~';
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1;
    }

    private record FailureKey(
        String scope,
        String route,
        String method,
        int statusCode,
        String exceptionType
    ) {}

    private static final class FailureAccumulator {
        private final long firstSeenAtMs;
        private long lastSeenAtMs;
        private long count = 1;

        private FailureAccumulator(long observedAtMs) {
            this.firstSeenAtMs = observedAtMs;
            this.lastSeenAtMs = observedAtMs;
        }

        private void observe(long observedAtMs) {
            lastSeenAtMs = observedAtMs;
            count = increment(count);
        }
    }

    private record RequestFailureDetails(
        String kind,
        String method,
        String route,
        int statusCode,
        String exceptionType
    ) {}

    private record OverflowDetails(String kind, int uniqueFailureLimit) {}
}
