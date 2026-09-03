package com.xhhao.insight.event;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ConfigMap;
import run.halo.app.plugin.PluginContext;

/** Converts Halo ConfigMap data changes into value-free timeline audit events. */
@Component
public final class ConfigMapTimelineRecorder {

    private static final int MAX_RESOURCE_NAME_LENGTH = 96;

    private final EventRecorder events;
    private final String insightConfigMapName;

    public ConfigMapTimelineRecorder(EventRecorder events, PluginContext pluginContext) {
        this.events = events;
        this.insightConfigMapName = pluginContext.getConfigMapName();
    }

    public void configMapAdded(ConfigMap configMap) {
        if (isInsightConfigMap(configMap)) {
            return;
        }
        String resourceName = resourceName(configMap);
        int entryCount = data(configMap).size();
        events.record("configuration", "info", resourceName, "配置资源已创建",
            "ConfigMap " + resourceName + " 已创建（" + entryCount + " 个配置项）",
            EventDetailsEncoder.encode(new ConfigMapChangeDetails(
                1, "config_map_change", resourceName, "created",
                entryCount, 0, 0, entryCount)));
    }

    public void configMapUpdated(ConfigMap oldConfigMap, ConfigMap newConfigMap) {
        if (isInsightConfigMap(newConfigMap)) {
            return;
        }
        ChangeCounts changes = changesBetween(data(oldConfigMap), data(newConfigMap));
        if (changes.total() == 0) {
            return;
        }
        String resourceName = resourceName(newConfigMap);
        events.record("configuration", "info", resourceName, "配置资源已更新",
            "ConfigMap " + resourceName + " 已更新（" + changes.total()
                + " 个配置项发生变化）",
            EventDetailsEncoder.encode(new ConfigMapChangeDetails(
                1, "config_map_change", resourceName, "updated",
                changes.added(), changes.modified(), changes.removed(),
                data(newConfigMap).size())));
    }

    public void configMapDeleted(ConfigMap configMap) {
        if (isInsightConfigMap(configMap)) {
            return;
        }
        String resourceName = resourceName(configMap);
        int entryCount = data(configMap).size();
        events.record("configuration", "warning", resourceName, "配置资源已删除",
            "ConfigMap " + resourceName + " 已删除（原有 " + entryCount + " 个配置项）",
            EventDetailsEncoder.encode(new ConfigMapChangeDetails(
                1, "config_map_change", resourceName, "deleted",
                0, 0, entryCount, 0)));
    }

    private static ChangeCounts changesBetween(
        Map<String, String> oldData,
        Map<String, String> newData
    ) {
        Set<String> keys = new HashSet<>(oldData.keySet());
        keys.addAll(newData.keySet());
        int added = 0;
        int modified = 0;
        int removed = 0;
        for (String key : keys) {
            boolean existed = oldData.containsKey(key);
            boolean exists = newData.containsKey(key);
            if (!existed) {
                added++;
            } else if (!exists) {
                removed++;
            } else if (!Objects.equals(oldData.get(key), newData.get(key))) {
                modified++;
            }
        }
        return new ChangeCounts(added, modified, removed);
    }

    private static Map<String, String> data(ConfigMap configMap) {
        if (configMap == null || configMap.getData() == null) {
            return Map.of();
        }
        return configMap.getData();
    }

    private boolean isInsightConfigMap(ConfigMap configMap) {
        return configMap != null && configMap.getMetadata() != null
            && insightConfigMapName != null
            && insightConfigMapName.equals(configMap.getMetadata().getName());
    }

    private static String resourceName(ConfigMap configMap) {
        if (configMap == null || configMap.getMetadata() == null) {
            return "unknown";
        }
        String name = configMap.getMetadata().getName();
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        String sanitized = name.replaceAll("[\\p{Cc}\\p{Cf}]", "?").strip();
        if (sanitized.length() <= MAX_RESOURCE_NAME_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_RESOURCE_NAME_LENGTH) + "…";
    }

    private record ChangeCounts(int added, int modified, int removed) {
        int total() {
            return added + modified + removed;
        }
    }

    private record ConfigMapChangeDetails(
        int schemaVersion,
        String kind,
        String resourceName,
        String action,
        int addedEntries,
        int modifiedEntries,
        int removedEntries,
        int entryCount
    ) {}
}
