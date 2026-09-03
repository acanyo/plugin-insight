package com.xhhao.insight.runtime;

public record BackgroundTaskStatus(
    long lastSuccessAtMs,
    long lastFailureAtMs,
    String lastError
) {

    public static BackgroundTaskStatus pending() {
        return new BackgroundTaskStatus(0, 0, null);
    }

    public boolean pendingFirstRun() {
        return lastSuccessAtMs == 0 && lastFailureAtMs == 0;
    }

    public boolean failed() {
        return lastError != null;
    }

    BackgroundTaskStatus succeeded(long timestampMs) {
        return new BackgroundTaskStatus(timestampMs, lastFailureAtMs, null);
    }

    BackgroundTaskStatus failed(long timestampMs, Throwable failure) {
        return new BackgroundTaskStatus(lastSuccessAtMs, timestampMs, FailureSummary.of(failure));
    }
}
