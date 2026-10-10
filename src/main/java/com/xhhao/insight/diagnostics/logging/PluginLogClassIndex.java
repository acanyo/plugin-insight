package com.xhhao.insight.diagnostics.logging;

import com.xhhao.insight.runtime.MonitoringState;

import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Indexes plugin-owned class names on demand without loading classes or scanning nested libraries.
 */
@Component
public class PluginLogClassIndex {
    private static final long REFRESH_NANOS = Duration.ofMinutes(1).toNanos();
    private static final int MAX_PLUGINS = 2_000;
    private static final int MAX_ENTRIES_PER_PLUGIN = 20_000;
    private static final int MAX_TOTAL_CLASSES = 100_000;
    private static final Pattern CLASS_NAME = Pattern.compile("[\\w$]+(?:\\.[\\w$]+)+");
    private final PluginManager manager;
    private final MonitoringState state;
    private final LongSupplier nanoTime;
    private Snapshot cached;
    private long refreshedAt;

    @Autowired
    public PluginLogClassIndex(PluginWrapper currentPlugin, MonitoringState state) {
        this(currentPlugin.getPluginManager(), state, System::nanoTime);
    }

    PluginLogClassIndex(PluginManager manager, MonitoringState state, LongSupplier nanoTime) {
        this.manager = manager;
        this.state = state;
        this.nanoTime = nanoTime;
    }

    public synchronized Snapshot snapshot() {
        long now = nanoTime.getAsLong();
        if (cached == null || now - refreshedAt >= REFRESH_NANOS) {
            cached = discover();
            refreshedAt = now;
        }
        return cached;
    }

    private Snapshot discover() {
        var names = new LinkedHashMap<String, String>();
        for (var plugin : state.halo().plugins()) {
            if (plugin.name() != null && !plugin.name().isBlank()) {
                names.put(
                        plugin.name(),
                        plugin.displayName() == null || plugin.displayName().isBlank()
                                ? plugin.name()
                                : plugin.displayName());
            }
        }
        var owners = new HashMap<String, Set<String>>();
        var indexed = new LinkedHashSet<String>();
        var discovered = new LinkedHashSet<String>();
        boolean complete = true;
        List<PluginWrapper> plugins;
        try {
            plugins = List.copyOf(manager.getPlugins());
        } catch (RuntimeException | LinkageError unavailable) {
            plugins = List.of();
            complete = false;
        }
        int inspected = 0;
        for (var plugin : plugins) {
            if (inspected++ >= MAX_PLUGINS || owners.size() >= MAX_TOTAL_CLASSES) {
                complete = false;
                break;
            }
            String id = plugin.getPluginId();
            if (id == null || id.isBlank()) {
                continue;
            }
            discovered.add(id);
            names.putIfAbsent(id, id);
            try {
                Set<String> classes = classes(plugin.getPluginPath());
                if (classes.isEmpty()) {
                    continue;
                }
                if (owners.size() + classes.size() > MAX_TOTAL_CLASSES) {
                    complete = false;
                    continue;
                }
                classes.forEach(
                        name ->
                                owners.computeIfAbsent(name, ignored -> new LinkedHashSet<>())
                                        .add(id));
                indexed.add(id);
            } catch (IOException | RuntimeException failure) {
                complete = false;
            }
        }
        if (!discovered.containsAll(names.keySet())) {
            complete = false;
        }
        var options =
                names.entrySet().stream()
                        .map(
                                entry ->
                                        new PluginOption(
                                                entry.getKey(),
                                                entry.getValue(),
                                                indexed.contains(entry.getKey())))
                        .sorted(
                                Comparator.comparing(PluginOption::displayName)
                                        .thenComparing(PluginOption::pluginId))
                        .toList();
        var immutableOwners = new HashMap<String, Set<String>>();
        owners.forEach((name, ids) -> immutableOwners.put(name, Set.copyOf(ids)));
        var bySimpleName = new HashMap<String, List<String>>();
        owners.keySet()
                .forEach(
                        name ->
                                bySimpleName
                                        .computeIfAbsent(
                                                name.substring(name.lastIndexOf('.') + 1),
                                                ignored -> new ArrayList<>())
                                        .add(name));
        bySimpleName.replaceAll((name, classes) -> List.copyOf(classes));
        return new Snapshot(
                options, Map.copyOf(immutableOwners), Map.copyOf(bySimpleName), complete);
    }

    private static Set<String> classes(Path path) throws IOException {
        var names = new LinkedHashSet<String>();
        if (path == null) {
            throw new IOException("Plugin path is unavailable");
        }
        if (Files.isDirectory(path)) {
            var roots =
                    List.of(
                                    "classes",
                                    "build/classes/java/main",
                                    "build/classes/kotlin/main",
                                    "target/classes")
                            .stream()
                            .map(path::resolve)
                            .filter(Files::isDirectory)
                            .toList();
            if (roots.isEmpty()) {
                throw new IOException("Plugin class directory is unavailable");
            }
            int count = 0;
            for (Path root : roots) {
                try (var files = Files.walk(root, 32)) {
                    var iterator = files.iterator();
                    while (iterator.hasNext()) {
                        if (++count > MAX_ENTRIES_PER_PLUGIN) {
                            throw new IOException("Plugin class index exceeds its limit");
                        }
                        Path file = iterator.next();
                        if (Files.isRegularFile(file)) {
                            addClass(names, root.relativize(file).toString().replace('\\', '/'));
                        }
                    }
                }
            }
        } else if (Files.isRegularFile(path)) {
            try (var jar = new JarFile(path.toFile())) {
                var entries = jar.entries();
                int count = 0;
                while (entries.hasMoreElements()) {
                    if (++count > MAX_ENTRIES_PER_PLUGIN) {
                        throw new IOException("Plugin class index exceeds its limit");
                    }
                    addClass(names, entries.nextElement().getName());
                }
            }
        } else {
            throw new IOException("Plugin path is unavailable");
        }
        return names;
    }

    private static void addClass(Set<String> names, String entry) {
        if (entry.startsWith("BOOT-INF/classes/")) {
            entry = entry.substring("BOOT-INF/classes/".length());
        }
        if (entry.startsWith("classes/")) {
            entry = entry.substring("classes/".length());
        }
        if (!entry.endsWith(".class")
                || entry.startsWith("META-INF/")
                || entry.startsWith("lib/")) {
            return;
        }
        String name = entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
        if (CLASS_NAME.matcher(name).matches()) {
            names.add(name);
        }
    }

    public record PluginOption(String pluginId, String displayName, boolean indexed) {}

    public record Snapshot(
            List<PluginOption> plugins,
            Map<String, Set<String>> classOwners,
            Map<String, List<String>> classesBySimpleName,
            boolean complete) {}
}
