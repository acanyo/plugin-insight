package com.xhhao.insight.event;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.Metadata;

class PluginTimelineRecorderTest {

    private final EventRecorder events = org.mockito.Mockito.mock(EventRecorder.class);
    private final PluginTimelineRecorder timeline = new PluginTimelineRecorder(events);

    @Test
    void recordsObservedStartedPhaseAndLastStartTime() {
        Instant startedAt = Instant.parse("2026-09-02T08:00:00Z");
        Plugin oldPlugin = plugin(true, "1.0.0", Plugin.Phase.STARTING, null);
        Plugin newPlugin = plugin(true, "1.0.0", Plugin.Phase.STARTED, startedAt);

        timeline.pluginUpdated(oldPlugin, newPlugin);

        verify(events).record(eq("plugin"), eq("info"), eq("plugin-links"),
            eq("插件运行已启动"), contains("STARTING → STARTED"),
            contains("\"toPhase\":\"STARTED\""));
        verify(events).record(eq("plugin"), eq("info"), eq("plugin-links"),
            eq("插件运行已启动"), contains(startedAt.toString()),
            contains("\"lastStartTime\":\"2026-09-02T08:00:00Z\""));
    }

    @Test
    void failedPhaseUsesCriticalSeverityContract() {
        Plugin oldPlugin = plugin(true, "1.0.0", Plugin.Phase.STARTING, null);
        Plugin newPlugin = plugin(true, "1.0.0", Plugin.Phase.FAILED, null);

        timeline.pluginUpdated(oldPlugin, newPlugin);

        verify(events).record(eq("plugin"), eq("critical"), eq("plugin-links"),
            eq("插件运行失败"), contains("STARTING → FAILED"),
            contains("\"toPhase\":\"FAILED\""));
    }

    @Test
    void recordsFirstObservedPhaseInsteadOfLosingAnEarlyFailure() {
        Plugin oldPlugin = plugin(true, "1.0.0", null, null);
        Plugin newPlugin = plugin(true, "1.0.0", Plugin.Phase.FAILED, null);

        timeline.pluginUpdated(oldPlugin, newPlugin);

        verify(events).record(eq("plugin"), eq("critical"), eq("plugin-links"),
            eq("插件运行失败"), contains("UNREPORTED → FAILED"),
            contains("\"fromPhase\":\"UNREPORTED\""));
    }

    @Test
    void enabledIntentIsNotReportedAsACompletedRuntimeTransition() {
        Plugin oldPlugin = plugin(true, "1.0.0", Plugin.Phase.STARTED, null);
        Plugin newPlugin = plugin(false, "1.0.0", Plugin.Phase.STARTED, null);

        timeline.pluginUpdated(oldPlugin, newPlugin);

        verify(events).record(eq("plugin"), eq("info"), eq("plugin-links"),
            eq("插件启用请求已变更"), eq("链接管理 已请求 Halo 停用"),
            contains("\"kind\":\"plugin_enabled_intent\""));
        verify(events, never()).record(
            ArgumentMatchers.eq("plugin"),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq("plugin-links"),
            ArgumentMatchers.eq("插件运行已停止"),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString()
        );
    }

    @Test
    void lastStartTimeChangeWithoutPhaseChangeIsStillRecorded() {
        Plugin oldPlugin = plugin(true, "1.0.0", Plugin.Phase.STARTED,
            Instant.parse("2026-09-02T08:00:00Z"));
        Plugin newPlugin = plugin(true, "1.0.0", Plugin.Phase.STARTED,
            Instant.parse("2026-09-02T09:00:00Z"));

        timeline.pluginUpdated(oldPlugin, newPlugin);

        verify(events).record(eq("plugin"), eq("info"), eq("plugin-links"),
            eq("插件最近成功启动时间已更新"), contains("2026-09-02T09:00:00Z"),
            contains("\"kind\":\"plugin_last_start_time_change\""));
    }

    private static Plugin plugin(
        boolean enabled,
        String version,
        Plugin.Phase phase,
        Instant lastStartTime
    ) {
        Metadata metadata = new Metadata();
        metadata.setName("plugin-links");
        Plugin.PluginSpec spec = new Plugin.PluginSpec();
        spec.setEnabled(enabled);
        spec.setVersion(version);
        spec.setDisplayName("链接管理");
        Plugin.PluginStatus status = new Plugin.PluginStatus();
        status.setPhase(phase);
        status.setLastStartTime(lastStartTime);
        Plugin plugin = new Plugin();
        plugin.setMetadata(metadata);
        plugin.setSpec(spec);
        plugin.setStatus(status);
        return plugin;
    }
}
