package com.xhhao.insight.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.event.PluginStartedEvent;

/** Records the PF4J-confirmed running transition for this Insight plugin context. */
@Component
public final class InsightPluginLifecycleListener {

    private final EventRecorder events;

    public InsightPluginLifecycleListener(EventRecorder events) {
        this.events = events;
    }

    @EventListener
    public void onPluginStarted(PluginStartedEvent event) {
        events.record("lifecycle", "info", "insight", "运行洞察插件已进入运行状态",
            "Halo 插件管理器已确认运行洞察处于 STARTED 状态",
            EventDetailsEncoder.encode(new InsightLifecycleDetails(
                1, "insight_plugin_lifecycle", "plugin_started_event", "STARTED")));
    }

    private record InsightLifecycleDetails(
        int schemaVersion,
        String kind,
        String signal,
        String state
    ) {}
}
