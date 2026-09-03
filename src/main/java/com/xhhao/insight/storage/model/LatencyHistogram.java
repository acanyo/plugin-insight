package com.xhhao.insight.storage.model;

/**
 * Non-cumulative counts for fixed latency buckets whose names describe upper bounds.
 */
public record LatencyHistogram(
    long le5Ms,
    long le10Ms,
    long le25Ms,
    long le50Ms,
    long le100Ms,
    long le250Ms,
    long le500Ms,
    long le1000Ms,
    long le2500Ms,
    long le5000Ms,
    long gt5000Ms
) {

    private static final long[] UPPER_BOUNDS_US = {
        5_000,
        10_000,
        25_000,
        50_000,
        100_000,
        250_000,
        500_000,
        1_000_000,
        2_500_000,
        5_000_000
    };

    public LatencyHistogram {
        ModelChecks.nonNegative(le5Ms, "le5Ms");
        ModelChecks.nonNegative(le10Ms, "le10Ms");
        ModelChecks.nonNegative(le25Ms, "le25Ms");
        ModelChecks.nonNegative(le50Ms, "le50Ms");
        ModelChecks.nonNegative(le100Ms, "le100Ms");
        ModelChecks.nonNegative(le250Ms, "le250Ms");
        ModelChecks.nonNegative(le500Ms, "le500Ms");
        ModelChecks.nonNegative(le1000Ms, "le1000Ms");
        ModelChecks.nonNegative(le2500Ms, "le2500Ms");
        ModelChecks.nonNegative(le5000Ms, "le5000Ms");
        ModelChecks.nonNegative(gt5000Ms, "gt5000Ms");
    }

    public long totalCount() {
        return le5Ms + le10Ms + le25Ms + le50Ms + le100Ms + le250Ms + le500Ms
            + le1000Ms + le2500Ms + le5000Ms + gt5000Ms;
    }

    public long estimatePercentileUs(double percentile, long observedMaximumUs) {
        if (percentile <= 0 || percentile > 1) {
            throw new IllegalArgumentException("percentile must be in (0, 1]");
        }
        long total = totalCount();
        if (total == 0) {
            return 0;
        }
        long target = Math.max(1, (long) Math.ceil(total * percentile));
        long cumulative = 0;
        long[] counts = counts();
        for (int i = 0; i < UPPER_BOUNDS_US.length; i++) {
            cumulative += counts[i];
            if (cumulative >= target) {
                return UPPER_BOUNDS_US[i];
            }
        }
        return Math.max(5_000_001, observedMaximumUs);
    }

    private long[] counts() {
        return new long[] {
            le5Ms, le10Ms, le25Ms, le50Ms, le100Ms, le250Ms, le500Ms,
            le1000Ms, le2500Ms, le5000Ms, gt5000Ms
        };
    }
}
