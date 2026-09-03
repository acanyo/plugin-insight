package com.xhhao.insight.event;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Structured, message-free diagnostics for a failed monitoring operation. */
public record MonitoringFailureDetails(
    int schemaVersion,
    String kind,
    String operation,
    String exceptionType,
    String rootCauseType
) {

    private static final int MAX_CAUSE_DEPTH = 16;

    public static MonitoringFailureDetails capture(String operation, Throwable failure) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(failure, "failure must not be null");
        Throwable rootCause = rootCause(failure);
        return new MonitoringFailureDetails(
            1,
            "monitoring_failure",
            operation,
            failure.getClass().getName(),
            rootCause.getClass().getName()
        );
    }

    String toJson() {
        return EventDetailsEncoder.encode(this);
    }

    private static Throwable rootCause(Throwable failure) {
        Map<Throwable, Boolean> visited = new IdentityHashMap<>();
        Throwable current = failure;
        int depth = 0;
        while (current.getCause() != null
            && current.getCause() != current
            && !visited.containsKey(current.getCause())
            && depth++ < MAX_CAUSE_DEPTH) {
            visited.put(current, Boolean.TRUE);
            current = current.getCause();
        }
        return current;
    }
}
