package com.xhhao.insight.diagnostics.logging;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Matches class-based loggers and stack frames; message text never establishes plugin ownership.
 */
public final class PluginLogAttribution {
    private final PluginLogClassIndex.Snapshot index;
    private final Map<String, List<String>> loggerMatches = new HashMap<>();

    public PluginLogAttribution(PluginLogClassIndex.Snapshot index) {
        this.index = index;
    }

    public Association associate(LogEntryParser.ParsedEntry entry) {
        var logger =
                entry.loggerName() == null
                        ? List.<String>of()
                        : loggerMatches.computeIfAbsent(entry.loggerName(), this::loggerOwners);
        Set<String> related = new LinkedHashSet<>(logger);
        boolean stackMatched = false;
        for (String className : entry.stackClasses()) {
            var owner = uniqueOwner(className);
            if (!owner.isEmpty()) {
                related.addAll(owner);
                stackMatched = true;
            }
        }
        String evidence =
                logger.isEmpty()
                        ? (stackMatched ? "stack" : "unknown")
                        : (stackMatched ? "logger-and-stack" : "logger");
        return new Association(List.copyOf(related), evidence);
    }

    private List<String> loggerOwners(String logger) {
        if (index.classOwners().containsKey(logger)) {
            return uniqueOwner(logger);
        }
        String[] segments = logger.split("\\.");
        if (segments.length < 2 || !index.complete()) {
            return List.of();
        }
        var candidates =
                index.classesBySimpleName().getOrDefault(segments[segments.length - 1], List.of());
        Set<String> owners = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (matchesAbbreviation(segments, candidate.split("\\."))) {
                var claimed = index.classOwners().get(candidate);
                owners.addAll(claimed);
            }
        }
        return owners.size() == 1 ? List.copyOf(owners) : List.of();
    }

    private List<String> uniqueOwner(String className) {
        var owners = index.classOwners().getOrDefault(className, Set.of());
        return owners.size() == 1 ? List.copyOf(owners) : List.of();
    }

    private static boolean matchesAbbreviation(String[] logger, String[] candidate) {
        if (logger.length != candidate.length) {
            return false;
        }
        for (int index = 0; index < logger.length; index++) {
            if (logger[index].equals(candidate[index])) {
                continue;
            }
            if (index == logger.length - 1
                    || logger[index].length() != 1
                    || !candidate[index].startsWith(logger[index])) {
                return false;
            }
        }
        return true;
    }

    public record Association(List<String> pluginIds, String evidence) {}
}
