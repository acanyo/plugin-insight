package com.xhhao.insight.optimization;

/** Persisted low-overhead mode state. */
public record OptimizationProfile(
    boolean enabled,
    long appliedAtMs,
    long updatedAtMs
) {

    public static OptimizationProfile standard() {
        return new OptimizationProfile(false, 0, 0);
    }
}
