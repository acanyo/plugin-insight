package com.xhhao.insight.event;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.Plugin;

/** Maps desired and observed Halo plugin lifecycle changes to honest timeline events. */
@Component
public final class PluginTimelineRecorder {

    private static final String UNREPORTED_PHASE = "UNREPORTED";

    private final EventRecorder events;

    public PluginTimelineRecorder(EventRecorder events) {
        this.events = events;
    }

    public void pluginAdded(Plugin plugin) {
        events.record("plugin", "info", pluginName(plugin), "插件记录已创建",
            pluginDisplayName(plugin) + " 已加入 Halo 插件清单");
    }

    public void pluginUpdated(Plugin oldPlugin, Plugin newPlugin) {
        recordEnabledIntent(oldPlugin, newPlugin);
        recordVersionChange(oldPlugin, newPlugin);
        recordObservedRuntimeChange(oldPlugin, newPlugin);
    }

    public void pluginDeleted(Plugin plugin) {
        events.record("plugin", "warning", pluginName(plugin), "插件记录已删除",
            pluginDisplayName(plugin) + " 已从 Halo 插件清单移除");
    }

    private void recordEnabledIntent(Plugin oldPlugin, Plugin newPlugin) {
        boolean oldEnabled = enabled(oldPlugin);
        boolean newEnabled = enabled(newPlugin);
        if (oldEnabled == newEnabled) {
            return;
        }
        String pluginId = pluginName(newPlugin);
        events.record("plugin", "info", pluginId, "插件启用请求已变更",
            pluginDisplayName(newPlugin) + (newEnabled ? " 已请求 Halo 启用" : " 已请求 Halo 停用"),
            EventDetailsEncoder.encode(new PluginEnabledIntentDetails(
                1, "plugin_enabled_intent", pluginId, oldEnabled, newEnabled)));
    }

    private void recordVersionChange(Plugin oldPlugin, Plugin newPlugin) {
        String oldVersion = version(oldPlugin);
        String newVersion = version(newPlugin);
        if (Objects.equals(oldVersion, newVersion)) {
            return;
        }
        String pluginId = pluginName(newPlugin);
        events.record("plugin", "info", pluginId, "插件版本已变更",
            value(oldVersion) + " → " + value(newVersion),
            EventDetailsEncoder.encode(new PluginVersionDetails(
                1, "plugin_version_change", pluginId, oldVersion, newVersion)));
    }

    private void recordObservedRuntimeChange(Plugin oldPlugin, Plugin newPlugin) {
        Plugin.Phase oldPhase = phase(oldPlugin);
        Plugin.Phase newPhase = phase(newPlugin);
        Instant oldLastStartTime = lastStartTime(oldPlugin);
        Instant newLastStartTime = lastStartTime(newPlugin);
        boolean phaseChanged = oldPhase != newPhase;
        boolean startTimeChanged = !Objects.equals(oldLastStartTime, newLastStartTime);
        if (!phaseChanged && !startTimeChanged) {
            return;
        }

        String pluginId = pluginName(newPlugin);
        if (phaseChanged) {
            events.record("plugin", severity(newPhase), pluginId, phaseTitle(newPhase),
                phaseSummary(newPlugin, oldPhase, newPhase, newLastStartTime),
                EventDetailsEncoder.encode(new PluginPhaseDetails(
                    1, "plugin_phase_transition", pluginId, phaseName(oldPhase),
                    phaseName(newPhase), instantValue(newLastStartTime))));
            return;
        }

        events.record("plugin", "info", pluginId, "插件最近成功启动时间已更新",
            pluginDisplayName(newPlugin) + "：" + instantValue(newLastStartTime),
            EventDetailsEncoder.encode(new PluginStartTimeDetails(
                1, "plugin_last_start_time_change", pluginId,
                instantValue(oldLastStartTime), instantValue(newLastStartTime))));
    }

    private static String phaseSummary(
        Plugin plugin,
        Plugin.Phase oldPhase,
        Plugin.Phase newPhase,
        Instant lastStartTime
    ) {
        String summary = pluginDisplayName(plugin) + "：" + phaseName(oldPhase)
            + " → " + phaseName(newPhase);
        if (newPhase == Plugin.Phase.STARTED && lastStartTime != null) {
            return summary + "，最近成功启动于 " + lastStartTime;
        }
        return summary;
    }

    private static String phaseTitle(Plugin.Phase phase) {
        if (phase == null) {
            return "插件运行阶段已清除";
        }
        return switch (phase) {
            case STARTING -> "插件正在启动";
            case STARTED -> "插件运行已启动";
            case DISABLING -> "插件正在停用";
            case DISABLED -> "插件已进入停用状态";
            case STOPPED -> "插件运行已停止";
            case FAILED -> "插件运行失败";
            default -> "插件运行阶段已变更";
        };
    }

    private static String severity(Plugin.Phase phase) {
        if (phase == Plugin.Phase.FAILED) {
            return "critical";
        }
        if (phase == Plugin.Phase.UNKNOWN || phase == null) {
            return "warning";
        }
        return "info";
    }

    private static boolean enabled(Plugin plugin) {
        return plugin.getSpec() != null && Boolean.TRUE.equals(plugin.getSpec().getEnabled());
    }

    private static String version(Plugin plugin) {
        return plugin.getSpec() == null ? null : plugin.getSpec().getVersion();
    }

    private static Plugin.Phase phase(Plugin plugin) {
        return plugin.getStatus() == null ? null : plugin.getStatus().getPhase();
    }

    private static Instant lastStartTime(Plugin plugin) {
        return plugin.getStatus() == null ? null : plugin.getStatus().getLastStartTime();
    }

    private static String pluginName(Plugin plugin) {
        if (plugin.getMetadata() == null
            || plugin.getMetadata().getName() == null
            || plugin.getMetadata().getName().isBlank()) {
            return "unknown";
        }
        return plugin.getMetadata().getName();
    }

    private static String pluginDisplayName(Plugin plugin) {
        if (plugin.getSpec() == null
            || plugin.getSpec().getDisplayName() == null
            || plugin.getSpec().getDisplayName().isBlank()) {
            return pluginName(plugin);
        }
        return plugin.getSpec().getDisplayName();
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String phaseName(Plugin.Phase phase) {
        return phase == null ? UNREPORTED_PHASE : phase.name();
    }

    private static String instantValue(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private record PluginEnabledIntentDetails(
        int schemaVersion,
        String kind,
        String pluginId,
        boolean fromEnabled,
        boolean toEnabled
    ) {}

    private record PluginVersionDetails(
        int schemaVersion,
        String kind,
        String pluginId,
        String fromVersion,
        String toVersion
    ) {}

    private record PluginPhaseDetails(
        int schemaVersion,
        String kind,
        String pluginId,
        String fromPhase,
        String toPhase,
        String lastStartTime
    ) {}

    private record PluginStartTimeDetails(
        int schemaVersion,
        String kind,
        String pluginId,
        String fromLastStartTime,
        String toLastStartTime
    ) {}
}
