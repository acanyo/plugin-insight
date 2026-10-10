package com.xhhao.insight.diagnostics.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogEntryParserTest {
    @Test
    void parsesBootHeadersAndKeepsModulePrefixedStacksTogether() {
        var entries =
                LogEntryParser.parse(
                        """
2026-10-08T19:00:00.000+08:00 ERROR 1 --- [halo] [worker] c.e.alpha.Worker : failed
java.lang.IllegalStateException: unavailable
\tat app//com.example.alpha.Worker.run(Worker.java:23)
\tat java.base/java.lang.Thread.run(Thread.java:42)
2026-10-08 19:00:01.000 INFO [worker] com.example.beta.Worker - ready
""");

        assertThat(entries).hasSize(2);
        assertThat(entries.getFirst().level()).isEqualTo("ERROR");
        assertThat(entries.getFirst().loggerName()).isEqualTo("c.e.alpha.Worker");
        assertThat(entries.getFirst().stackClasses())
                .containsExactly("com.example.alpha.Worker", "java.lang.Thread");
        assertThat(entries.getFirst().text()).contains("IllegalStateException", "Worker.java:23");
        assertThat(entries.getLast().loggerName()).isEqualTo("com.example.beta.Worker");
        assertThat(entries.getLast().level()).isEqualTo("INFO");
    }

    @Test
    void retainsUnknownFormatsWithoutGuessingTheirLogger() {
        var entries = LogEntryParser.parse("custom plugin alpha started\nmore output\n");
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().loggerName()).isNull();
        assertThat(entries.getFirst().text()).contains("custom plugin alpha", "more output");
        assertThat(LogEntryParser.parse("")).isEmpty();
    }

    @Test
    void redactsCredentialsBeforeReturningOrGroupingEntries() {
        var entry =
                LogEntryParser.parse(
                                """
ERROR [worker] com.example.alpha.Worker - token=abc123 password=private
Authorization: Bearer authorization-value
Cookie: session=cookie-value
https://user:pass@example.com/path?key=query-value
""")
                        .getFirst();

        assertThat(entry.text())
                .doesNotContain(
                        "abc123",
                        "private",
                        "authorization-value",
                        "cookie-value",
                        "user:pass",
                        "query-value")
                .contains("[REDACTED]");
    }

    @Test
    void boundsLargeEntriesAndContinuesAtTheNextHeader() {
        var entries =
                LogEntryParser.parse(
                        "ERROR [worker] com.example.alpha.Worker - "
                                + "x".repeat(10_000)
                                + "\n"
                                + ("y".repeat(5_000) + "\n").repeat(20)
                                + "INFO [worker] com.example.beta.Worker - next\n");
        assertThat(entries).hasSize(2);
        assertThat(entries.getFirst().text().length()).isLessThanOrEqualTo(16_384);
        assertThat(entries.getFirst().text().lines().mapToInt(String::length).max().orElseThrow())
                .isLessThanOrEqualTo(4_096);
        assertThat(entries.getLast().text()).contains("next");
    }
}
