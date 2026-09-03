package com.xhhao.insight.event;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Watcher;

/** Watches Halo ConfigMap resources and forwards their data changes to the timeline. */
@Component
public final class HaloConfigMapEventWatcher implements Watcher {

    private final ReactiveExtensionClient extensionClient;
    private final ConfigMapTimelineRecorder timeline;
    private final AtomicBoolean disposed = new AtomicBoolean();
    private volatile Runnable disposeHook;

    public HaloConfigMapEventWatcher(
        ReactiveExtensionClient extensionClient,
        ConfigMapTimelineRecorder timeline
    ) {
        this.extensionClient = extensionClient;
        this.timeline = timeline;
    }

    public void start() {
        disposed.set(false);
        extensionClient.watch(this);
    }

    @Override
    public void onAdd(Extension extension) {
        if (extension instanceof ConfigMap configMap) {
            timeline.configMapAdded(configMap);
        }
    }

    @Override
    public void onUpdate(Extension oldExtension, Extension newExtension) {
        if (oldExtension instanceof ConfigMap oldConfigMap
            && newExtension instanceof ConfigMap newConfigMap) {
            timeline.configMapUpdated(oldConfigMap, newConfigMap);
        }
    }

    @Override
    public void onDelete(Extension extension) {
        if (extension instanceof ConfigMap configMap) {
            timeline.configMapDeleted(configMap);
        }
    }

    @Override
    public void registerDisposeHook(Runnable dispose) {
        this.disposeHook = dispose;
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true) && disposeHook != null) {
            disposeHook.run();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }
}
