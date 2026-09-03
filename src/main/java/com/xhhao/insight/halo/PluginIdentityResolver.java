package com.xhhao.insight.halo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds the only accepted identity map for plugin metrics: Halo metadata.name. */
@Component
public class PluginIdentityResolver {

    public Map<String, PluginRuntimeInfo> index(List<PluginRuntimeInfo> plugins) {
        Map<String, PluginRuntimeInfo> result = new LinkedHashMap<>();
        for (PluginRuntimeInfo plugin : plugins) {
            if (plugin.name() != null && !plugin.name().isBlank()) {
                result.putIfAbsent(key(plugin.name()), plugin);
            }
        }
        return Map.copyOf(result);
    }

    public String key(String pluginId) {
        return pluginId == null ? "" : pluginId.toLowerCase(Locale.ROOT);
    }
}
