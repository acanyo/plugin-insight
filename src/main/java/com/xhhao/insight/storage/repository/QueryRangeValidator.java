package com.xhhao.insight.storage.repository;

/** Shared argument validation for time-bounded repository queries. */
final class QueryRangeValidator {

    private QueryRangeValidator() {}

    static void validate(long fromInclusiveMs, long toExclusiveMs) {
        if (fromInclusiveMs < 0 || toExclusiveMs <= fromInclusiveMs) {
            throw new IllegalArgumentException("Invalid time range");
        }
    }

    static void validate(long fromInclusiveMs, long toExclusiveMs, int limit) {
        validate(fromInclusiveMs, toExclusiveMs);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
