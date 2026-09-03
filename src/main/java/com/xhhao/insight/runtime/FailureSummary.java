package com.xhhao.insight.runtime;

/**
 * Creates a bounded, non-sensitive description of an internal failure for the monitoring API.
 *
 * <p>Exception messages commonly contain filesystem paths, JDBC URLs, SQL fragments, or other
 * deployment-specific data. The monitoring overview is an administrative surface, so it should
 * expose enough information to identify the failure class without echoing that raw text.</p>
 */
public final class FailureSummary {

    private static final int MAX_TYPE_LENGTH = 96;
    private static final int MAX_CAUSE_DEPTH = 12;

    private FailureSummary() {
    }

    public static String of(Throwable failure) {
        if (failure == null) {
            return "Unknown failure";
        }
        Throwable root = rootCause(failure);
        String type = root.getClass().getSimpleName();
        if (type == null || type.isBlank()) {
            type = root.getClass().getName();
        }
        return bounded(type);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; depth < MAX_CAUSE_DEPTH; depth++) {
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                return current;
            }
            current = cause;
        }
        return current;
    }

    private static String bounded(String value) {
        if (value.length() <= MAX_TYPE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TYPE_LENGTH);
    }
}
