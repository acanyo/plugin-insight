package com.xhhao.insight.halo;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;

/** Resolves the PF4J plugin that owns an implementation class. */
@Component
public class PluginComponentOwnerResolver {

    private final PluginManager pluginManager;
    private final Map<ClassLoader, Optional<String>> owners =
        Collections.synchronizedMap(new WeakHashMap<>());

    public PluginComponentOwnerResolver(PluginWrapper currentPlugin) {
        this.pluginManager = currentPlugin.getPluginManager();
    }

    public Optional<String> pluginId(Object component) {
        return component == null ? Optional.empty() : pluginId(component.getClass());
    }

    public Optional<String> pluginId(Class<?> componentType) {
        if (componentType == null || componentType.getClassLoader() == null) {
            return Optional.empty();
        }
        ClassLoader classLoader = componentType.getClassLoader();
        Optional<String> cached = owners.get(classLoader);
        if (cached != null) {
            return cached;
        }
        try {
            PluginWrapper owner = pluginManager.whichPlugin(componentType);
            Optional<String> resolved = owner == null || owner.getPluginId() == null
                || owner.getPluginId().isBlank()
                ? Optional.empty()
                : Optional.of(owner.getPluginId());
            owners.put(classLoader, resolved);
            return resolved;
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }
}
