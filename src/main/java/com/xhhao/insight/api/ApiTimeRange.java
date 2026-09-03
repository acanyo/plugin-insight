package com.xhhao.insight.api;

import java.time.Duration;
import java.util.Locale;

record ApiTimeRange(String name, long fromMs, long toMs) {

    static ApiTimeRange parse(String value, long nowMs) {
        String normalized = value == null || value.isBlank()
            ? "1h" : value.toLowerCase(Locale.ROOT);
        Duration duration = switch (normalized) {
            case "15m" -> Duration.ofMinutes(15);
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            default -> throw new IllegalArgumentException("Unsupported range: " + normalized);
        };
        return new ApiTimeRange(normalized, Math.max(0, nowMs - duration.toMillis()), nowMs + 1);
    }
}
