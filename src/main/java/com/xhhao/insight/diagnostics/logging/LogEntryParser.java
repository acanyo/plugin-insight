package com.xhhao.insight.diagnostics.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Parses Java log headers and keeps their multiline exception stacks together. */
public final class LogEntryParser {
    private static final Pattern HEADER =
            Pattern.compile(
                    "^\\s*(?:\\d{4}-\\d{2}-\\d{2}(?:T\\S+|\\s+\\S+)\\s+)?"
                            + "(TRACE|DEBUG|INFO|WARN|ERROR)\\b(.*)$");
    private static final Pattern LOGGER =
            Pattern.compile(
                    "^\\s+(?:\\d+\\s+---\\s+)?(?:\\[[^\\]\\r\\n]*\\]\\s*)*"
                            + "([\\w.$-]+)\\s*(?::|\\s+-)\\s");
    private static final Pattern STACK_CLASS =
            Pattern.compile("(?m)^\\s*at\\s+(?:[^\\s()/]*/){0,2}([\\w.$]+)\\.[\\w$<>]+\\(");
    private static final int MAX_ENTRY_CHARACTERS = 16_384;
    private static final int MAX_LINE_CHARACTERS = 4_096;

    private LogEntryParser() {}

    public static boolean startsEntry(String text) {
        String firstLine = text.lines().findFirst().orElse("");
        return !firstLine.startsWith("\t") && HEADER.matcher(firstLine).matches();
    }

    public static List<ParsedEntry> parse(String text) {
        if (text.isBlank()) {
            return List.of();
        }
        var entries = new ArrayList<ParsedEntry>();
        String level = "INFO";
        String logger = null;
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n")) {
            var header = HEADER.matcher(line);
            if (!line.startsWith("\t") && header.matches()) {
                if (!current.isEmpty()) {
                    entries.add(entry(level, logger, current));
                }
                current.setLength(0);
                level = header.group(1);
                var name = LOGGER.matcher(header.group(2));
                logger = name.find() ? name.group(1) : null;
            }
            int remaining = MAX_ENTRY_CHARACTERS - current.length();
            if (remaining > 1) {
                current.append(
                                line,
                                0,
                                Math.min(
                                        line.length(),
                                        Math.min(MAX_LINE_CHARACTERS, remaining - 1)))
                        .append('\n');
            }
        }
        if (!current.isEmpty()) {
            entries.add(entry(level, logger, current));
        }
        return List.copyOf(entries);
    }

    private static ParsedEntry entry(String level, String logger, StringBuilder text) {
        String redacted = LogRedactor.redact(text.toString());
        var classes = new ArrayList<String>();
        var frames = STACK_CLASS.matcher(redacted);
        while (frames.find() && classes.size() < 100) {
            classes.add(frames.group(1));
        }
        return new ParsedEntry(level, logger, redacted, List.copyOf(classes));
    }

    public record ParsedEntry(
            String level, String loggerName, String text, List<String> stackClasses) {}
}
