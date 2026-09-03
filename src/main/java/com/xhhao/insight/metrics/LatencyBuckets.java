package com.xhhao.insight.metrics;

public final class LatencyBuckets {

    private static final long[] UPPER_BOUNDS_MS = {
        5, 10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, Long.MAX_VALUE
    };

    private LatencyBuckets() {}

    public static int size() {
        return UPPER_BOUNDS_MS.length;
    }

    public static int index(long durationMs) {
        long safeDuration = Math.max(0, durationMs);
        for (int index = 0; index < UPPER_BOUNDS_MS.length; index++) {
            if (safeDuration <= UPPER_BOUNDS_MS[index]) {
                return index;
            }
        }
        return UPPER_BOUNDS_MS.length - 1;
    }

    public static long percentile(long[] counts, double percentile, long observedMaximumMs) {
        if (counts == null || counts.length == 0) {
            return 0;
        }
        long total = 0;
        for (long count : counts) {
            total += Math.max(0, count);
        }
        if (total == 0) {
            return 0;
        }
        long target = Math.max(1, (long) Math.ceil(total * percentile));
        long seen = 0;
        for (int index = 0; index < Math.min(counts.length, UPPER_BOUNDS_MS.length); index++) {
            seen += Math.max(0, counts[index]);
            if (seen >= target) {
                long bound = UPPER_BOUNDS_MS[index];
                return bound == Long.MAX_VALUE
                    ? Math.max(5_001, observedMaximumMs)
                    : bound;
            }
        }
        return Math.max(5_001, observedMaximumMs);
    }
}
