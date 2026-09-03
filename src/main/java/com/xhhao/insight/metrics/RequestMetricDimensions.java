package com.xhhao.insight.metrics;

import java.util.Locale;

/**
 * Applies the storage boundary for request metric dimensions.
 *
 * <p>Handler patterns normally keep these values small, but unmatched or malicious requests can
 * otherwise put arbitrarily large strings into the in-memory registry and SQLite.</p>
 */
public record RequestMetricDimensions(String scope, String route, String method) {

    static final int MAX_SCOPE_LENGTH = 96;
    static final int MAX_ROUTE_LENGTH = 512;
    static final int MAX_METHOD_LENGTH = 16;

    public static RequestMetricDimensions normalize(String scope, String route, String method) {
        return new RequestMetricDimensions(
            clean(scope, "unknown", MAX_SCOPE_LENGTH),
            normalizeRoute(route),
            bounded(clean(method, "OTHER", MAX_METHOD_LENGTH).toUpperCase(Locale.ROOT),
                MAX_METHOD_LENGTH)
        );
    }

    private static String normalizeRoute(String route) {
        String value = clean(route, "/", MAX_ROUTE_LENGTH);
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return bounded(value, MAX_ROUTE_LENGTH);
    }

    private static String clean(String value, String fallback, int maximumLength) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String stripped = value.strip();
        int contentLimit = stripped.length() > maximumLength ? maximumLength - 1 : maximumLength;
        StringBuilder cleaned = new StringBuilder(Math.min(stripped.length(), maximumLength));
        int offset = 0;
        while (offset < stripped.length() && cleaned.length() < contentLimit) {
            int codePoint = stripped.codePointAt(offset);
            int width = Character.charCount(codePoint);
            if (cleaned.length() + width > contentLimit) {
                break;
            }
            if (Character.isISOControl(codePoint)) {
                cleaned.append('_');
            } else {
                cleaned.appendCodePoint(codePoint);
            }
            offset += width;
        }
        if (offset < stripped.length()) {
            cleaned.append('~');
        }
        return cleaned.isEmpty() ? fallback : cleaned.toString();
    }

    private static String bounded(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength - 1) + "~";
    }
}
