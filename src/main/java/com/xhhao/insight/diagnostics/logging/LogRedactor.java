package com.xhhao.insight.diagnostics.logging;

import java.util.regex.Pattern;

/** Removes common credentials before log text is returned or grouped. */
public final class LogRedactor {
    private static final Pattern SECRET =
            Pattern.compile(
                    "(?i)([\\\"']?(?:password|passwd|token|secret|authorization|cookie|api[_-]?key)[\\\"']?\\s*[:=]\\s*)(?:Bearer\\s+)?[^\\s,;]+");

    private LogRedactor() {}

    public static String redact(String text) {
        String headers =
                text.replaceAll(
                        "(?im)(\\b(?:authorization|cookie|set-cookie)\\s*[:=]\\s*)[^\\r\\n]+",
                        "$1[REDACTED]");
        return SECRET.matcher(headers)
                .replaceAll("$1[REDACTED]")
                .replaceAll("(?i)(https?://)[^\\s/@]+:[^\\s/@]+@", "$1[REDACTED]@")
                .replaceAll("(?i)(https?://[^\\s?]+)\\?[^\\s]+", "$1?[REDACTED]");
    }
}
