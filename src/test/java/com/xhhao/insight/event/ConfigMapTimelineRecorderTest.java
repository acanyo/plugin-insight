package com.xhhao.insight.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.plugin.PluginContext;

class ConfigMapTimelineRecorderTest {

    private final EventRecorder events = org.mockito.Mockito.mock(EventRecorder.class);
    private final ConfigMapTimelineRecorder timeline = new ConfigMapTimelineRecorder(events,
        PluginContext.builder().configMapName("insight-configmap").build());

    @Test
    void recordsOnlyCountsAndNeverPersistsKeysOrValues() {
        ConfigMap oldConfig = configMap("mail-config", Map.of(
            "smtp-password", "old-secret",
            "sender", "old@example.test",
            "removed-token", "private-token"));
        ConfigMap newConfig = configMap("mail-config", Map.of(
            "smtp-password", "new-secret",
            "sender", "old@example.test",
            "api-key", "private-api-key"));

        timeline.configMapUpdated(oldConfig, newConfig);

        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(events).record(eq("configuration"), eq("info"), eq("mail-config"),
            eq("配置资源已更新"), summary.capture(), details.capture());
        assertThat(summary.getValue())
            .contains("3 个配置项发生变化")
            .doesNotContain("smtp-password", "old-secret", "new-secret", "api-key",
                "private-api-key", "removed-token", "private-token");
        assertThat(details.getValue())
            .contains("\"action\":\"updated\"")
            .contains("\"addedEntries\":1")
            .contains("\"modifiedEntries\":1")
            .contains("\"removedEntries\":1")
            .contains("\"entryCount\":3")
            .doesNotContain("smtp-password", "old-secret", "new-secret", "api-key",
                "private-api-key", "removed-token", "private-token");
    }

    @Test
    void ignoresMetadataOnlyUpdates() {
        ConfigMap oldConfig = configMap("halo-config", Map.of("mode", "safe"));
        ConfigMap newConfig = configMap("halo-config", Map.of("mode", "safe"));
        newConfig.getMetadata().setVersion(2L);

        timeline.configMapUpdated(oldConfig, newConfig);

        verify(events, never()).record(anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString());
    }

    @Test
    void insightConfigMapIsLeftToTheGroupAwareListener() {
        ConfigMap oldConfig = configMap("insight-configmap",
            Map.of("storage", "before"));
        ConfigMap newConfig = configMap("insight-configmap",
            Map.of("storage", "after"));

        timeline.configMapAdded(newConfig);
        timeline.configMapUpdated(oldConfig, newConfig);
        timeline.configMapDeleted(newConfig);

        verify(events, never()).record(anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString());
    }

    @Test
    void recordsCreatedAndDeletedResourcesWithoutTheirContents() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("secret-key", "private-value");
        data.put("nullable", null);
        ConfigMap configMap = configMap("runtime-config", data);

        timeline.configMapAdded(configMap);
        timeline.configMapDeleted(configMap);

        ArgumentCaptor<String> summaries = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(events).record(eq("configuration"), eq("info"), eq("runtime-config"),
            eq("配置资源已创建"), summaries.capture(), details.capture());
        verify(events).record(eq("configuration"), eq("warning"), eq("runtime-config"),
            eq("配置资源已删除"), summaries.capture(), details.capture());
        assertThat(summaries.getAllValues()).allSatisfy(summary -> assertThat(summary)
            .doesNotContain("secret-key", "private-value", "nullable"));
        assertThat(details.getAllValues()).allSatisfy(detail -> assertThat(detail)
            .doesNotContain("secret-key", "private-value", "nullable"));
    }

    private static ConfigMap configMap(String name, Map<String, String> data) {
        Metadata metadata = new Metadata();
        metadata.setName(name);
        ConfigMap configMap = new ConfigMap();
        configMap.setMetadata(metadata);
        configMap.setData(data);
        return configMap;
    }
}
