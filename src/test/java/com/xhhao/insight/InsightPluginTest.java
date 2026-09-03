package com.xhhao.insight;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.collection.MonitoringCoordinator;
import com.xhhao.insight.event.EventRecorder;
import com.xhhao.insight.event.HaloApplicationLifecycleObserver;
import com.xhhao.insight.event.HaloConfigMapEventWatcher;
import com.xhhao.insight.event.HaloPluginEventWatcher;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.plugin.PluginContext;

@ExtendWith(MockitoExtension.class)
class InsightPluginTest {

    @Mock
    PluginContext context;

    @Mock
    SqliteStorageLifecycle storage;

    @Mock
    MonitoringCoordinator coordinator;

    @Mock
    EventRecorder events;

    @Mock
    HaloPluginEventWatcher pluginEventWatcher;

    @Mock
    HaloConfigMapEventWatcher configMapEventWatcher;

    @Mock
    HaloApplicationLifecycleObserver haloLifecycleObserver;

    @InjectMocks
    InsightPlugin plugin;

    @Test
    void shouldStartAndStopMonitoringResourcesInOrder() {
        when(storage.initialize()).thenReturn(true);

        plugin.start();
        plugin.stop();

        var order = inOrder(storage, haloLifecycleObserver, pluginEventWatcher,
            configMapEventWatcher, coordinator);
        order.verify(storage).initialize();
        order.verify(haloLifecycleObserver).start();
        order.verify(pluginEventWatcher).start();
        order.verify(configMapEventWatcher).start();
        order.verify(coordinator).start();
        order.verify(coordinator).stop();
        order.verify(configMapEventWatcher).dispose();
        order.verify(pluginEventWatcher).dispose();
        order.verify(haloLifecycleObserver).stop();
        order.verify(storage).close();
        verify(events).record("lifecycle", "info", "insight", "Insight 监控组件已初始化",
            "监控采集与 SQLite 历史存储均已启用");
        verify(events).record("lifecycle", "info", "insight", "Insight 监控组件已停止",
            "Insight 自身的监控采集已停止；此事件不代表 Halo 应用关闭");
    }
}
