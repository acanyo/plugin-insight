package com.xhhao.insight.api;

import com.xhhao.insight.storage.model.RequestRouteSort;
import java.util.Locale;

/** Defines the public route-ranking modes independently from SQLite ordering details. */
public enum RequestRouteRankingMode {
    SLOW(RequestRouteSort.P95_DURATION, true),
    REQUESTS(RequestRouteSort.REQUEST_COUNT, false),
    ERRORS(RequestRouteSort.ERROR_COUNT, false),
    ERROR_RATE(RequestRouteSort.ERROR_RATE, false),
    AVERAGE(RequestRouteSort.AVERAGE_DURATION, false),
    MAXIMUM(RequestRouteSort.MAX_DURATION, false),
    P95(RequestRouteSort.P95_DURATION, false),
    P99(RequestRouteSort.P99_DURATION, false);

    private final RequestRouteSort storageSort;
    private final boolean slowOnly;

    RequestRouteRankingMode(RequestRouteSort storageSort, boolean slowOnly) {
        this.storageSort = storageSort;
        this.slowOnly = slowOnly;
    }

    public RequestRouteSort storageSort() {
        return storageSort;
    }

    public boolean slowOnly() {
        return slowOnly;
    }

    boolean includes(double p95Ms, long slowRequestMillis) {
        return !slowOnly || p95Ms >= slowRequestMillis;
    }

    public static RequestRouteRankingMode parse(String value) {
        if (value == null || value.isBlank()) {
            return SLOW;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "requests", "count" -> REQUESTS;
            case "errors" -> ERRORS;
            case "error-rate" -> ERROR_RATE;
            case "average" -> AVERAGE;
            case "max" -> MAXIMUM;
            case "p95" -> P95;
            case "p99" -> P99;
            case "slow" -> SLOW;
            default -> SLOW;
        };
    }
}
