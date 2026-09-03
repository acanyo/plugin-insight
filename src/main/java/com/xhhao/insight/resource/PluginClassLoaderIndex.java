package com.xhhao.insight.resource;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;

/** Resolves only an exact PF4J plugin class loader, avoiding package/name heuristics. */
@Component
public class PluginClassLoaderIndex {

    private final PluginManager pluginManager;

    public PluginClassLoaderIndex(PluginWrapper currentPlugin) {
        this.pluginManager = currentPlugin.getPluginManager();
    }

    Map<ClassLoader, String> snapshot() {
        IdentityHashMap<ClassLoader, String> owners = new IdentityHashMap<>();
        try {
            List<PluginWrapper> plugins = pluginManager.getStartedPlugins();
            for (PluginWrapper plugin : plugins) {
                ClassLoader classLoader = plugin.getPluginClassLoader();
                String pluginId = plugin.getPluginId();
                if (classLoader != null && pluginId != null && !pluginId.isBlank()) {
                    owners.put(classLoader, pluginId);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A concurrent plugin lifecycle transition is retried on the next sample.
        }
        return owners;
    }

    static Optional<PluginThreadOwner> owner(
        Thread thread,
        Map<ClassLoader, String> owners
    ) {
        try {
            ClassLoader classLoader = thread.getContextClassLoader();
            String pluginId = owners.get(classLoader);
            return pluginId == null
                ? Optional.empty()
                : Optional.of(new PluginThreadOwner(pluginId, classLoader));
        } catch (RuntimeException | LinkageError failure) {
            return Optional.empty();
        }
    }

    record PluginThreadOwner(String pluginId, ClassLoader classLoader) {
    }
}
