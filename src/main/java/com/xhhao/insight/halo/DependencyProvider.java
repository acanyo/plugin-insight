package com.xhhao.insight.halo;

/** Identifies a Halo plugin that provides or owns a checked dependency. */
public record DependencyProvider(String pluginId, String displayName) {

    public DependencyProvider {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        pluginId = pluginId.trim();
        displayName = displayName == null || displayName.isBlank()
            ? null
            : displayName.trim();
    }

    public DependencyProvider(String pluginId) {
        this(pluginId, null);
    }
}
