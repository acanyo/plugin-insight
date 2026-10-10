package com.xhhao.insight.diagnostics.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class PluginLogAttributionTest {
    private final Map<String, Set<String>> classes =
            Map.of(
                    "com.example.alpha.Worker", Set.of("alpha"),
                    "com.example.beta.Worker", Set.of("beta"),
                    "com.example.shared.Helper", Set.of("alpha", "beta"),
                    "com.external.alpha.Worker", Set.of("beta"));

    @Test
    void recognizesFullAndUnambiguousAbbreviatedClassLoggers() {
        var attribution = attribution(true);
        assertThat(attribution.associate(entry("com.example.alpha.Worker")).pluginIds())
                .containsExactly("alpha");
        assertThat(attribution.associate(entry("c.e.beta.Worker")).pluginIds())
                .containsExactly("beta");
        assertThat(attribution.associate(entry("c.example.alpha.Worker")).evidence())
                .isEqualTo("logger");
    }

    @Test
    void rejectsAmbiguousShortenedAndSharedClassLoggers() {
        var attribution = attribution(true);
        assertThat(attribution.associate(entry("c.e.alpha.Worker")).pluginIds()).isEmpty();
        assertThat(attribution.associate(entry("com.example.shared.Helper")).pluginIds()).isEmpty();
        assertThat(attribution.associate(entry("Worker")).pluginIds()).isEmpty();
    }

    @Test
    void disablesAbbreviationWhenSomePluginClassesAreUnavailable() {
        var attribution = attribution(false);
        assertThat(attribution.associate(entry("c.example.alpha.Worker")).pluginIds()).isEmpty();
        assertThat(attribution.associate(entry("com.example.alpha.Worker")).pluginIds())
                .containsExactly("alpha");
    }

    @Test
    void associatesEachPluginInAnExceptionStackWithoutMatchingMessageMentions() {
        var attribution = attribution(true);
        var associated =
                attribution.associate(
                        new LogEntryParser.ParsedEntry(
                                "ERROR",
                                "com.example.alpha.Worker",
                                "failed in beta",
                                List.of("com.example.beta.Worker", "com.example.shared.Helper")));
        assertThat(associated.pluginIds()).containsExactly("alpha", "beta");
        assertThat(associated.evidence()).isEqualTo("logger-and-stack");
        assertThat(
                        attribution
                                .associate(
                                        new LogEntryParser.ParsedEntry(
                                                "ERROR",
                                                null,
                                                "plugin alpha failed in com.example.alpha.Worker",
                                                List.of()))
                                .pluginIds())
                .isEmpty();
        assertThat(
                        attribution
                                .associate(
                                        new LogEntryParser.ParsedEntry(
                                                "ERROR",
                                                "org.halo.Host",
                                                "failed",
                                                List.of("com.example.beta.Worker")))
                                .evidence())
                .isEqualTo("stack");
    }

    private PluginLogAttribution attribution(boolean complete) {
        var bySimpleName = new HashMap<String, List<String>>();
        classes.keySet()
                .forEach(
                        name ->
                                bySimpleName
                                        .computeIfAbsent(
                                                name.substring(name.lastIndexOf('.') + 1),
                                                ignored -> new ArrayList<>())
                                        .add(name));
        return new PluginLogAttribution(
                new PluginLogClassIndex.Snapshot(List.of(), classes, bySimpleName, complete));
    }

    private static LogEntryParser.ParsedEntry entry(String logger) {
        return new LogEntryParser.ParsedEntry("INFO", logger, "ready", List.of());
    }
}
