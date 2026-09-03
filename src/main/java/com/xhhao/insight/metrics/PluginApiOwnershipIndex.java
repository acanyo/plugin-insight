package com.xhhao.insight.metrics;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.plugin.SpringPlugin;
import run.halo.app.plugin.SpringPluginManager;

/**
 * Indexes functional CustomEndpoint API groups by their owning Halo plugin.
 *
 * <p>Halo wraps functional plugin handlers in a core-classloader lambda, so handler class
 * ownership alone cannot identify these requests. CustomEndpoint group versions remain the
 * stable public contract for resolving that ownership.
 */
@Component
public class PluginApiOwnershipIndex {

    private static final long REFRESH_INTERVAL_NANOS = Duration.ofSeconds(15).toNanos();

    private final PluginManager pluginManager;
    private final LongSupplier nanoTime;
    private volatile Snapshot snapshot = new Snapshot(Long.MIN_VALUE, Map.of());

    @Autowired
    public PluginApiOwnershipIndex(PluginWrapper currentPlugin) {
        this(currentPlugin.getPluginManager(), System::nanoTime);
    }

    PluginApiOwnershipIndex(PluginManager pluginManager, LongSupplier nanoTime) {
        this.pluginManager = pluginManager;
        this.nanoTime = nanoTime;
    }

    public Optional<String> pluginId(String apiGroupVersion) {
        if (apiGroupVersion == null || apiGroupVersion.isBlank()) {
            return Optional.empty();
        }
        long now = nanoTime.getAsLong();
        Snapshot current = snapshot;
        if (expired(current, now)) {
            current = refreshIfExpired(current, now);
        }
        return Optional.ofNullable(
            current.owners().get(apiGroupVersion.toLowerCase(Locale.ROOT)));
    }

    private synchronized Snapshot refreshIfExpired(Snapshot observed, long now) {
        Snapshot current = snapshot;
        if (current != observed && !expired(current, now)) {
            return current;
        }
        snapshot = new Snapshot(now, discoverOwners());
        return snapshot;
    }

    private Map<String, String> discoverOwners() {
        Map<String, String> owners = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (PluginWrapper wrapper : startedPlugins()) {
            indexPlugin(wrapper, owners, ambiguous);
        }
        return Map.copyOf(owners);
    }

    private List<PluginWrapper> startedPlugins() {
        return pluginManager instanceof SpringPluginManager springPluginManager
            ? springPluginManager.startedPlugins()
            : pluginManager.getStartedPlugins();
    }

    private static void indexPlugin(
        PluginWrapper wrapper,
        Map<String, String> owners,
        Set<String> ambiguous
    ) {
        try {
            if (!(wrapper.getPlugin() instanceof SpringPlugin springPlugin)) {
                return;
            }
            ApplicationContext context = springPlugin.getApplicationContext();
            for (CustomEndpoint endpoint : context
                .getBeansOfType(CustomEndpoint.class, false, false).values()) {
                String groupVersion = endpoint.groupVersion().toString();
                if (groupVersion.isBlank()) {
                    continue;
                }
                addClaim(groupVersion.toLowerCase(Locale.ROOT),
                    wrapper.getPluginId(), owners, ambiguous);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A plugin may stop while this snapshot is being rebuilt; the next refresh retries it.
        }
    }

    private static void addClaim(
        String group,
        String pluginId,
        Map<String, String> owners,
        Set<String> ambiguous
    ) {
        if (pluginId == null || pluginId.isBlank() || ambiguous.contains(group)) {
            return;
        }
        String existing = owners.putIfAbsent(group, pluginId);
        if (existing != null && !existing.equals(pluginId)) {
            owners.remove(group);
            ambiguous.add(group);
        }
    }

    private static boolean expired(Snapshot snapshot, long now) {
        return snapshot.refreshedAtNanos() == Long.MIN_VALUE
            || now - snapshot.refreshedAtNanos() >= REFRESH_INTERVAL_NANOS;
    }

    private record Snapshot(long refreshedAtNanos, Map<String, String> owners) {}
}
