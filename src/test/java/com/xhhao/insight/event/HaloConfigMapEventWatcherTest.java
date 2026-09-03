package com.xhhao.insight.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ReactiveExtensionClient;

class HaloConfigMapEventWatcherTest {

    private final ReactiveExtensionClient extensionClient =
        mock(ReactiveExtensionClient.class);
    private final ConfigMapTimelineRecorder timeline = mock(ConfigMapTimelineRecorder.class);
    private final HaloConfigMapEventWatcher watcher =
        new HaloConfigMapEventWatcher(extensionClient, timeline);

    @Test
    void registersAndForwardsOnlyConfigMapChanges() {
        ConfigMap oldConfig = new ConfigMap();
        ConfigMap newConfig = new ConfigMap();
        Extension unrelated = mock(Extension.class);

        watcher.start();
        watcher.onAdd(newConfig);
        watcher.onUpdate(oldConfig, newConfig);
        watcher.onDelete(newConfig);
        watcher.onAdd(unrelated);
        watcher.onUpdate(unrelated, unrelated);
        watcher.onDelete(unrelated);

        verify(extensionClient).watch(watcher);
        verify(timeline).configMapAdded(newConfig);
        verify(timeline).configMapUpdated(oldConfig, newConfig);
        verify(timeline).configMapDeleted(newConfig);
        verify(timeline, never()).configMapAdded(null);
    }

    @Test
    void disposeIsIdempotentAndInvokesTheClientHookOnce() {
        AtomicInteger disposals = new AtomicInteger();
        watcher.registerDisposeHook(disposals::incrementAndGet);
        watcher.start();

        watcher.dispose();
        watcher.dispose();

        assertThat(watcher.isDisposed()).isTrue();
        assertThat(disposals).hasValue(1);
    }
}
