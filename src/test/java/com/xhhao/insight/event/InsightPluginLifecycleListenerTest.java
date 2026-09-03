package com.xhhao.insight.event;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import run.halo.app.plugin.event.PluginStartedEvent;

class InsightPluginLifecycleListenerTest {

    @Test
    void recordsOnlyTheConfirmedInsightStartedSignal() {
        EventRecorder events = mock(EventRecorder.class);
        InsightPluginLifecycleListener listener = new InsightPluginLifecycleListener(events);

        listener.onPluginStarted(new PluginStartedEvent(this));

        verify(events).record(eq("lifecycle"), eq("info"), eq("insight"),
            eq("Insight 插件已进入运行状态"), contains("STARTED"),
            contains("\"signal\":\"plugin_started_event\""));
    }
}
