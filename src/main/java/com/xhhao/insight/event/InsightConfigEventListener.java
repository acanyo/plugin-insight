package com.xhhao.insight.event;

import java.util.Set;
import java.util.TreeSet;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.PluginConfigUpdatedEvent;

@Component
public class InsightConfigEventListener {

    private static final int MAX_GROUPS = 12;
    private static final int MAX_GROUP_LENGTH = 64;

    private final EventRecorder events;

    public InsightConfigEventListener(EventRecorder events) {
        this.events = events;
    }

    @EventListener
    public void onConfigUpdated(PluginConfigUpdatedEvent event) {
        Set<String> groups = new TreeSet<>();
        if (event.getNewSettingValues() != null) {
            event.getNewSettingValues().keySet().stream()
                .filter(group -> group != null && !group.isBlank())
                .map(InsightConfigEventListener::boundedGroup)
                .limit(MAX_GROUPS)
                .forEach(groups::add);
        }
        events.record("configuration", "info", "insight", "运行洞察配置已变更",
            groups.isEmpty() ? "插件设置已更新" : "已更新分组：" + String.join("、", groups));
    }

    private static String boundedGroup(String value) {
        String stripped = value.strip();
        return stripped.length() <= MAX_GROUP_LENGTH
            ? stripped : stripped.substring(0, MAX_GROUP_LENGTH - 1) + "~";
    }
}
